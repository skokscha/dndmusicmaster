package com.example.dndsound.core.oneshot

import com.example.dndsound.core.mixer.GainMath
import com.example.dndsound.core.mixer.PanMath
import com.example.dndsound.core.model.OneShot
import com.example.dndsound.core.model.SoundFile
import com.example.dndsound.core.scheduler.RandomSpots
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
import kotlin.random.Random

/** Observable state of the one-shot part. */
data class OneShotState(
    val groups: List<OneShot> = emptyList(),
    /** Streams believed to be still sounding (SoundPool reports no endings). */
    val activeStreams: Int = 0,
    /** True while music/ambience are ducked because a one-shot is playing. */
    val ducking: Boolean = false,
    val error: String? = null,
)

/**
 * Platform-neutral mixer for short fire-and-forget sounds. :app implements it
 * with SoundPool (maxStreams ~8, pitch via playback rate, pan via per-channel
 * volumes); tests use a fake.
 */
interface OneShotMixer {
    /**
     * Starts playback and returns the stream id, or 0 when the sound must be
     * decoded first (the mixer then starts it by itself once decoded).
     */
    fun play(uri: String, left: Float, right: Float, rate: Float): Int

    fun stopAll()

    fun release()
}

/**
 * One-shot playback engine: variant selection with anti-repeat, optional
 * gain/pan/pitch jitter and ducking of the music/ambience buses.
 *
 * SoundPool reports stream endings, so "still playing" is tracked by
 * deadline: each play arms an unduck at `now + duration + tail`, using the
 * indexed file duration or [DEFAULT_DURATION_MS] when unknown. A newer play
 * postpones the deadline; [stopAll] releases the duck immediately.
 */
class OneShotEngine(
    private val scope: CoroutineScope,
    private val mixer: OneShotMixer,
    /** Called on the duck state change: db < 0 to duck, null to restore. */
    private val onDuck: suspend (db: Float?) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {

    private val _state = MutableStateFlow(OneShotState())
    val state: StateFlow<OneShotState> = _state.asStateFlow()

    private sealed interface Command {
        data class SetGroups(val groups: List<OneShot>) : Command
        data class Play(val groupId: String) : Command
        data object StopAll : Command
        data class SetDucking(val enabled: Boolean, val db: Float) : Command
        data class SetBusGains(val masterDb: Float, val sfxDb: Float) : Command
        data object Release : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val groups = linkedMapOf<String, OneShot>()

    /** Last played variant per group; drives the anti-repeat filter. */
    private val lastVariant = mutableMapOf<String, SoundFile>()

    /** Tracked streams (mixer stream id or a synthetic key) -> deadline ms. */
    private val streamDeadlines = mutableMapOf<Int, Long>()
    private var duckEnabled = true
    private var duckingDb = DEFAULT_DUCK_DB
    private var ducking = false
    private var duckJob: Job? = null
    private var syntheticId = -1
    private var masterDb = 0f
    private var sfxBusDb = 0f

    init {
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

    fun setGroups(groups: List<OneShot>) {
        commands.trySend(Command.SetGroups(groups))
    }

    fun play(groupId: String) {
        commands.trySend(Command.Play(groupId))
    }

    fun stopAll() {
        commands.trySend(Command.StopAll)
    }

    fun setDucking(enabled: Boolean, db: Float) {
        commands.trySend(Command.SetDucking(enabled, db))
    }

    /**
     * Applies the master and SFX bus gains to newly started streams. Already
     * sounding streams keep their volume — SoundPool streams are short and a
     * per-stream volume change would need id tracking in :app for no real gain.
     */
    fun setBusGains(masterDb: Float, sfxDb: Float) {
        commands.trySend(Command.SetBusGains(masterDb, sfxDb))
    }

    fun release() {
        commands.trySend(Command.Release)
    }

    // -------------------------------------------------------------- internals

    private suspend fun handleCommand(command: Command) {
        when (command) {
            is Command.SetGroups -> {
                groups.clear()
                command.groups.forEach { groups[it.id] = it }
                _state.update { it.copy(groups = command.groups) }
            }

            is Command.Play -> playGroup(command.groupId)

            Command.StopAll -> stopEverything()

            is Command.SetDucking -> {
                duckEnabled = command.enabled
                duckingDb = command.db
                if (!duckEnabled && ducking) releaseDuck()
            }

            is Command.SetBusGains -> {
                masterDb = command.masterDb
                sfxBusDb = command.sfxDb
            }

            Command.Release -> {
                stopEverything()
                mixer.release()
            }
        }
    }

    private suspend fun playGroup(groupId: String) {
        val group = groups[groupId] ?: return
        val variant = RandomSpots.pickVariant(group.variants, lastVariant[group.id], random) ?: return
        lastVariant[group.id] = variant

        val spec = group.random
        val gainDb = if (spec != null) {
            RandomSpots.jitterGainDb(group.gainDb, spec.gainJitterDb, random)
        } else {
            group.gainDb
        }
        val pan = if (spec != null) RandomSpots.jitterPan(spec.panJitter, random) else 0f
        val rate = if (spec != null) RandomSpots.jitterPitch(spec.pitchJitterPct, random) else 1f

        val gainLinear = GainMath.dbToLinear(gainDb)
        val busLinear = GainMath.dbToLinear(masterDb) * GainMath.dbToLinear(sfxBusDb)
        val (left, right) = PanMath.volumes(pan)
        val streamId = mixer.play(
            variant.uri,
            left * gainLinear * busLinear,
            right * gainLinear * busLinear,
            rate,
        )

        // Stream id 0 means "decoding"; track it under a synthetic key so the
        // duck still covers the first tap of every freshly loaded sound.
        val key = if (streamId > 0) streamId else syntheticId--
        val durationMs = variant.durationMs.takeIf { it > 0 } ?: DEFAULT_DURATION_MS
        streamDeadlines[key] = clockMs() + durationMs + DUCK_TAIL_MS
        ensureDuck()
        scheduleUnduck()
        publishState()
    }

    private fun stopEverything() {
        mixer.stopAll()
        streamDeadlines.clear()
        duckJob?.cancel()
        duckJob = null
        if (ducking) releaseDuck()
        publishState()
    }

    private fun ensureDuck() {
        if (!duckEnabled || ducking) return
        ducking = true
        publishState()
        scope.launch { onDuck(duckingDb) }
    }

    private fun releaseDuck() {
        ducking = false
        publishState()
        scope.launch { onDuck(null) }
    }

    /**
     * Unducks when the newest deadline passes. Every play reschedules this
     * job, so the latest call always owns the wait; entries older than their
     * deadline are pruned as they expire.
     */
    private fun scheduleUnduck() {
        duckJob?.cancel()
        val latest = streamDeadlines.values.max()
        duckJob = scope.launch {
            delay((latest - clockMs()).coerceAtLeast(1))
            streamDeadlines.values.removeAll { it <= clockMs() }
            if (streamDeadlines.isEmpty() && ducking) releaseDuck()
        }
    }

    private fun publishState() {
        _state.update { it.copy(activeStreams = streamDeadlines.size, ducking = ducking) }
    }

    private companion object {
        /** Duck tail: small extra hold so the tail of the sample stays ducked. */
        const val DUCK_TAIL_MS = 300L

        /** Fallback length for files without a probed duration. */
        const val DEFAULT_DURATION_MS = 3_000L

        const val DEFAULT_DUCK_DB = -3.5f
    }
}
