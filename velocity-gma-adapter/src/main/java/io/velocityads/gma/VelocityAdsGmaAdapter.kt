package io.velocityads.gma

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdConfiguration
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import io.velocityads.sdk.VelocityAds
import io.velocityads.sdk.VelocityAdsMediationBridge
import io.velocityads.sdk.listeners.VelocityAdsInitListener
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import io.velocityads.sdk.models.VelocityAdsInitRequest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Mobile Ads (AdMob / Google Ad Manager) custom event adapter for the Velocity Ads SDK.
 *
 * Custom event class name: `io.velocityads.gma.VelocityAdsGmaAdapter`
 *
 * The AdMob UI passes a single **parameter** string per custom event mapping; see
 * [VelocityAdsServerParameters] for the accepted format.
 */
class VelocityAdsGmaAdapter : Adapter() {
    companion object {
        private const val TAG = "VelocityAdsGmaAdapter"
        private const val MEDIATION_NAME = "gma"

        /**
         * Shared across adapter instances (the Google Mobile Ads SDK creates one per ad
         * request) because Velocity init is process-global. Main-thread-confined — see
         * [InitCoalescer].
         */
        private val initCoalescer = InitCoalescer<Boolean>()

        /**
         * The app key captured from the first sighting, used as a fallback for load-time init
         * attempts whose parameter carries only an ad unit ID. First-wins: Velocity init is
         * process-global, so later mismatched keys are logged and ignored. Main-thread-confined
         * like [initCoalescer].
         */
        private var storedAppKey: String? = null

        private var appKeyMismatchLogged = false

        private val mediationInfoForwarded = AtomicBoolean(false)

        /**
         * Test seam: performs the Velocity SDK initialization call. Production wiring is
         * [VelocityAds.initSDK]; tests substitute a fake so the coalesced init flow can be
         * driven deterministically without network I/O.
         */
        internal var initSdkRunner: (Context, VelocityAdsInitRequest, VelocityAdsInitListener) -> Unit = VelocityAds::initSDK

        /** Test seam: reports whether the Velocity SDK is initialized. Production wiring is [VelocityAds.isInitialized]. */
        internal var isSdkInitialized: () -> Boolean = VelocityAds::isInitialized

        /**
         * Test-only: drains and unclaims the shared coalescer and clears all remembered state
         * and seams so nothing leaks between test cases.
         */
        internal fun resetForTesting() {
            if (initCoalescer.isClaimed) initCoalescer.complete(false)
            storedAppKey = null
            appKeyMismatchLogged = false
            initSdkRunner = VelocityAds::initSDK
            isSdkInitialized = VelocityAds::isInitialized
        }

        /**
         * Records [appKey] and returns the key every init attempt must use: the first key ever
         * seen. A later, different key is logged once and ignored.
         */
        private fun rememberAppKey(appKey: String): String {
            val previous = storedAppKey
            if (previous == null) {
                storedAppKey = appKey
                return appKey
            }
            if (previous != appKey && !appKeyMismatchLogged) {
                appKeyMismatchLogged = true
                Log.w(
                    TAG,
                    "Velocity Ads: multiple appKey values detected across custom event parameters. " +
                        "Use one Velocity app key per application process.",
                )
            }
            return previous
        }

        /** Reports the mediation environment to the Velocity SDK. Idempotent; safe from any entry point. */
        internal fun forwardMediationInfo() {
            if (!mediationInfoForwarded.compareAndSet(false, true)) return
            val gmaVersion =
                try {
                    MobileAds.getVersion().toString()
                } catch (_: Exception) {
                    null
                }
            VelocityAdsMediationBridge.setMediationInfo(MEDIATION_NAME, BuildConfig.ADAPTER_VERSION, gmaVersion)
        }

        /**
         * Reads and parses the Velocity configuration from a load-time configuration's
         * server parameters.
         */
        internal fun serverParameters(configuration: MediationAdConfiguration): VelocityAdsServerParameters =
            VelocityAdsServerParameters.parse(
                configuration.serverParameters.getString(MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD),
            )

        /**
         * Finds the first non-blank `appKey` across the configurations the Google Mobile Ads
         * SDK hands to [initialize] — one per custom event mapping in the account.
         */
        internal fun firstAppKey(configurations: List<MediationConfiguration>): String? =
            configurations
                .asSequence()
                .map { it.serverParameters.getString(MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD) }
                .map { VelocityAdsServerParameters.parse(it).appKey }
                .firstOrNull { !it.isNullOrBlank() }
    }

    // =========================================================================
    // Adapter
    // =========================================================================

    override fun initialize(
        context: Context,
        callback: InitializationCompleteCallback,
        configurations: List<MediationConfiguration>,
    ) {
        forwardMediationInfo()

        if (isSdkInitialized()) {
            callback.onInitializationSucceeded()
            return
        }

        val configuredAppKey = firstAppKey(configurations)
        if (configuredAppKey.isNullOrBlank()) {
            // No mapping carries an appKey: either the host app initializes the Velocity SDK
            // itself, or the key arrives at load time. Report success so the Google Mobile
            // Ads SDK's own initialization is never blocked; ensureInitialized() performs the
            // real SDK init lazily on the first load.
            callback.onInitializationSucceeded()
            return
        }

        runOnMainNow {
            val appKey = rememberAppKey(configuredAppKey)
            if (isSdkInitialized()) {
                callback.onInitializationSucceeded()
                return@runOnMainNow
            }
            val won =
                initCoalescer.claim { initialized ->
                    if (initialized) {
                        callback.onInitializationSucceeded()
                    } else {
                        callback.onInitializationFailed("Velocity Ads: initialization failed or timed out")
                    }
                }
            if (won) {
                startClaimedInit(context.applicationContext, appKey)
            }
        }
    }

