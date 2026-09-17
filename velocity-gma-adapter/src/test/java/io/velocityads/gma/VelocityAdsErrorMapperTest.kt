package io.velocityads.gma

import com.google.android.gms.ads.AdRequest
import io.velocityads.sdk.models.VelocityAdsError
import io.velocityads.sdk.models.VelocityAdsErrorCode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/**
 * Covers every constant in [VelocityAdsErrorCode] plus the unknown-code fallback. Each
 * mapping must preserve the Velocity code and message in the cause so they stay visible
 * in Ad Inspector and logs.
 */
class VelocityAdsErrorMapperTest {
    private companion object {
        const val TEST_MESSAGE = "test message"
    }

    private fun assertMapping(
        velocityCode: Int,
        expectedCategory: Int,
    ) {
        val mapped = VelocityAdsErrorMapper.toAdError(VelocityAdsError(velocityCode, TEST_MESSAGE))
        assertEquals(expectedCategory, mapped.code, "GMA category code must match")
        assertEquals(VelocityAdsErrorMapper.SDK_DOMAIN, mapped.domain)
        assertEquals("Velocity Ads [$velocityCode]: $TEST_MESSAGE", mapped.message)
        val cause = assertNotNull(mapped.cause, "Velocity error must be preserved as the cause")
        assertEquals(velocityCode, cause.code, "Velocity code must be preserved verbatim")
        assertEquals(TEST_MESSAGE, cause.message, "Velocity message must be preserved verbatim")
        assertEquals(VelocityAdsErrorMapper.SDK_DOMAIN, cause.domain)
    }

    // ========== No fill ==========

    @Test
    fun `NO_FILL maps to ERROR_CODE_NO_FILL so the waterfall advances`() {
        assertMapping(VelocityAdsErrorCode.NO_FILL, AdRequest.ERROR_CODE_NO_FILL)
    }

    // ========== Network ==========

    @Test
    fun `network-layer codes map to ERROR_CODE_NETWORK_ERROR`() {
        assertMapping(VelocityAdsErrorCode.NETWORK_ERROR, AdRequest.ERROR_CODE_NETWORK_ERROR)
        assertMapping(VelocityAdsErrorCode.HTTP_FAILURE, AdRequest.ERROR_CODE_NETWORK_ERROR)
        assertMapping(VelocityAdsErrorCode.SERVER_ERROR_FIELD, AdRequest.ERROR_CODE_NETWORK_ERROR)
    }

    // ========== Invalid request / state ==========

    @Test
    fun `configuration and state codes map to ERROR_CODE_INVALID_REQUEST`() {
        assertMapping(VelocityAdsErrorCode.INVALID_URL, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.INVALID_APP_KEY, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.INVALID_AD_UNIT_ID, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.SDK_NOT_INITIALIZED, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.SDK_INITIALIZATION_IN_PROGRESS, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.LOAD_ALREADY_IN_PROGRESS, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.AD_ALREADY_LOADED, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.AD_SPENT, AdRequest.ERROR_CODE_INVALID_REQUEST)
        assertMapping(VelocityAdsErrorCode.AD_DESTROYED, AdRequest.ERROR_CODE_INVALID_REQUEST)
    }

    // ========== Internal ==========

    @Test
    fun `parse, response and internal codes map to ERROR_CODE_INTERNAL_ERROR`() {
        assertMapping(VelocityAdsErrorCode.JSON_PARSE_ERROR, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.INVALID_RESPONSE, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.EMPTY_RESPONSE_BODY, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.INVALID_AD_RESPONSE, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.LOAD_SERVICE_UNAVAILABLE, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.WATERFALL_LOAD_FAILED, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(VelocityAdsErrorCode.INTERNAL_ERROR, AdRequest.ERROR_CODE_INTERNAL_ERROR)
    }

    @Test
    fun `unknown code maps to ERROR_CODE_INTERNAL_ERROR`() {
        assertMapping(-1, AdRequest.ERROR_CODE_INTERNAL_ERROR)
        assertMapping(9999, AdRequest.ERROR_CODE_INTERNAL_ERROR)
    }

    // ========== Adapter-originated errors ==========

    @Test
    fun `adapter errors use the adapter domain and have no cause`() {
        val errors =
            listOf(
                VelocityAdsErrorMapper.invalidServerParameters() to VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_SERVER_PARAMETERS,
                VelocityAdsErrorMapper.sdkNotInitialized() to VelocityAdsErrorMapper.ADAPTER_ERROR_SDK_NOT_INITIALIZED,
                VelocityAdsErrorMapper.adNotReady() to VelocityAdsErrorMapper.ADAPTER_ERROR_AD_NOT_READY,
                VelocityAdsErrorMapper.invalidAdSize("FLUID") to VelocityAdsErrorMapper.ADAPTER_ERROR_INVALID_AD_SIZE,
            )
        for ((error, expectedCode) in errors) {
            assertEquals(expectedCode, error.code)
            assertEquals(VelocityAdsErrorMapper.ADAPTER_DOMAIN, error.domain)
            assertNull(error.cause)
        }
    }
}
