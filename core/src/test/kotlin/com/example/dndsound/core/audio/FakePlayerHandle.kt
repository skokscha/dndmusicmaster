package com.example.dndsound.core.audio

import kotlinx.coroutines.flow.MutableSharedFlow

/** Test double for [PlayerHandle]; shared by the engine test suites. */
class FakePlayerHandle : PlayerHandle {
    override val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    val sources = mutableListOf<String>()
    var paused = false
        private set
    var released = false
        private set
    var looping = false
        private set
    var speed: Float = 1f
        private set
    override var volume: Float = 1f
        private set

    override fun setSource(uri: String) {
        sources += uri
    }

    override fun play() {
        paused = false
    }

    override fun pause() {
        paused = true
    }

    override fun setVolume(linear: Float) {
        volume = linear
    }

    override fun setLooping(enabled: Boolean) {
        looping = enabled
    }

    override fun setSpeedFactor(factor: Float) {
        speed = factor
    }

    override fun release() {
        released = true
    }

    fun emit(event: PlayerEvent) {
        events.tryEmit(event)
    }
}
