package io.velocityads.gma

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdFormat
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdConfiguration
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [VelocityAdsGmaAdapter].
 *
 * Note on VelocityAds mocking: [io.velocityads.sdk.VelocityAds] is a Kotlin `object` using
 * interface delegation, so its methods are not static invocations — Mockito's `mockStatic`
 * cannot intercept them. Tests that depend on the SDK reporting `isInitialized() == true`
 * are covered at the integration-test level; the unit tests here focus on the adapter's
 * guard logic, wiring, and error delivery — all testable while the SDK stays uninitialised
 * in the test process.
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityAdsGmaAdapterTest {
    private lateinit var context: Context
    private lateinit var adapter: VelocityAdsGmaAdapter

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).get()
        adapter = VelocityAdsGmaAdapter()
    }

    @After
    fun tearDown() {
        resetCompanionState()
    }

    /**
     * Resets the static fields of [VelocityAdsGmaAdapter] shared across instances and
     * tests (stored app key, mismatch flag, shared coalescer). Uses reflection because they
     * are `private` — a `resetForTesting()` hook would widen the production surface.
     */
    private fun resetCompanionState() {
        try {
            val adapterClass = VelocityAdsGmaAdapter::class.java
            adapterClass.getDeclaredField("storedAppKey").apply { isAccessible = true }.set(null, null)
            (adapterClass.getDeclaredField("appKeyMismatchLogged").apply { isAccessible = true }.get(null) as AtomicBoolean).set(false)
            sharedCoalescer()?.complete(false)
        } catch (_: Exception) {
            // Best-effort; failing to reset is not fatal but may cause inter-test interference.
        }
    }

    private fun sharedCoalescer(): InitCoalescer<Boolean>? =
        try {
            val field = VelocityAdsGmaAdapter::class.java.getDeclaredField("initCoalescer")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(null) as? InitCoalescer<Boolean>
        } catch (_: Exception) {
            null
        }

    // ========== Helpers ==========

    private fun serverBundle(parameter: String?): Bundle =
        Bundle().apply {
            if (parameter != null) putString(MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD, parameter)
        }

    private fun initConfiguration(parameter: String?): MediationConfiguration =
        MediationConfiguration(
            AdFormat.INTERSTITIAL,
            serverBundle(parameter),
        )

    private fun interstitialConfiguration(parameter: String?): MediationInterstitialAdConfiguration =
        MediationInterstitialAdConfiguration(
            context,
            "",
            serverBundle(parameter),
            Bundle(),
            false,
            null,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            null,
            "",
        )

    private fun rewardedConfiguration(parameter: String?): MediationRewardedAdConfiguration =
        MediationRewardedAdConfiguration(
            context,
            "",
            serverBundle(parameter),
            Bundle(),
            false,
            null,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            null,
            "",
        )

    private fun bannerConfiguration(
        parameter: String?,
        adSize: AdSize = AdSize.BANNER,
    ): MediationBannerAdConfiguration =
        MediationBannerAdConfiguration(
            context,
            "",
            serverBundle(parameter),
            Bundle(),
            false,
            null,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            MediationAdConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
            "",
            adSize,
            "",
        )

    @Suppress("UNCHECKED_CAST")
    private fun interstitialCallback() = mock(MediationAdLoadCallback::class.java) as InterstitialLoadCallback

    @Suppress("UNCHECKED_CAST")
    private fun rewardedCallback() = mock(MediationAdLoadCallback::class.java) as RewardedLoadCallback

    @Suppress("UNCHECKED_CAST")
    private fun bannerCallback() = mock(MediationAdLoadCallback::class.java) as BannerLoadCallback

    private fun capturedFailure(callback: MediationAdLoadCallback<*, *>): AdError {
        val captor = ArgumentCaptor.forClass(AdError::class.java)
        verify(callback).onFailure(captor.capture())
        return captor.value
    }

    private companion object {
        const val UNIT_ONLY = """{"adUnitId":"unit-1"}"""
        const val WITH_APP_KEY = """{"appKey":"app-1","adUnitId":"unit-1"}"""
    }

    // ========== initialize() ==========

    @Test
    fun `initialize with no appKey in any configuration reports success immediately`() {
        val callback = mock(InitializationCompleteCallback::class.java)

        adapter.initialize(context, callback, listOf(initConfiguration(UNIT_ONLY), initConfiguration(null)))

        // Lazy-init contract: never block the Google Mobile Ads SDK; init happens on first load.
        verify(callback).onInitializationSucceeded()
    }

    @Test
    fun `initialize with empty configuration list reports success immediately`() {
        val callback = mock(InitializationCompleteCallback::class.java)

        adapter.initialize(context, callback, emptyList())

        verify(callback).onInitializationSucceeded()
    }

    @Test
    fun `initialize parks on an in-flight init and reports its outcome`() {
        val coalescer = checkNotNull(sharedCoalescer())
        coalescer.claim { }
        val callback = mock(InitializationCompleteCallback::class.java)

        adapter.initialize(context, callback, listOf(initConfiguration(WITH_APP_KEY)))
        verifyNoInteractions(callback)

        coalescer.complete(false)

        verify(callback).onInitializationFailed("Velocity Ads: initialization failed or timed out")
    }

    @Test
    fun `initialize parked on an in-flight init reports success when it succeeds`() {
        val coalescer = checkNotNull(sharedCoalescer())
        coalescer.claim { }
        val callback = mock(InitializationCompleteCallback::class.java)

        adapter.initialize(context, callback, listOf(initConfiguration(WITH_APP_KEY)))
        coalescer.complete(true)

        verify(callback).onInitializationSucceeded()
    }

    // ========== firstAppKey ==========

    @Test
    fun `firstAppKey returns the first non-blank appKey across configurations`() {
        val configurations =
            listOf(
                initConfiguration(null),
                initConfiguration(UNIT_ONLY),
                initConfiguration("""{"appKey":"app-2","adUnitId":"unit-2"}"""),
                initConfiguration("""{"appKey":"app-3","adUnitId":"unit-3"}"""),
            )
        assertEquals("app-2", VelocityAdsGmaAdapter.firstAppKey(configurations))
    }

    @Test
    fun `firstAppKey returns null when no configuration carries an appKey`() {
        assertNull(VelocityAdsGmaAdapter.firstAppKey(listOf(initConfiguration(UNIT_ONLY), initConfiguration("bare-unit"))))
    }

    // ========== Version reporting ==========

    @Test
    fun `getVersionInfo reflects the adapter version`() {
        val expected = VersionInfoParser.adapterVersion(BuildConfig.ADAPTER_VERSION)
        val actual = adapter.versionInfo
        assertEquals(expected.majorVersion, actual.majorVersion)
        assertEquals(expected.minorVersion, actual.minorVersion)
        assertEquals(expected.microVersion, actual.microVersion)
    }

    // ========== Load — server parameter guard ==========

    @Test
    fun `loadInterstitialAd with missing parameter fails with INVALID_SERVER_PARAMETERS`() {
        val callback = interstitialCallback()

        adapter.loadInterstitialAd(interstitialConfiguration(null), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_SERVER_PARAMETERS, capturedFailure(callback).code)
    }

    @Test
    fun `loadInterstitialAd with JSON lacking adUnitId fails with INVALID_SERVER_PARAMETERS`() {
        val callback = interstitialCallback()

        adapter.loadInterstitialAd(interstitialConfiguration("""{"appKey":"app-1"}"""), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_SERVER_PARAMETERS, capturedFailure(callback).code)
    }

    @Test
    fun `loadRewardedAd with malformed parameter fails with INVALID_SERVER_PARAMETERS`() {
        val callback = rewardedCallback()

        adapter.loadRewardedAd(rewardedConfiguration("{oops"), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_SERVER_PARAMETERS, capturedFailure(callback).code)
    }

    @Test
    fun `loadBannerAd with blank parameter fails with INVALID_SERVER_PARAMETERS`() {
        val callback = bannerCallback()

        adapter.loadBannerAd(bannerConfiguration("   "), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_SERVER_PARAMETERS, capturedFailure(callback).code)
    }

    @Test
    fun `loadBannerAd with FLUID size fails with INVALID_AD_SIZE before touching the SDK`() {
        val callback = bannerCallback()

        adapter.loadBannerAd(bannerConfiguration(WITH_APP_KEY, adSize = AdSize.FLUID), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_AD_SIZE, capturedFailure(callback).code)
    }

    // ========== Load — not-initialized path ==========
    //
    // When the SDK is not initialised (the real state in a unit-test JVM) and no app key
    // is available (no appKey in the parameter, none remembered from initialize()),
    // ensureInitialized() delivers false synchronously — before the coalescer or the SDK.

    @Test
    fun `loadInterstitialAd without any appKey fails with SDK_NOT_INITIALIZED`() {
        val callback = interstitialCallback()

        adapter.loadInterstitialAd(interstitialConfiguration(UNIT_ONLY), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED, capturedFailure(callback).code)
    }

    @Test
    fun `loadRewardedAd without any appKey fails with SDK_NOT_INITIALIZED`() {
        val callback = rewardedCallback()

        adapter.loadRewardedAd(rewardedConfiguration("bare-unit-id"), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED, capturedFailure(callback).code)
    }

    @Test
    fun `loadBannerAd without any appKey fails with SDK_NOT_INITIALIZED`() {
        val callback = bannerCallback()

        adapter.loadBannerAd(bannerConfiguration(UNIT_ONLY), callback)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED, capturedFailure(callback).code)
    }

    // ========== Load — parked on in-flight init ==========
    //
    // Pre-claiming the coalescer keeps the load from winning the claim, so the real
    // Velocity SDK is never touched while still exercising the parked continuation.

    @Test
    fun `parked load fails with SDK_NOT_INITIALIZED when the in-flight init fails`() {
        val coalescer = checkNotNull(sharedCoalescer())
        coalescer.claim { }
        val callback = interstitialCallback()

        adapter.loadInterstitialAd(interstitialConfiguration(WITH_APP_KEY), callback)
        verifyNoInteractions(callback)

        coalescer.complete(false)

        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED, capturedFailure(callback).code)
    }

    @Test
    fun `load uses the appKey remembered from initialize when the parameter carries only an adUnitId`() {
        // initialize() with an appKey parks on a pre-claimed coalescer and remembers the key.
        val coalescer = checkNotNull(sharedCoalescer())
        coalescer.claim { }
        adapter.initialize(context, mock(InitializationCompleteCallback::class.java), listOf(initConfiguration(WITH_APP_KEY)))

        // A load with only an adUnitId must park (not fail fast) because a key is known.
        val callback = interstitialCallback()
        adapter.loadInterstitialAd(interstitialConfiguration(UNIT_ONLY), callback)
        verifyNoInteractions(callback)

        coalescer.complete(false)
        assertEquals(VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED, capturedFailure(callback).code)
    }
}
