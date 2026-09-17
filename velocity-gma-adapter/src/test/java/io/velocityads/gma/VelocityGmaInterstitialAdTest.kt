package io.velocityads.gma

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import io.velocityads.sdk.listeners.VelocityInterstitialAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityInterstitialAd
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
class VelocityGmaInterstitialAdTest {
    private lateinit var activity: Activity
    private lateinit var velocityAd: VelocityInterstitialAd
    private lateinit var loadCallback: InterstitialLoadCallback
    private lateinit var adCallback: MediationInterstitialAdCallback
    private lateinit var mediationAd: VelocityGmaInterstitialAd

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).get()
        velocityAd = mock(VelocityInterstitialAd::class.java)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as InterstitialLoadCallback
        adCallback = mock(MediationInterstitialAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        mediationAd = VelocityGmaInterstitialAd(velocityAd, loadCallback)
    }

    /** Runs `load()` and returns the listener the ad object registered with the Velocity ad. */
    private fun loadAndCaptureListener(): VelocityInterstitialAdListener {
        mediationAd.load()
        val captor: ArgumentCaptor<VelocityInterstitialAdListener> =
            ArgumentCaptor.forClass(VelocityInterstitialAdListener::class.java)
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

        listener.onAdDismissed(velocityAd)

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
        assertEquals(VelocityAdsErrorMapper.ADAPTER_DOMAIN, captor.value.domain)
        verify(velocityAd, never()).show(activity)
    }
}
