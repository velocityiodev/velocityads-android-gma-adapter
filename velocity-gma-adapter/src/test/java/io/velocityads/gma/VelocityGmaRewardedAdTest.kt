package io.velocityads.gma

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import io.velocityads.sdk.listeners.VelocityRewardedAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityRewardedAd
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityGmaRewardedAdTest {
    private lateinit var activity: Activity
    private lateinit var velocityAd: VelocityRewardedAd
    private lateinit var loadCallback: RewardedLoadCallback
    private lateinit var adCallback: MediationRewardedAdCallback
    private lateinit var mediationAd: VelocityGmaRewardedAd

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).get()
        velocityAd = mock(VelocityRewardedAd::class.java)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as RewardedLoadCallback
        adCallback = mock(MediationRewardedAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        mediationAd = VelocityGmaRewardedAd(velocityAd, loadCallback)
    }

    /** Runs `load()` and returns the listener the ad object registered with the Velocity ad. */
    private fun loadAndCaptureListener(): VelocityRewardedAdListener {
        mediationAd.load()
        val captor: ArgumentCaptor<VelocityRewardedAdListener> =
            ArgumentCaptor.forClass(VelocityRewardedAdListener::class.java)
        verify(velocityAd).load(captor.captureNonNull())
        return captor.value
    }

    // ========== load ==========

    @Test
    fun `load starts the Velocity ad and a successful load hands this object to Google`() {
        val listener = loadAndCaptureListener()

        listener.onAdLoaded(velocityAd)

        verify(loadCallback).onSuccess(mediationAd)
    }

    @Test
    fun `a failed load releases the creative`() {
        val listener = loadAndCaptureListener()

        listener.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        verify(loadCallback).onFailure(any(AdError::class.java))
        verify(velocityAd).destroy()
    }

    @Test
    fun `dismissing the ad releases the single-use creative`() {
        val listener = loadAndCaptureListener()
        listener.onAdLoaded(velocityAd)

        listener.onUserRewarded(velocityAd)
        listener.onAdDismissed(velocityAd)

        verify(adCallback).onUserEarnedReward()
        verify(adCallback).onAdClosed()
        verify(velocityAd).destroy()
    }

    // ========== showAd ==========

    @Test
    fun `showAd when the ad is ready shows it with the given context`() {
        `when`(velocityAd.isReady).thenReturn(true)
        loadAndCaptureListener().onAdLoaded(velocityAd)

        mediationAd.showAd(activity)

        verify(velocityAd).show(activity)
        verify(adCallback, never()).onAdFailedToShow(any(AdError::class.java))
    }

    @Test
    fun `showAd when the ad is not ready reports AD_NOT_READY and does not show`() {
        `when`(velocityAd.isReady).thenReturn(false)
        loadAndCaptureListener().onAdLoaded(velocityAd)

        mediationAd.showAd(activity)

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(adCallback).onAdFailedToShow(captor.capture())
        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_AD_NOT_READY, captor.value.code)
        verify(velocityAd, never()).show(activity)
    }
}
