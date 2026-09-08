package io.velocityads.gma

import android.os.Handler
import android.os.Looper

/**
 * Polls an initialization predicate on the main thread until it reports `true` or a
 * deadline passes.
 *
 * Used when the Velocity SDK rejects the adapter's `initSDK` call with
 * `SDK_INITIALIZATION_IN_PROGRESS` because the host app started its own initialization
 * moments earlier. That rejection is not a permanent failure — the in-flight init will finish
 * shortly — so instead of failing the parked loads the adapter waits for the real outcome.
 *
 * [onResult] is invoked exactly once: every poll iteration either terminates with a result
 * or reschedules itself, never both.
 */
internal object InFlightInitPoller {
    const val DEFAULT_POLL_INTERVAL_MS = 200L
    const val DEFAULT_TIMEOUT_MS = 5_000L

    /**
     * Starts polling [isInitialized] on [handler]. Calls `onResult(true)` as soon as the
     * predicate returns `true`, or `onResult(false)` once the poll budget is exhausted.
     */
    fun awaitInitialization(
        isInitialized: () -> Boolean,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        handler: Handler = Handler(Looper.getMainLooper()),
        onResult: (Boolean) -> Unit,
    ) {
        // Integer poll budget rather than a wall-clock deadline: deterministic under test and
        // immune to clock adjustments.
        val totalPolls = if (pollIntervalMs > 0) (timeoutMs / pollIntervalMs).toInt().coerceAtLeast(0) else 0
        poll(totalPolls, isInitialized, pollIntervalMs, handler, onResult)
    }

    private fun poll(
        remainingPolls: Int,
        isInitialized: () -> Boolean,
        pollIntervalMs: Long,
        handler: Handler,
        onResult: (Boolean) -> Unit,
    ) {
        if (isInitialized()) {
            onResult(true)
            return
        }
        if (remainingPolls <= 0) {
            onResult(false)
            return
        }
        handler.postDelayed(
            { poll(remainingPolls - 1, isInitialized, pollIntervalMs, handler, onResult) },
            pollIntervalMs,
        )
    }
}
