package com.example.dndsound.core.music

import com.example.dndsound.core.audio.FadeCoordinator
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.audio.PlayerHandle
import com.example.dndsound.core.mixer.GainMath
import com.example.dndsound.core.model.MusicMode
import com.example.dndsound.core.model.Track
import com.example.dndsound.core.model.WheelPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Music playback engine: two [PlayerHandle]s for equal-power A/B crossfading.
 *
 * All player mutation happens in one coroutine processing a command channel,
 * so no locking is needed for state; crossfades run in child jobs guarded by
 * a generation counter (a newer fade supersedes the older one at its next
 * ramp step) plus a mutex (so the superseded fade exits before the new one
 * starts writing volumes).
 *
 * Wheel input is debounced: [setWheelTarget] waits for [debounceMs] of
 * stillness before committing, so dragging across the wheel does not spam
 * track switches. The KEEP_RADIUS hysteresis lives in [WheelMusicController].
 */
class MusicEngine(
    private val scope: CoroutineScope,
    playerFactory: () -> PlayerHandle,
    private val selectTrack: suspend (point: WheelPoint, mode: MusicMode, recent: List<String>) -> Track?,
    private val crossfadeMs: Long = 4_000,
    private val debounceMs: Long = 600,
    private val rampStepMs: Long = 25,
) {

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private sealed interface Command {
        data class Wheel(val point: WheelPoint, val mode: MusicMode) : Command
        data class SetMode(val mode: MusicMode) : Command
        data object Next : Command
        data object Pause : Command
        data object Resume : Command
        data class BusGains(val masterDb: Float, val musicDb: Float) : Command
        /** One-shot ducking; null restores the normal bus gains. */
        data class SetDuck(val db: Float?) : Command
        data object Release : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val players: List<PlayerHandle> = listOf(playerFactory(), playerFactory())
    private val loadedTrack = arrayOfNulls<Track>(2)
    private var active = -1
    private val anchors = mutableMapOf<MusicMode, WheelPoint?>()
    private var mode = MusicMode.EXPLORATION
    private val recent = ArrayDeque<String>()
    private var masterDb = 0f
    private var musicBusDb = 0f
    private var duckDb: Float? = null
    private val fader = FadeCoordinator(rampStepMs)
    private var debounceJob: Job? = null

    init {
        players.forEachIndexed { index, handle ->
            scope.launch {
                handle.events.collect { event ->
                    when (event) {
                        PlayerEvent.Ended -> if (index == active) commands.trySend(Command.Next)
                        is PlayerEvent.Error -> if (index == active) {
                            _state.update { it.copy(error = event.message ?: "playback error") }
                        }
                        PlayerEvent.Ready -> Unit
                    }
                }
            }
        }
        scope.launch {
            for (command in commands) {
                try {
                    handleCommand(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    // ------------------------------------------------------------- public API

    /** Commits the wheel target after [debounceMs] without new input. */
    fun setWheelTarget(point: WheelPoint, requestedMode: MusicMode = mode) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            commands.trySend(Command.Wheel(point, requestedMode))
        }
    }

    fun setMode(newMode: MusicMode) {
        commands.trySend(Command.SetMode(newMode))
    }

    fun next() {
        commands.trySend(Command.Next)
    }

    fun pause() {
        commands.trySend(Command.Pause)
    }

    fun resume() {
        commands.trySend(Command.Resume)
    }

    fun setBusGains(masterDb: Float, musicBusDb: Float) {
        commands.trySend(Command.BusGains(masterDb, musicBusDb))
    }

    /** Ducks (db < 0) or restores (null) the music bus for one-shot playback. */
    fun setDuck(db: Float?) {
        commands.trySend(Command.SetDuck(db))
    }

    fun release() {
        debounceJob?.cancel()
        commands.trySend(Command.Release)
    }

    // -------------------------------------------------------------- internals

    private suspend fun handleCommand(command: Command) {
        when (command) {
            is Command.Wheel -> {
                val decision = WheelMusicController.decide(
                    anchor = anchors[command.mode],
                    target = command.point,
                    requestedMode = command.mode,
                    activeMode = mode,
                )
                if (decision is WheelMusicController.Decision.Switch) {
                    anchors[command.mode] = decision.anchor
                    mode = command.mode
                    advanceTo(decision.anchor)
                }
            }

            is Command.SetMode -> {
                if (command.mode != mode) {
                    mode = command.mode
                    advanceTo(anchors[mode] ?: WheelPoint.CENTER)
                }
            }

            Command.Next -> advanceTo(anchors[mode] ?: WheelPoint.CENTER)

            Command.Pause -> pauseActive()

            Command.Resume -> resumeActive()

            is Command.BusGains -> {
                masterDb = command.masterDb
                musicBusDb = command.musicDb
                applyStaticGains()
            }

            is Command.SetDuck -> {
                duckDb = command.db
                applyStaticGains()
            }

            Command.Release -> releaseNow()
        }
    }

    private suspend fun advanceTo(point: WheelPoint) {
        val track = selectTrack(point, mode, recent.toList())
        if (track == null) {
            fadeEverythingOut()
            _state.update {
                it.copy(playing = false, currentTrack = null, anchor = point, mode = mode, crossfading = false)
            }
            return
        }
        crossfadeTo(track, point)
    }

    private suspend fun crossfadeTo(track: Track, anchorPoint: WheelPoint) {
        pushRecent(track.id)
        val nextIndex = if (active < 0) 0 else 1 - active
        val incoming = players[nextIndex]
        val outgoing = if (active >= 0) players[active] else null

        loadedTrack[nextIndex] = track
        active = nextIndex

        incoming.setSource(track.uri)
        incoming.setVolume(0f)
        incoming.play()

        _state.update {
            it.copy(
                playing = true,
                currentTrack = track,
                anchor = anchorPoint,
                mode = mode,
                crossfading = outgoing != null,
                error = null,
            )
        }
        val inTarget = GainMath.dbToLinear(masterDb) *
            GainMath.dbToLinear(musicBusDb) *
            GainMath.dbToLinear(track.gainDb) *
            duckLinear()
        val generation = fader.begin(listOfNotNull(outgoing, incoming))
        scope.launch {
            val completed = fader.crossfade(generation, outgoing, incoming, inTarget, crossfadeMs)
            if (completed) {
                outgoing?.pause()
                _state.update { it.copy(crossfading = false) }
            }
        }
    }

    private suspend fun fadeEverythingOut() {
        val generation = fader.begin(players)
        if (fader.fadeOutAll(generation, players, 150)) {
            players.forEach { it.pause() }
        }
    }

    private fun pauseActive() {
        if (active >= 0) players[active].pause()
        _state.update { it.copy(playing = false) }
    }

    private fun resumeActive() {
        if (active < 0) return
        val handle = players[active]
        val track = loadedTrack[active] ?: return
        handle.play()
        _state.update { it.copy(playing = true) }
        val target = GainMath.dbToLinear(masterDb) *
            GainMath.dbToLinear(musicBusDb) *
            GainMath.dbToLinear(track.gainDb) *
            duckLinear()
        val generation = fader.begin(handle)
        scope.launch { fader.rampTo(generation, handle, target, 300) } // ~300 ms resume ramp
    }

    private fun applyStaticGains() {
        if (active < 0) return
        val track = loadedTrack[active] ?: return
        val target = GainMath.dbToLinear(masterDb) *
            GainMath.dbToLinear(musicBusDb) *
            GainMath.dbToLinear(track.gainDb) *
            duckLinear()
        val generation = fader.begin(players[active])
        scope.launch { fader.rampTo(generation, players[active], target, 100) }
    }

    private fun duckLinear(): Float = GainMath.dbToLinear(duckDb ?: 0f)

    private fun releaseNow() {
        loadedTrack.fill(null)
        active = -1
        players.forEach { it.release() }
        _state.update {
            MusicState(mode = mode, anchor = it.anchor)
        }
    }

    private fun pushRecent(trackId: String) {
        recent.removeFirstOrNull { it == trackId }
        recent.addFirst(trackId)
        while (recent.size > MAX_RECENT) {
            recent.removeLast()
        }
    }

    private companion object {
        const val MAX_RECENT = 5
    }
}

private fun <T> ArrayDeque<T>.removeFirstOrNull(predicate: (T) -> Boolean) {
    val index = indexOfFirst(predicate)
    if (index >= 0) removeAt(index)
}
