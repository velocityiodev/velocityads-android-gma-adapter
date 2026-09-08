package io.velocityads.gma

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityRewardedAd
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class VelocityRewardedAdHandlerTest {
    private lateinit var velocityAd: VelocityRewardedAd
    private lateinit var mediationAd: MediationRewardedAd
    private lateinit var loadCallback: RewardedLoadCallback
    private lateinit var adCallback: MediationRewardedAdCallback
    private var finishedCount = 0
    private lateinit var handler: VelocityRewardedAdHandler

    @Before
    fun setUp() {
        velocityAd = mock(VelocityRewardedAd::class.java)
        mediationAd = mock(MediationRewardedAd::class.java)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as RewardedLoadCallback
        adCallback = mock(MediationRewardedAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        finishedCount = 0
        handler = VelocityRewardedAdHandler(mediationAd, loadCallback, onAdFinished = { finishedCount++ })
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
        handler.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NETWORK_ERROR, "offline"))

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(loadCallback).onFailure(captor.capture())
        assertEquals(AdRequest.ERROR_CODE_NETWORK_ERROR, captor.value.code)
        assertEquals(VelocityAdsErrorCode.NETWORK_ERROR, captor.value.cause?.code)
        assertEquals("offline", captor.value.cause?.message)
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
    fun `onUserRewarded delivers the reward via the parameterless overload before close`() {
        handler.onAdLoaded(velocityAd)

        handler.onUserRewarded(velocityAd)
        handler.onAdDismissed(velocityAd)

        val order = inOrder(adCallback)
        order.verify(adCallback).onUserEarnedReward()
        order.verify(adCallback).onAdClosed()
        verify(adCallback, never()).onUserEarnedReward(any())
        verify(adCallback, never()).onVideoStart()
        verify(adCallback, never()).onVideoComplete()
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

        handler.onAdFailedToShow(velocityAd, VelocityAdsError(VelocityAdsErrorCode.INTERNAL_ERROR, "boom"))

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(adCallback).onAdFailedToShow(captor.capture())
        assertEquals(AdRequest.ERROR_CODE_INTERNAL_ERROR, captor.value.code)
        assertEquals(VelocityAdsErrorCode.INTERNAL_ERROR, captor.value.cause?.code)
    }

    @Test
    fun `display callbacks before onSuccess are safe no-ops`() {
        handler.onAdShown(velocityAd)
        handler.onUserRewarded(velocityAd)
        handler.onAdDismissed(velocityAd)

        verifyNoInteractions(adCallback)
    }
}
