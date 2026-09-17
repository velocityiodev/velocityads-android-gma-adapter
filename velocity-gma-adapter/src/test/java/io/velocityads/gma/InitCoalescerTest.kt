package io.velocityads.gma

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the load-path re-init coalescing used by
 * [VelocityAdsGmaAdapter.ensureInitialized]: concurrent load calls must not each
 * fire their own initSDK — one caller wins the claim and every other caller is parked
 * until the winner broadcasts the outcome.
 */
class InitCoalescerTest {
    private enum class Outcome {
        SUCCESS,
        FAILURE,
    }

    private lateinit var coalescer: InitCoalescer<Outcome>

    @Before
    fun setUp() {
        coalescer = InitCoalescer()
    }

    // ========== Claiming ==========

    @Test
    fun `first caller wins the claim and sets isClaimed`() {
        // When
        val won = coalescer.claim { }

        // Then
        assertTrue(won, "First caller must win the claim")
        assertTrue(coalescer.isClaimed)
    }

    @Test
    fun `callers arriving while claimed are parked and do not win`() {
        // Given
        coalescer.claim { }

        // When
        val secondWon = coalescer.claim { }
        val thirdWon = coalescer.claim { }

        // Then
        assertFalse(secondWon, "Callers arriving while claimed must be parked")
        assertFalse(thirdWon, "Callers arriving while claimed must be parked")
        assertTrue(coalescer.isClaimed)
    }

    // ========== Broadcasting ==========

    @Test
    fun `complete broadcasts outcome to all handlers in registration order`() {
        // Given
        val received = mutableListOf<Pair<Int, Outcome>>()
        coalescer.claim { received.add(0 to it) }
        coalescer.claim { received.add(1 to it) }
        coalescer.claim { received.add(2 to it) }

        // When
        coalescer.complete(Outcome.SUCCESS)

        // Then
        assertEquals(listOf(0, 1, 2), received.map { it.first }, "All parked handlers must drain in order")
        assertTrue(received.all { it.second == Outcome.SUCCESS })
    }

    @Test
    fun `complete drains handlers so a second complete invokes nothing`() {
        // Given
        var invocationCount = 0
        coalescer.claim { invocationCount++ }
        coalescer.claim { invocationCount++ }
        coalescer.complete(Outcome.SUCCESS)

        // When
        coalescer.complete(Outcome.FAILURE)

        // Then
        assertEquals(2, invocationCount, "Handlers must be invoked exactly once")
    }

    // ========== Claim reset ==========

    @Test
    fun `complete on success resets claim so next caller wins again`() {
        // Given
        coalescer.claim { }
        coalescer.complete(Outcome.SUCCESS)

        // When / Then
        assertFalse(coalescer.isClaimed, "Claim must reset after a successful broadcast")
        assertTrue(coalescer.claim { }, "Next caller after completion must win a fresh claim")
    }

    @Test
    fun `complete on failure resets claim so a re-init can be attempted`() {
        // Given
        coalescer.claim { }
        coalescer.complete(Outcome.FAILURE)

        // When / Then
        assertFalse(coalescer.isClaimed, "Claim must reset after a failed broadcast")
        assertTrue(coalescer.claim { }, "A re-init attempt after failure must win a fresh claim")
    }

    @Test
    fun `reclaim after failure parks new handlers independently`() {
        // Given — first round fails
        val firstRound = mutableListOf<Outcome>()
        coalescer.claim { firstRound.add(it) }
        coalescer.complete(Outcome.FAILURE)

        // When — second round succeeds
        val secondRound = mutableListOf<Outcome>()
        coalescer.claim { secondRound.add(it) }
        coalescer.claim { secondRound.add(it) }
        coalescer.complete(Outcome.SUCCESS)

        // Then
        assertEquals(listOf(Outcome.FAILURE), firstRound)
        assertEquals(listOf(Outcome.SUCCESS, Outcome.SUCCESS), secondRound)
    }

    @Test
    fun `handler registered during broadcast belongs to the next round`() {
        // Given — a parked handler re-claims from inside its own broadcast, as a load
        // retry issued from a failure callback would
        val retryOutcomes = mutableListOf<Outcome>()
        coalescer.claim {
            val wonRetry = coalescer.claim { retry -> retryOutcomes.add(retry) }
            assertTrue(wonRetry, "Re-claim from inside a broadcast must win the fresh claim")
        }

        // When
        coalescer.complete(Outcome.FAILURE)

        // Then — the retry handler is untouched by the first broadcast
        assertEquals(emptyList(), retryOutcomes)
        assertTrue(coalescer.isClaimed, "The retry claim must still be in flight")
        coalescer.complete(Outcome.SUCCESS)
        assertEquals(listOf(Outcome.SUCCESS), retryOutcomes)
    }
}