    override fun getVersionInfo(): VersionInfo = VersionInfoParser.adapterVersion(BuildConfig.ADAPTER_VERSION)

    override fun getSDKVersionInfo(): VersionInfo = VersionInfoParser.sdkVersion(VelocityAds.getSdkVersion())

    override fun loadInterstitialAd(
        configuration: MediationInterstitialAdConfiguration,
        callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
    ) {
        forwardMediationInfo()
        runOnMainNow { VelocityGmaInterstitialAd.load(loadContext, configuration, callback) }
    }

    override fun loadRewardedAd(
        configuration: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ) {
        forwardMediationInfo()
        runOnMainNow { VelocityGmaRewardedAd.load(loadContext, configuration, callback) }
    }

    override fun loadBannerAd(
        configuration: MediationBannerAdConfiguration,
        callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
    ) {
        forwardMediationInfo()
        runOnMainNow { VelocityGmaBannerAd.load(loadContext, configuration, callback) }
    }

    // =========================================================================
    // AdLoadContext
    // =========================================================================

    /**
     * The seam the per-format ad classes use to reach [ensureInitialized]. Kept as a
     * separate object so the public adapter class does not expose internal types.
     */
    internal val loadContext: AdLoadContext =
        object : AdLoadContext {
            override fun ensureInitialized(
                context: Context,
                parameters: VelocityAdsServerParameters,
                onReady: (Boolean) -> Unit,
            ) = this@VelocityAdsGmaAdapter.ensureInitialized(context, parameters, onReady)
        }

    /**
     * Ensures the Velocity SDK is initialized before a load proceeds.
     *
     * If the SDK is already up, [onReady] fires with `true` synchronously. Otherwise an init
     * is attempted (or coalesced onto an in-flight attempt) with the `appKey` from the
     * load-time parameters, falling back to the key remembered from [initialize]. This
     * covers both the lazy-init contract (no key at startup) and transient failures of the
     * startup init (e.g. no connectivity at launch) — the Velocity SDK explicitly permits
     * re-init from its FAILED state.
     */
    internal fun ensureInitialized(
        context: Context,
        parameters: VelocityAdsServerParameters,
        onReady: (Boolean) -> Unit,
    ) {
        if (isSdkInitialized()) {
            onReady(true)
            return
        }

        runOnMainNow {
            if (isSdkInitialized()) {
                onReady(true)
                return@runOnMainNow
            }
            val appKey = parameters.appKey?.let(::rememberAppKey) ?: storedAppKey
            if (appKey.isNullOrBlank()) {
                onReady(false)
                return@runOnMainNow
            }
            val won = initCoalescer.claim(onReady)
            if (won) {
                startClaimedInit(context.applicationContext, appKey)
            }
        }
    }

    // =========================================================================
    // Init helpers
    // =========================================================================

    /**
     * Performs the actual Velocity SDK init call on behalf of the caller that won the
     * coalescer claim, broadcasting the outcome to every parked handler when the SDK responds.
     *
     * If the SDK reports `SDK_INITIALIZATION_IN_PROGRESS` — the host app called `initSDK`
     * moments before the adapter did — the claim stays held and [InFlightInitPoller] waits for
     * that init to settle, so concurrent callers keep parking on the coalescer instead of
     * failing. A host init that fails inside the poll window surfaces as a timeout; the next
     * load re-attempts init, which the Velocity SDK permits from its FAILED state.
     */
    private fun startClaimedInit(
        context: Context,
        appKey: String,
    ) {
        val initRequest = VelocityAdsInitRequest.Builder(appKey).build()
        val initListener =
            object : VelocityAdsInitListener {
                override fun onInitSuccess() {
                    initCoalescer.complete(true)
                }

                override fun onInitFailure(error: VelocityAdsError) {
                    if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                        InFlightInitPoller.awaitInitialization(isInitialized = isSdkInitialized) { initialized ->
                            if (!initialized) {
                                Log.w(TAG, "Velocity Ads: timed out waiting for in-flight SDK initialization")
                            }
                            initCoalescer.complete(initialized)
                        }
                        return
                    }
                    Log.w(TAG, "Velocity Ads initialization failed [${error.code}]: ${error.message}")
                    initCoalescer.complete(false)
                }
            }
        try {
            initSdkRunner(context, initRequest, initListener)
        } catch (t: Throwable) {
            // The Velocity SDK's public API contract is no-throw, but a synchronous throw
            // here would otherwise strand the claimed coalescer forever (parking every
            // future load) and propagate a crash into the host app. Fail cleanly instead.
            Log.e(TAG, "Velocity Ads initSDK threw unexpectedly", t)
            initCoalescer.complete(false)
        }
    }

    /**
     * Executes [block] on the main thread — inline when already there, otherwise posted.
     * The Google Mobile Ads SDK invokes adapter entry points on the main thread, so the
     * inline path is the norm; the post fallback keeps the main-thread-confined
     * [initCoalescer] safe if a caller strays off-main.
     */
    private fun runOnMainNow(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }
}
