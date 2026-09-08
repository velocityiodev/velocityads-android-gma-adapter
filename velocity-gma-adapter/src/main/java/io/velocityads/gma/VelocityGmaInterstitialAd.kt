package io.velocityads.gma

import android.content.Context
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import io.velocityads.sdk.models.VelocityInterstitialAd
import io.velocityads.sdk.models.VelocityInterstitialAdRequest

/**
 * Handles the [MediationInterstitialAd] contract for [VelocityAdsGmaAdapter].
 *
 * Owns the [VelocityInterstitialAd] and its [VelocityInterstitialAdHandler] for one load
 * cycle. A fullscreen ad is single-use: the creative is released as soon as the load fails or
 * the ad is dismissed. Main-thread-confined: both SDKs deliver every callback on the main
 * thread.
 */
internal class VelocityGmaInterstitialAd(
    private val ad: VelocityInterstitialAd,
    loadCallback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
) : MediationInterstitialAd {
    companion object {
        /**
         * Validates the configuration, ensures the Velocity SDK is initialized, then creates
         * and loads the ad. Every failure path reports exactly once via [callback].
         */
        fun load(
            ctx: AdLoadContext,
            configuration: MediationInterstitialAdConfiguration,
            callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
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
                val request = VelocityInterstitialAdRequest.Builder(adUnitId).build()
                VelocityGmaInterstitialAd(VelocityInterstitialAd(request), callback).load()
            }
        }
    }

    private val handler = VelocityInterstitialAdHandler(this, loadCallback, onAdFinished = ad::destroy)

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
