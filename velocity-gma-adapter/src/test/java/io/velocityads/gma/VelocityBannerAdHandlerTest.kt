package io.velocityads.gma

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityBannerAd
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

class VelocityBannerAdHandlerTest {
    private lateinit var velocityAd: VelocityBannerAd
    private lateinit var mediationAd: MediationBannerAd
    private lateinit var loadCallback: BannerLoadCallback
    private lateinit var adCallback: MediationBannerAdCallback
    private var loadFailedCount = 0
    private lateinit var handler: VelocityBannerAdHandler

    @Before
    fun setUp() {
        velocityAd = mock(VelocityBannerAd::class.java)
        mediationAd = mock(MediationBannerAd::class.java)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as BannerLoadCallback
        adCallback = mock(MediationBannerAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        loadFailedCount = 0
        handler = VelocityBannerAdHandler(mediationAd, loadCallback, onLoadFailed = { loadFailedCount++ })
    }

    @Test
    fun `onAdLoaded hands the mediation ad to the load callback and keeps the returned ad callback`() {
        handler.onAdLoaded(velocityAd)

        verify(loadCallback).onSuccess(mediationAd)
        assertSame(adCallback, handler.adCallback)
    }

    @Test
    fun `onAdFailedToLoad forwards the mapped error and signals the load failure`() {
        handler.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(loadCallback).onFailure(captor.capture())
        assertEquals(AdRequest.ERROR_CODE_NO_FILL, captor.value.code)
        assertEquals(VelocityAdsErrorCode.NO_FILL, captor.value.cause?.code)
        assertEquals(1, loadFailedCount)
        assertNull(handler.adCallback)
    }

    @Test
    fun `onAdImpression and onAdClicked are forwarded after onSuccess`() {
        handler.onAdLoaded(velocityAd)

        handler.onAdImpression(velocityAd)
        handler.onAdClicked(velocityAd)

        verify(adCallback).reportAdImpression()
        verify(adCallback).reportAdClicked()
    }

    @Test
    fun `onAdFailedToShow after load does not reach the ad callback`() {
        handler.onAdLoaded(velocityAd)

        handler.onAdFailedToShow(velocityAd, VelocityAdsError(VelocityAdsErrorCode.INTERNAL_ERROR, "boom"))

        verifyNoInteractions(adCallback)
    }

    @Test
    fun `display callbacks before onSuccess are safe no-ops`() {
        handler.onAdImpression(velocityAd)
        handler.onAdClicked(velocityAd)

        verifyNoInteractions(adCallback)
    }
}
