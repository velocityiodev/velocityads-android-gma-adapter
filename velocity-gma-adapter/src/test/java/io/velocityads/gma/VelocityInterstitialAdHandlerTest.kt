package io.velocityads.gma

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityInterstitialAd
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class VelocityInterstitialAdHandlerTest {
    private lateinit var velocityAd: VelocityInterstitialAd
    private lateinit var mediationAd: MediationInterstitialAd
    private lateinit var loadCallback: InterstitialLoadCallback
    private lateinit var adCallback: MediationInterstitialAdCallback
    private var finishedCount = 0
    private lateinit var handler: VelocityInterstitialAdHandler

    @Before
    fun setUp() {
        velocityAd = mock(VelocityInterstitialAd::class.java)
        mediationAd = mock(MediationInterstitialAd::class.java)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as InterstitialLoadCallback
        adCallback = mock(MediationInterstitialAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        finishedCount = 0
        handler = VelocityInterstitialAdHandler(mediationAd, loadCallback, onAdFinished = { finishedCount++ })
    }

    // ========== Load phase ==========

    @Test
    fun `onAdLoaded hands the mediation ad to the load callback and keeps the returned ad callback`() {
        handler.onAdLoaded(velocityAd)

        verify(loadCallback).onSuccess(mediationAd)
        assertSame(adCallback, handler.adCallback)
    }

    @Test
    fun `onAdFailedToLoad forwards the mapped error and signals the ad is finished`() {
        handler.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(loadCallback).onFailure(captor.capture())
        assertEquals(AdRequest.ERROR_CODE_NO_FILL, captor.value.code)
        assertEquals(VelocityAdsErrorCode.NO_FILL, captor.value.cause?.code)
        assertEquals("no fill", captor.value.cause?.message)
        assertEquals(1, finishedCount)
        assertNull(handler.adCallback)
    }

    // ========== Display phase ==========

    @Test
    fun `display callbacks are forwarded to the ad callback returned by onSuccess`() {
        handler.onAdLoaded(velocityAd)

        handler.onAdShown(velocityAd)
        handler.onAdImpression(velocityAd)
        handler.onAdClicked(velocityAd)
        handler.onAdDismissed(velocityAd)

        verify(adCallback).onAdOpened()
        verify(adCallback).reportAdImpression()
        verify(adCallback).reportAdClicked()
        verify(adCallback).onAdClosed()
    }

    @Test
    fun `onAdDismissed signals the ad is finished`() {
        handler.onAdLoaded(velocityAd)

        handler.onAdDismissed(velocityAd)

        assertEquals(1, finishedCount)
    }

    @Test
    fun `onAdFailedToShow forwards the mapped error`() {
        handler.onAdLoaded(velocityAd)

        handler.onAdFailedToShow(velocityAd, VelocityAdsError(VelocityAdsErrorCode.AD_SPENT, "spent"))

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(adCallback).onAdFailedToShow(captor.capture())
        assertEquals(AdRequest.ERROR_CODE_INVALID_REQUEST, captor.value.code)
        assertEquals(VelocityAdsErrorCode.AD_SPENT, captor.value.cause?.code)
    }

    @Test
    fun `display callbacks before onSuccess are safe no-ops`() {
        handler.onAdShown(velocityAd)
        handler.onAdClicked(velocityAd)
        handler.onAdDismissed(velocityAd)

        verifyNoInteractions(adCallback)
    }
}
