package io.velocityads.gma

import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import io.velocityads.sdk.listeners.VelocityInterstitialAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityFullscreenAd

/**
 * Translates [VelocityInterstitialAdListener] callbacks to the Google Mobile Ads SDK.
 *
 * Load-phase events go to the [MediationAdLoadCallback]; once the SDK accepts the ad via
 * [MediationAdLoadCallback.onSuccess] the returned [MediationInterstitialAdCallback] receives
 * every display-phase event. Main-thread-confined: both SDKs deliver every callback on the
 * main thread.
 *
 * @param mediationAd The ad object handed to the Google Mobile Ads SDK on success.
 * @param onAdFinished Invoked once the creative is no longer usable (load failed or the ad
 *                     was dismissed) so the owner can release it.
 */
internal class VelocityInterstitialAdHandler(
    private val mediationAd: MediationInterstitialAd,
    private val loadCallback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
    private val onAdFinished: () -> Unit = {},
) : VelocityInterstitialAdListener {
    /** Set once the Google Mobile Ads SDK accepts the loaded ad via [MediationAdLoadCallback.onSuccess]. */
    var adCallback: MediationInterstitialAdCallback? = null
        private set

    override fun onAdLoaded(ad: VelocityFullscreenAd) {
        adCallback = loadCallback.onSuccess(mediationAd)
    }

    override fun onAdFailedToLoad(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
        loadCallback.onFailure(VelocityAdsErrorMapper.toAdError(error))
        onAdFinished()
    }

    override fun onAdShown(ad: VelocityFullscreenAd) {
        adCallback?.onAdOpened()
    }

    override fun onAdImpression(ad: VelocityFullscreenAd) {
        adCallback?.reportAdImpression()
    }

    override fun onAdFailedToShow(
        ad: VelocityFullscreenAd,
        error: VelocityAdsError,
    ) {
        adCallback?.onAdFailedToShow(VelocityAdsErrorMapper.toAdError(error))
    }

    override fun onAdClicked(ad: VelocityFullscreenAd) {
        adCallback?.reportAdClicked()
    }

    override fun onAdDismissed(ad: VelocityFullscreenAd) {
        adCallback?.onAdClosed()
        onAdFinished()
    }
}
