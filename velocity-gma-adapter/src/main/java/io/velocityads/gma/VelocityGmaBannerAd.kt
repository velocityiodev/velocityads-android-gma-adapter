package io.velocityads.gma

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import io.velocityads.sdk.ads.banner.VelocityBannerAdView
import io.velocityads.sdk.models.VelocityBannerAd
import io.velocityads.sdk.models.VelocityBannerAdRequest
import io.velocityads.sdk.models.VelocityBannerAdSize

/**
 * Handles the [MediationBannerAd] contract for [VelocityAdsGmaAdapter].
 *
 * Owns the [VelocityBannerAd], the [VelocityBannerAdView] that hosts the creative and the
 * [VelocityBannerAdHandler] for one load cycle. Main-thread-confined: both SDKs deliver every
 * callback on the main thread.
 *
 * The Google Mobile Ads SDK offers no teardown callback for banner ads: on refresh or when the
 * hosting `AdView` is destroyed it simply removes the mediated view and drops this object. The
 * Velocity creative is therefore released when its view leaves the window and is not re-attached
 * within [DETACH_TEARDOWN_GRACE_MS] — long enough to survive reparenting and list recycling, short
 * enough that a dropped banner does not keep its player alive.
 */
internal class VelocityGmaBannerAd(
    private val ad: VelocityBannerAd,
    private val adView: VelocityBannerAdView,
    loadCallback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
) : MediationBannerAd {
    companion object {
        /** How long a detached banner view may stay off-window before its creative is released. */
        internal const val DETACH_TEARDOWN_GRACE_MS = 2_000L

        /**
         * Validates the configuration and size, ensures the Velocity SDK is initialized, then
         * creates and loads the ad. Every failure path reports exactly once via [callback].
         */
        fun load(
            ctx: AdLoadContext,
            configuration: MediationBannerAdConfiguration,
            callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
        ) {
            val parameters = VelocityAdsGmaAdapter.serverParameters(configuration)
            val adUnitId = parameters.adUnitId
            if (adUnitId.isNullOrBlank()) {
                callback.onFailure(VelocityAdsErrorMapper.invalidServerParameters())
                return
            }
            val context = configuration.context
            val size = resolveAdSize(configuration.adSize, context)
            if (size == null) {
                callback.onFailure(VelocityAdsErrorMapper.invalidAdSize(configuration.adSize.toString()))
                return
            }
            ctx.ensureInitialized(context, parameters) { initialized ->
                if (!initialized) {
                    callback.onFailure(VelocityAdsErrorMapper.sdkNotInitialized())
                    return@ensureInitialized
                }
                val request = VelocityBannerAdRequest.Builder(adUnitId, size).build()
                VelocityGmaBannerAd(VelocityBannerAd(request), VelocityBannerAdView(context), callback).load()
            }
        }

        /**
         * Resolves the Velocity banner size for a Google Mobile Ads request.
         *
         * - The standard IAB constants map to their Velocity presets so the server can apply
         *   size-class-specific fill rules.
         * - Anchored adaptive banners arrive as a concrete width × height and are forwarded
         *   verbatim as a custom size.
         * - [AdSize.FULL_WIDTH] resolves to the current screen width; [AdSize.AUTO_HEIGHT]
         *   (inline adaptive without a max height) resolves to the anchored-adaptive height
         *   for that width.
         * - [AdSize.FLUID] has no fixed dimensions and cannot be served — returns `null`.
         */
        internal fun resolveAdSize(
            adSize: AdSize,
            context: Context,
        ): VelocityBannerAdSize? {
            if (adSize.isFluid) return null

            val widthDp = if (adSize.isFullWidth) context.resources.configuration.screenWidthDp else adSize.width
            if (widthDp <= 0) return null
            if (adSize.isAutoHeight) {
                return VelocityBannerAdSize.getAdaptiveBannerAdSize(context, widthDp)
            }
            val heightDp = adSize.height
            if (heightDp <= 0) return null

            return when {
                widthDp == AdSize.BANNER.width && heightDp == AdSize.BANNER.height -> VelocityBannerAdSize.BANNER
                widthDp == AdSize.MEDIUM_RECTANGLE.width && heightDp == AdSize.MEDIUM_RECTANGLE.height -> VelocityBannerAdSize.MREC
                widthDp == AdSize.LEADERBOARD.width && heightDp == AdSize.LEADERBOARD.height -> VelocityBannerAdSize.LEADERBOARD
                else -> VelocityBannerAdSize.custom(widthDp, heightDp)
            }
        }
    }

    private val handler = VelocityBannerAdHandler(this, loadCallback, onLoadFailed = ::release)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val detachTeardown =
        Runnable {
            if (!adView.isAttachedToWindow) release()
        }

    private val attachStateListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                mainHandler.removeCallbacks(detachTeardown)
            }

            override fun onViewDetachedFromWindow(v: View) {
                mainHandler.postDelayed(detachTeardown, DETACH_TEARDOWN_GRACE_MS)
            }
        }

    init {
        adView.addOnAttachStateChangeListener(attachStateListener)
    }

    fun load() {
        ad.load(adView, handler)
    }

    override fun getView(): View = adView

    private fun release() {
        mainHandler.removeCallbacks(detachTeardown)
        adView.removeOnAttachStateChangeListener(attachStateListener)
        ad.destroy()
    }
}
