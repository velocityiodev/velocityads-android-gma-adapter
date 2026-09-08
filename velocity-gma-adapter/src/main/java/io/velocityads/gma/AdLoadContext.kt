package io.velocityads.gma

import android.content.Context

/**
 * The dependency each format loader needs from the main adapter.
 *
 * Implemented by [VelocityAdsGmaAdapter] and injected into the per-format ad classes so
 * each format's load/show logic lives in its own file without referencing the adapter.
 */
internal interface AdLoadContext {
    /**
     * Ensures the Velocity SDK is initialized and delivers the result on the main thread.
     * See [VelocityAdsGmaAdapter.ensureInitialized] for full semantics.
     */
    fun ensureInitialized(
        context: Context,
        parameters: VelocityAdsServerParameters,
        onReady: (Boolean) -> Unit,
    )
}
