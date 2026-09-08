package io.velocityads.gma

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode

/**
 * Builds the [AdError] values the adapter reports to the Google Mobile Ads SDK.
 *
 * Two error domains are used:
 * - [ADAPTER_DOMAIN] for conditions detected by the adapter itself (missing configuration,
 *   SDK not initialized, ad not ready). Codes are the [ADAPTER_ERROR_*] constants.
 * - [SDK_DOMAIN] for errors raised by the Velocity SDK. The primary error carries the
 *   closest [AdRequest] category code so AdMob reporting buckets it correctly; the original
 *   Velocity code and message are preserved verbatim in [AdError.getCause] so they stay
 *   visible in Ad Inspector and logs.
 */
internal object VelocityAdsErrorMapper {
    /** Domain for errors originating in this adapter. */
    const val ADAPTER_DOMAIN = "io.velocityads.gma"

    /** Domain for errors originating in the Velocity Ads SDK. */
    const val SDK_DOMAIN = "io.velocityads.sdk"

    /** The custom-event parameter is missing, malformed, or has no ad unit ID. */
    const val ADAPTER_ERROR_INVALID_SERVER_PARAMETERS = 101

    /** The Velocity SDK could not be initialized before the load. */
    const val ADAPTER_ERROR_SDK_NOT_INITIALIZED = 102

    /** `showAd` was called with no loaded ad. */
    const val ADAPTER_ERROR_AD_NOT_READY = 103

    /** The requested banner size cannot be served by the Velocity SDK. */
    const val ADAPTER_ERROR_INVALID_AD_SIZE = 104

    fun invalidServerParameters(): AdError =
        AdError(
            ADAPTER_ERROR_INVALID_SERVER_PARAMETERS,
            "Velocity Ads: the custom event parameter must be a JSON object with an \"adUnitId\" (and optional \"appKey\").",
            ADAPTER_DOMAIN,
        )

    fun sdkNotInitialized(): AdError =
        AdError(
            ADAPTER_ERROR_SDK_NOT_INITIALIZED,
            "Velocity Ads: SDK is not initialized. Provide an \"appKey\" in the custom event parameter or initialize the SDK in the app.",
            ADAPTER_DOMAIN,
        )

    fun adNotReady(): AdError =
        AdError(
            ADAPTER_ERROR_AD_NOT_READY,
            "Velocity Ads: no ad is loaded and ready to show.",
            ADAPTER_DOMAIN,
        )

    fun invalidAdSize(description: String): AdError =
        AdError(
            ADAPTER_ERROR_INVALID_AD_SIZE,
            "Velocity Ads: unsupported banner size $description.",
            ADAPTER_DOMAIN,
        )

    /**
     * Maps a [VelocityAdsError] to an [AdError] whose code is the closest [AdRequest]
     * error category, with the untouched Velocity error attached as the cause.
     */
    fun toAdError(error: VelocityAdsError): AdError {
        val category =
            when (error.code) {
                VelocityAdsErrorCode.NO_FILL -> AdRequest.ERROR_CODE_NO_FILL

                VelocityAdsErrorCode.NETWORK_ERROR,
                VelocityAdsErrorCode.HTTP_FAILURE,
                VelocityAdsErrorCode.SERVER_ERROR_FIELD,
                -> AdRequest.ERROR_CODE_NETWORK_ERROR

                VelocityAdsErrorCode.INVALID_URL,
                VelocityAdsErrorCode.INVALID_APP_KEY,
                VelocityAdsErrorCode.INVALID_AD_UNIT_ID,
                VelocityAdsErrorCode.SDK_NOT_INITIALIZED,
                VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS,
                VelocityAdsErrorCode.LOAD_ALREADY_IN_PROGRESS,
                VelocityAdsErrorCode.AD_ALREADY_LOADED,
                VelocityAdsErrorCode.AD_SPENT,
                VelocityAdsErrorCode.AD_DESTROYED,
                -> AdRequest.ERROR_CODE_INVALID_REQUEST

                // Parse / response-shape failures, service unavailability, waterfall
                // exhaustion, and anything unknown are internal to the Velocity SDK.
                else -> AdRequest.ERROR_CODE_INTERNAL_ERROR
            }
        val cause = AdError(error.code, error.message, SDK_DOMAIN)
        return AdError(category, "Velocity Ads [${error.code}]: ${error.message}", SDK_DOMAIN, cause)
    }
}
