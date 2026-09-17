package io.velocityads.gma

import org.json.JSONException
import org.json.JSONObject

/**
 * The Velocity configuration carried by the single custom-event **parameter** string that
 * the AdMob UI passes to the adapter.
 *
 * Accepted formats:
 * - JSON object — `{"appKey":"<velocity app key>","adUnitId":"<velocity ad unit id>"}`.
 *   `appKey` is optional when the host app initializes the Velocity SDK itself.
 * - Bare string — treated as the ad unit ID alone (no app key).
 *
 * Blank values are normalised to `null` so callers only need a single null check.
 */
internal data class VelocityAdsServerParameters(
    val appKey: String?,
    val adUnitId: String?,
) {
    companion object {
        internal const val KEY_APP_KEY = "appKey"
        internal const val KEY_AD_UNIT_ID = "adUnitId"

        val EMPTY = VelocityAdsServerParameters(appKey = null, adUnitId = null)

        /**
         * Parses the raw custom-event parameter. Never throws: malformed JSON that starts
         * with `{` yields [EMPTY]; any other non-blank string is taken as a bare ad unit ID.
         */
        fun parse(raw: String?): VelocityAdsServerParameters {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return EMPTY
            if (!trimmed.startsWith("{")) {
                return VelocityAdsServerParameters(appKey = null, adUnitId = trimmed)
            }
            return try {
                val json = JSONObject(trimmed)
                VelocityAdsServerParameters(
                    appKey = json.optString(KEY_APP_KEY).takeUnless { it.isBlank() },
                    adUnitId = json.optString(KEY_AD_UNIT_ID).takeUnless { it.isBlank() },
                )
            } catch (_: JSONException) {
                EMPTY
            }
        }
    }
}
