package io.velocityads.gma

import android.content.Context
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import io.velocityads.sdk.models.VelocityRewardedAd
import io.velocityads.sdk.models.VelocityRewardedAdRequest

/**
 * Handles the [MediationRewardedAd] contract for [VelocityAdsGmaAdapter].
 *
 * Owns the [VelocityRewardedAd] and its [VelocityRewardedAdHandler] for one load cycle. A
 * fullscreen ad is single-use: the creative is released as soon as the load fails or the ad
 * is dismissed. Main-thread-confined: both SDKs deliver every callback on the main thread.
 */
internal class VelocityGmaRewardedAd(
    private val ad: VelocityRewardedAd,
    loadCallback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
) : MediationRewardedAd {
    companion object {
        /**
         * Validates the configuration, ensures the Velocity SDK is initialized, then creates
         * and loads the ad. Every failure path reports exactly once via [callback].
         */
        fun load(
            ctx: AdLoadContext,
            configuration: MediationRewardedAdConfiguration,
            callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
        ) {
            val parameters = VelocityAdsGmaAdapter.serverParameters(configuration)
            val adUnitId = parameters.adUnitId
            if (adUnitId.isNullOrBlank()) {
                callback.onFailure(VelocityAdsErrorMapper.invalidServerParameters())
                return
            }
            ctx.ensureInitialized(configuration.context, parameters) { initialized ->
                if (!initialized) {
                    callback.onFailure(VelocityAdsErrorMapper.sdkNotInitialized())
                    return@ensureInitialized
                }
                val request = VelocityRewardedAdRequest.Builder(adUnitId).build()
                VelocityGmaRewardedAd(VelocityRewardedAd(request), callback).load()
            }
        }
    }

    private val handler = VelocityRewardedAdHandler(this, loadCallback, onAdFinished = ad::destroy)

    fun load() {
        ad.load(handler)
    }

    override fun showAd(context: Context) {
        if (!ad.isReady) {
            handler.adCallback?.onAdFailedToShow(VelocityAdsErrorMapper.adNotReady())
            return
        }
        ad.show(context)
    }
}
