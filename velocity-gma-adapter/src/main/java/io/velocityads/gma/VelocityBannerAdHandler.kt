package io.velocityads.gma

import android.util.Log
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import io.velocityads.sdk.listeners.VelocityBannerAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityBannerAd

/**
 * Translates [VelocityBannerAdListener] callbacks to the Google Mobile Ads SDK.
 *
 * Load-phase events go to the [MediationAdLoadCallback]; once the SDK accepts the ad via
 * [MediationAdLoadCallback.onSuccess] the returned [MediationBannerAdCallback] receives
 * impression and click events. Main-thread-confined: both SDKs deliver every callback on
 * the main thread.
 *
 * @param mediationAd The ad object handed to the Google Mobile Ads SDK on success.
 * @param onLoadFailed Invoked after a load failure so the owner can release the creative.
 */
internal class VelocityBannerAdHandler(
    private val mediationAd: MediationBannerAd,
    private val loadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
    private val onLoadFailed: () -> Unit = {},
) : VelocityBannerAdListener {
    /** Set once the Google Mobile Ads SDK accepts the loaded ad via [MediationAdLoadCallback.onSuccess]. */
    var adCallback: MediationBannerAdCallback? = null
        private set

    override fun onAdLoaded(ad: VelocityBannerAd) {
        adCallback = loadCallback.onSuccess(mediationAd)
    }

    override fun onAdFailedToLoad(
        ad: VelocityBannerAd,
        error: VelocityAdsError,
    ) {
        loadCallback.onFailure(VelocityAdsErrorMapper.toAdError(error))
        onLoadFailed()
    }

    /**
     * Emitted on Velocity's viewability-gated impression (≥ 50% visible for ≥ 1 s) rather
     * than on load, so the impression the Google Mobile Ads SDK records matches Velocity's
     * own accounting.
     */
    override fun onAdImpression(ad: VelocityBannerAd) {
        adCallback?.reportAdImpression()
    }

    override fun onAdClicked(ad: VelocityBannerAd) {
        adCallback?.reportAdClicked()
    }

    /**
     * The Google Mobile Ads banner contract has no post-load failure hook — the SDK already
     * holds the view — so the failure is only logged for diagnosis of a blank slot.
     */
    override fun onAdFailedToShow(
        ad: VelocityBannerAd,
        error: VelocityAdsError,
    ) {
        Log.w(TAG, "Velocity Ads banner failed to render [${error.code}]: ${error.message}")
    }

    private companion object {
        const val TAG = "VelocityAdsGmaAdapter"
    }
}
