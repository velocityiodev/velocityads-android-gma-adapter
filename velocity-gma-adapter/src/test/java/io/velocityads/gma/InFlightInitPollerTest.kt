package io.velocityads.gma

import android.os.Handler
import android.os.Looper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InFlightInitPollerTest {
    private val handler = Handler(Looper.getMainLooper())
    private val results = mutableListOf<Boolean>()

    private fun advance(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun await(
        isInitialized: () -> Boolean,
        pollIntervalMs: Long = 100,
        timeoutMs: Long = 500,
    ) {
        InFlightInitPoller.awaitInitialization(
            isInitialized = isInitialized,
            pollIntervalMs = pollIntervalMs,
            timeoutMs = timeoutMs,
            handler = handler,
        ) { results += it }
    }

    @Test
    fun `reports true synchronously when already initialized`() {
        await(isInitialized = { true })

        assertEquals(listOf(true), results)
    }

    @Test
    fun `reports true on the first poll that observes initialization`() {
        var initialized = false
        var checks = 0
        await(isInitialized = {
            checks++
            initialized
        })

        advance(200)
        assertTrue(results.isEmpty())

        initialized = true
        advance(100)

        assertEquals(listOf(true), results)
        assertEquals(4, checks)
    }

    @Test
    fun `reports false exactly once after the poll budget is exhausted`() {
        await(isInitialized = { false }, pollIntervalMs = 100, timeoutMs = 500)

        advance(400)
        assertTrue(results.isEmpty())

        advance(100)
        assertEquals(listOf(false), results)

        advance(1_000)
        assertEquals(listOf(false), results)
    }

    @Test
    fun `a non-positive interval reports the current state without scheduling`() {
        await(isInitialized = { false }, pollIntervalMs = 0)

        assertEquals(listOf(false), results)
    }
}
