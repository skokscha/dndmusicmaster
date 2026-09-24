package com.example.dndsound.core.ambience

import com.example.dndsound.core.audio.PlayerHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fallback looper for base loops whose files are not gapless (e.g. mp3 with
 * encoder delay): two players alternate, the next one starts [overlapMs]
 * before the current file ends, covering the trailing silence. Timeline is
 * anchored to a wall clock so coroutine drift does not accumulate.
 *
 * Only used when meta.json declares "seamless": false; well-encoded
 * OGG/Opus bases loop through the player's own repeat mode.
 */
class SeamlessFallbackLooper(
    private val scope: CoroutineScope,
    first: PlayerHandle,
    second: PlayerHandle,
    private val clockNs: () -> Long = System::nanoTime,
) {

    private val handles = listOf(first, second)
    private var job: Job? = null
    private var currentVolume = 1f

    val active: Boolean get() = job?.isActive == true

    fun start(uri: String, loopLengthMs: Long, volume: Float, overlapMs: Long = 100) {
        stop()
        currentVolume = volume
        val swapEveryMs = (loopLengthMs - overlapMs).coerceAtLeast(200)
        job = scope.launch {
            var index = 0
            val startedNs = clockNs()
            var cycle = 0L
            handles[0].apply {
                setSource(uri)
                setLooping(false)
                setVolume(volume)
                play()
            }
            while (isActive) {
                cycle++
                val fireAtNs = startedNs + cycle * swapEveryMs * 1_000_000
                val waitMs = (fireAtNs - clockNs()) / 1_000_000
                if (waitMs > 0) delay(waitMs)
                val incoming = handles[(index + 1) % 2]
                val outgoing = handles[index % 2]
                incoming.setSource(uri)
                incoming.setLooping(false)
                incoming.setVolume(volume)
                incoming.play()
                // The outgoing file is in its trailing gap; stop it shortly.
                launch {
                    delay(overlapMs)
                    outgoing.pause()
                }
                index++
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
        handles.forEach { it.setVolume(volume) }
    }

    fun pause() {
        handles.forEach { it.pause() }
    }

    fun resume() {
        if (job?.isActive == true) handles.forEach { it.play() }
    }

    fun stop() {
        job?.cancel()
        job = null
        handles.forEach { it.pause() }
    }
}
