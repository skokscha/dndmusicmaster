package com.example.dndsound.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import com.example.dndsound.core.oneshot.OneShotMixer

/**
 * SoundPool-backed one-shot mixer. Sounds are decoded once and cached by uri;
 * tapping a not-yet-decoded sound queues the play and starts it from the
 * load-complete listener. SoundPool caps concurrent streams itself
 * ([maxStreams], stealing the oldest), so the engine does not need to.
 */
class SoundPoolMixer(context: Context, private val maxStreams: Int = 8) : OneShotMixer {

    private class PendingPlay(val left: Float, val right: Float, val rate: Float)

    private class LoadingSound(val uri: String, val afd: AssetFileDescriptor)

    private val appContext = context.applicationContext
    private val lock = Any()
    private val loaded = HashMap<String, Int>()
    private val loading = HashMap<Int, LoadingSound>()
    private val pending = HashMap<String, PendingPlay>()
    private val active = HashSet<Int>()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            synchronized(lock) {
                val entry = loading.remove(soundId)
                if (entry == null) return@synchronized
                entry.afd.closeQuietly()
                if (status != 0) {
                    pending.remove(entry.uri)
                    return@synchronized
                }
                loaded[entry.uri] = soundId
                pending.remove(entry.uri)?.let { play ->
                    startStream(soundId, play)
                }
            }
        }
    }

    override fun play(uri: String, left: Float, right: Float, rate: Float): Int {
        synchronized(lock) {
            loaded[uri]?.let { return startStream(it, PendingPlay(left, right, rate)) }
            if (pending.containsKey(uri)) return 0 // already decoding; will start on its own
            val afd = try {
                appContext.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")
            } catch (_: Exception) {
                null
            } ?: return 0
            val soundId = soundPool.load(afd, 1)
            loading[soundId] = LoadingSound(uri, afd)
            pending[uri] = PendingPlay(left, right, rate)
            return 0
        }
    }

    override fun stopAll() {
        synchronized(lock) {
            active.forEach { soundPool.stop(it) }
            active.clear()
        }
    }

    override fun release() {
        synchronized(lock) {
            loading.values.forEach { it.afd.closeQuietly() }
            loading.clear()
            pending.clear()
            loaded.clear()
            active.clear()
            soundPool.release()
        }
    }

    private fun startStream(soundId: Int, play: PendingPlay): Int {
        val streamId = soundPool.play(
            soundId,
            play.left.coerceIn(0f, 1f),
            play.right.coerceIn(0f, 1f),
            1,
            0,
            play.rate.coerceIn(0.5f, 2f),
        )
        if (streamId > 0) {
            // Bounded bookkeeping: only stop() needs the ids; stale ones are
            // no-ops, so an occasional sweep is enough.
            if (active.size > MAX_TRACKED_STREAMS) active.clear()
            active += streamId
        }
        return streamId
    }

    private fun AssetFileDescriptor.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
            // Closing a decoded descriptor must never break playback.
        }
    }

    private companion object {
        const val MAX_TRACKED_STREAMS = 64
    }
}
