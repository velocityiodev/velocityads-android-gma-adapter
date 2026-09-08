package io.velocityads.gma

import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import io.velocityads.sdk.listeners.VelocityRewardedAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityFullscreenAd

/**
 * Translates [VelocityRewardedAdListener] callbacks to the Google Mobile Ads SDK.
 *
 * Load-phase events go to the [MediationAdLoadCallback]; once the SDK accepts the ad via
 * [MediationAdLoadCallback.onSuccess] the returned [MediationRewardedAdCallback] receives
 * every display-phase event.
 *
 * Reward amount and type are configured in the AdMob UI, so the reward is delivered via the
 * parameterless [MediationRewardedAdCallback.onUserEarnedReward] and the Google Mobile Ads
 * SDK fills in the ad unit's configured values.
 *
 * Main-thread-confined: both SDKs deliver every callback on the main thread.
 *
 * @param mediationAd The ad object handed to the Google Mobile Ads SDK on success.
 * @param onAdFinished Invoked once the creative is no longer usable (load failed or the ad
 *                     was dismissed) so the owner can release it.
 */
internal class VelocityRewardedAdHandler(
    private val mediationAd: MediationRewardedAd,
    private val loadCallback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    private val onAdFinished: () -> Unit = {},
) : VelocityRewardedAdListener {
    /** Set once the Google Mobile Ads SDK accepts the loaded ad via [MediationAdLoadCallback.onSuccess]. */
    var adCallback: MediationRewardedAdCallback? = null
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

    /**
     * Fires before [onAdDismissed] per the Velocity callback contract. Video start/complete
     * signals are intentionally not emitted: Velocity creatives are not necessarily video.
     */
    override fun onUserRewarded(ad: VelocityFullscreenAd) {
        adCallback?.onUserEarnedReward()
    }

    override fun onAdDismissed(ad: VelocityFullscreenAd) {
        adCallback?.onAdClosed()
        onAdFinished()
    }
}
