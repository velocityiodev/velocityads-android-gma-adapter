package io.velocityads.gma

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        private const val INIT_POLL_INTERVAL_MS = 200L
        private const val INIT_POLL_TIMEOUT_MS = 5_000L

        /**
         * Shared across adapter instances (the Google Mobile Ads SDK creates one per ad
         * request) because Velocity init is process-global. Main-thread-confined — see
         * [InitCoalescer].
         */
        private val initCoalescer = InitCoalescer<Boolean>()

        /**
         * The app key captured from the first successful sighting, used as a fallback for
         * load-time init attempts whose parameter carries only an ad unit ID. First-wins:
         * Velocity init is process-global, so later mismatched keys are logged and ignored.
         */
        @Volatile private var storedAppKey: String? = null

        private val appKeyMismatchLogged = AtomicBoolean(false)

        private fun rememberAppKey(appKey: String) {
            val previous = storedAppKey
            if (previous == null) {
                storedAppKey = appKey
                return
            }
            if (previous != appKey && appKeyMismatchLogged.compareAndSet(false, true)) {
                Log.w(
                    TAG,
                    "Velocity Ads: multiple appKey values detected across custom event parameters. " +
                        "Use one Velocity app key per application process.",
                )
            }
        }

        /**
         * Mediation name reported to the Velocity SDK via [VelocityAdsMediationBridge].
         * Owned by this adapter — the SDK accepts any lowercase canonical string.
         */
        private const val MEDIATION_NAME = "gma"

        private val mediationInfoForwarded = AtomicBoolean(false)

        /**
         * Reports the mediation environment to the Velocity SDK. Safe to call from any
         * adapter entry point; only the first call has an effect.
         */
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
        // Identify the mediation environment before SDK init so the very first
        // request and event carry it.
        forwardMediationInfo()

        if (VelocityAds.isInitialized()) {
            callback.onInitializationSucceeded()
            return
        }

        val appKey = firstAppKey(configurations)
        if (appKey.isNullOrBlank()) {
            // No mapping carries an appKey: either the host app initializes the Velocity SDK
            // itself, or the key arrives at load time. Report success so the Google Mobile
            // Ads SDK's own initialization is never blocked; ensureInitialized() performs the
            // real SDK init lazily on the first load.
            callback.onInitializationSucceeded()
            return
        }

        rememberAppKey(appKey)

        runOnMainNow {
            if (VelocityAds.isInitialized()) {
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
        if (VelocityAds.isInitialized()) {
            onReady(true)
            return
        }

        val loadAppKey = parameters.appKey
        if (!loadAppKey.isNullOrBlank()) {
            rememberAppKey(loadAppKey)
        }
        val appKey = loadAppKey ?: storedAppKey
        if (appKey.isNullOrBlank()) {
            onReady(false)
            return
        }

        runOnMainNow {
            if (VelocityAds.isInitialized()) {
                onReady(true)
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
     * Performs the actual [VelocityAds.initSDK] call on behalf of the caller that won
     * the coalescer claim, broadcasting the outcome to every parked handler when the
     * SDK responds.
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
                        // Another caller (e.g. the host app) owns the in-flight init —
                        // wait for its outcome instead of failing the parked loads.
                        awaitInFlightInitialization(context, appKey) { initialized ->
                            initCoalescer.complete(initialized)
                        }
                        return
                    }
                    Log.w(TAG, "Velocity Ads initialization failed [${error.code}]: ${error.message}")
                    initCoalescer.complete(false)
                }
            }
        try {
            VelocityAds.initSDK(context, initRequest, initListener)
        } catch (t: Throwable) {
            // The Velocity SDK's public API contract is no-throw, but a synchronous throw
            // here would otherwise strand the claimed coalescer forever (parking every
            // future load) and propagate a crash into the host app. Fail cleanly instead.
            Log.e(TAG, "Velocity Ads initSDK threw unexpectedly", t)
            initCoalescer.complete(false)
        }
    }

    /**
     * Polls on the main thread until the host-owned in-flight initialization resolves, then
     * either succeeds fast or re-attempts [VelocityAds.initSDK] itself — the Velocity SDK
     * permits re-init from its FAILED state, so a failed host init is retried immediately
     * instead of waiting out the full poll window. [onResult] is invoked exactly once.
     */
    private fun awaitInFlightInitialization(
        context: Context,
        appKey: String,
        onResult: (Boolean) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val deadlineUptimeMs = SystemClock.uptimeMillis() + INIT_POLL_TIMEOUT_MS
        var settled = false
        val settle: (Boolean) -> Unit = { initialized ->
            if (!settled) {
                settled = true
                onResult(initialized)
            }
        }
        val attempt =
            object : Runnable {
                override fun run() {
                    if (settled) return
                    if (VelocityAds.isInitialized()) {
                        settle(true)
                        return
                    }
                    if (SystemClock.uptimeMillis() >= deadlineUptimeMs) {
                        settle(false)
                        return
                    }
                    val reattempt = this
                    val retryListener =
                        object : VelocityAdsInitListener {
                            override fun onInitSuccess() {
                                settle(true)
                            }

                            override fun onInitFailure(error: VelocityAdsError) {
                                if (settled) return
                                if (error.code == VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS) {
                                    handler.postDelayed(reattempt, INIT_POLL_INTERVAL_MS)
                                } else {
                                    Log.w(TAG, "Velocity Ads re-init after host init failed [${error.code}]: ${error.message}")
                                    settle(false)
                                }
                            }
                        }
                    val initRequest = VelocityAdsInitRequest.Builder(appKey).build()
                    try {
                        VelocityAds.initSDK(context, initRequest, retryListener)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Velocity Ads initSDK threw unexpectedly during re-init", t)
                        settle(false)
                    }
                }
            }
        handler.post(attempt)
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
