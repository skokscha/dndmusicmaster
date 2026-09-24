package com.example.dndsound.core.ambience

import com.example.dndsound.core.audio.FakePlayerHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeamlessFallbackLooperTest {

    @Test
    fun `starts on the first handle without looping`() = runTest {
        val a = FakePlayerHandle()
        val b = FakePlayerHandle()
        val looper = SeamlessFallbackLooper(backgroundScope, a, b, clockNs = { currentTime * 1_000_000 })

        looper.start("content://base", loopLengthMs = 1000, volume = 1f, overlapMs = 100)
        runCurrent()

        assertEquals(listOf("content://base"), a.sources)
        assertFalse(a.looping)
        assertFalse(a.paused)
        assertEquals(1f, a.volume, 1e-6f)
    }

    @Test
    fun `swaps to the second handle before the file end`() = runTest {
        val a = FakePlayerHandle()
        val b = FakePlayerHandle()
        val looper = SeamlessFallbackLooper(backgroundScope, a, b, clockNs = { currentTime * 1_000_000 })

        looper.start("content://base", loopLengthMs = 1000, volume = 1f, overlapMs = 100)
        runCurrent()

        advanceTimeBy(900) // swap fires at 900 ms
        runCurrent()

        assertEquals(listOf("content://base"), b.sources)
        assertFalse(b.paused)
        assertEquals(1f, b.volume, 1e-6f)

        advanceTimeBy(150) // outgoing stops ~100 ms after the swap
        runCurrent()
        assertTrue(a.paused)

        advanceTimeBy(850) // next cycle swaps back to the first handle
        runCurrent()
        assertEquals(2, a.sources.size)
    }

    @Test
    fun `stop pauses both handles and halts swapping`() = runTest {
        val a = FakePlayerHandle()
        val b = FakePlayerHandle()
        val looper = SeamlessFallbackLooper(backgroundScope, a, b, clockNs = { currentTime * 1_000_000 })

        looper.start("content://base", loopLengthMs = 1000, volume = 1f, overlapMs = 100)
        runCurrent()
        looper.stop()
        runCurrent()

        assertTrue(a.paused)
        assertTrue(b.paused)

        advanceTimeBy(2000)
        runCurrent()
        assertEquals(1, a.sources.size)
        assertEquals(0, b.sources.size)
    }
}
