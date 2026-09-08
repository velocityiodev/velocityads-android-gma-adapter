package io.velocityads.gma

import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import io.velocityads.sdk.ads.banner.VelocityBannerAdView
import io.velocityads.sdk.listeners.VelocityBannerAdListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityBannerAd
import io.velocityads.sdk.models.VelocityBannerAdSize
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityGmaBannerAdTest {
    private lateinit var activity: Activity
    private lateinit var velocityAd: VelocityBannerAd
    private lateinit var adView: VelocityBannerAdView
    private lateinit var loadCallback: BannerLoadCallback
    private lateinit var adCallback: MediationBannerAdCallback
    private lateinit var wrapper: VelocityGmaBannerAd

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        velocityAd = mock(VelocityBannerAd::class.java)
        adView = VelocityBannerAdView(activity)
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(MediationAdLoadCallback::class.java) as BannerLoadCallback
        adCallback = mock(MediationBannerAdCallback::class.java)
        `when`(loadCallback.onSuccess(any())).thenReturn(adCallback)
        wrapper = VelocityGmaBannerAd(velocityAd, adView, loadCallback)
    }

    // ========== resolveAdSize ==========

    @Test
    fun `resolveAdSize maps BANNER to the Velocity BANNER preset`() {
        assertEquals(VelocityBannerAdSize.BANNER, VelocityGmaBannerAd.resolveAdSize(AdSize.BANNER, activity))
    }

    @Test
    fun `resolveAdSize maps MEDIUM_RECTANGLE to MREC`() {
        assertEquals(VelocityBannerAdSize.MREC, VelocityGmaBannerAd.resolveAdSize(AdSize.MEDIUM_RECTANGLE, activity))
    }

    @Test
    fun `resolveAdSize maps LEADERBOARD to LEADERBOARD`() {
        assertEquals(VelocityBannerAdSize.LEADERBOARD, VelocityGmaBannerAd.resolveAdSize(AdSize.LEADERBOARD, activity))
    }

    @Test
    fun `resolveAdSize forwards a concrete anchored-adaptive size as a custom size`() {
        // Anchored adaptive banners arrive as concrete dimensions, e.g. 360x56.
        val size = VelocityGmaBannerAd.resolveAdSize(AdSize(360, 56), activity)
        assertEquals(VelocityBannerAdSize.custom(360, 56), size)
    }

    @Test
    fun `resolveAdSize forwards other fixed IAB sizes as custom sizes`() {
        assertEquals(VelocityBannerAdSize.custom(320, 100), VelocityGmaBannerAd.resolveAdSize(AdSize.LARGE_BANNER, activity))
        assertEquals(VelocityBannerAdSize.custom(468, 60), VelocityGmaBannerAd.resolveAdSize(AdSize.FULL_BANNER, activity))
    }

    @Test
    fun `resolveAdSize uses the screen width for FULL_WIDTH and adaptive height for AUTO_HEIGHT`() {
        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val size = VelocityGmaBannerAd.resolveAdSize(AdSize(AdSize.FULL_WIDTH, AdSize.AUTO_HEIGHT), activity)
        assertEquals(VelocityBannerAdSize.getAdaptiveBannerAdSize(activity, screenWidthDp), size)
    }

    @Test
    fun `resolveAdSize keeps an explicit height with FULL_WIDTH`() {
        val screenWidthDp = activity.resources.configuration.screenWidthDp
        val size = VelocityGmaBannerAd.resolveAdSize(AdSize(AdSize.FULL_WIDTH, 90), activity)
        assertEquals(VelocityBannerAdSize.custom(screenWidthDp, 90), size)
    }

    @Test
    fun `resolveAdSize rejects FLUID`() {
        assertNull(VelocityGmaBannerAd.resolveAdSize(AdSize.FLUID, activity))
    }

    @Test
    fun `resolveAdSize rejects INVALID`() {
        assertNull(VelocityGmaBannerAd.resolveAdSize(AdSize.INVALID, activity))
    }

    // ========== MediationBannerAd ==========

    @Test
    fun `getView returns the hosting Velocity banner view`() {
        assertSame(adView, wrapper.view)
    }

    // ========== load ==========

    /** Runs `load()` and returns the listener the ad object registered with the Velocity ad. */
    private fun loadAndCaptureListener(): VelocityBannerAdListener {
        wrapper.load()
        val viewCaptor: ArgumentCaptor<VelocityBannerAdView> =
            ArgumentCaptor.forClass(VelocityBannerAdView::class.java)
        val listenerCaptor: ArgumentCaptor<VelocityBannerAdListener> =
            ArgumentCaptor.forClass(VelocityBannerAdListener::class.java)
        verify(velocityAd).load(viewCaptor.captureNonNull(), listenerCaptor.captureNonNull())
        assertSame(adView, viewCaptor.value)
        return listenerCaptor.value
    }

    @Test
    fun `load starts the Velocity ad in the hosting view and a successful load hands this object to Google`() {
        val listener = loadAndCaptureListener()

        listener.onAdLoaded(velocityAd)

        verify(loadCallback).onSuccess(wrapper)
    }

    @Test
    fun `a failed load releases the creative`() {
        val listener = loadAndCaptureListener()

        listener.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))

        verify(loadCallback).onFailure(any(AdError::class.java))
        verify(velocityAd).destroy()
    }

    // ========== teardown ==========

    private fun attachToWindow() {
        activity.setContentView(FrameLayout(activity).apply { addView(adView) })
        assertTrue(adView.isAttachedToWindow)
    }

    private fun detachFromWindow() {
        (adView.parent as ViewGroup).removeView(adView)
        assertFalse(adView.isAttachedToWindow)
    }

    private fun advanceMainLooper(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    @Test
    fun `a loaded banner that leaves the window is released after the grace period`() {
        attachToWindow()
        loadAndCaptureListener().onAdLoaded(velocityAd)

        detachFromWindow()
        advanceMainLooper(VelocityGmaBannerAd.DETACH_TEARDOWN_GRACE_MS - 1)
        verify(velocityAd, never()).destroy()

        advanceMainLooper(1)
        verify(velocityAd).destroy()
    }

    @Test
    fun `a banner re-attached within the grace period is kept alive`() {
        attachToWindow()
        loadAndCaptureListener().onAdLoaded(velocityAd)

        detachFromWindow()
        advanceMainLooper(VelocityGmaBannerAd.DETACH_TEARDOWN_GRACE_MS / 2)
        attachToWindow()
        advanceMainLooper(VelocityGmaBannerAd.DETACH_TEARDOWN_GRACE_MS)

        verify(velocityAd, never()).destroy()
    }

    @Test
    fun `the creative is released at most once`() {
        attachToWindow()
        val listener = loadAndCaptureListener()

        listener.onAdFailedToLoad(velocityAd, VelocityAdsError(VelocityAdsErrorCode.NO_FILL, "no fill"))
        detachFromWindow()
        advanceMainLooper(VelocityGmaBannerAd.DETACH_TEARDOWN_GRACE_MS)

        verify(velocityAd, times(1)).destroy()
    }
}
