package com.example.dndsound.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.dndsound.core.audio.PlayerEvent
import com.example.dndsound.core.audio.PlayerHandle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * ExoPlayer-backed [PlayerHandle]. SAF content URIs play through the default
 * data source; no file is copied out of the user's folder.
 */
class ExoPlayerHandle(context: Context) : PlayerHandle {

    private val appContext = context.applicationContext
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlayerEvent> = _events

    override var volume: Float = 1f
        private set

    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _events.tryEmit(PlayerEvent.Ready)
                    Player.STATE_ENDED -> _events.tryEmit(PlayerEvent.Ended)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _events.tryEmit(PlayerEvent.Error(error.message))
            }
        })
    }

    override fun setSource(uri: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun setVolume(linear: Float) {
        volume = linear.coerceIn(0f, 1f)
        player.volume = volume
    }

    override fun setLooping(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun setSpeedFactor(factor: Float) {
        player.playbackParameters = player.playbackParameters.withSpeed(factor.coerceIn(0.5f, 1.5f))
    }

    override fun release() {
        player.release()
    }
}
