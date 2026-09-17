package io.velocityads.gma

import com.google.android.gms.ads.VersionInfo

/**
 * Converts dotted version strings into the [VersionInfo] structure the Google Mobile Ads
 * SDK expects from mediation adapters.
 */
internal object VersionInfoParser {
    private val ZERO = VersionInfo(0, 0, 0)

    /**
     * Parses the 4-segment adapter version `A.B.C.D` following Google's mediation-adapter
     * convention: major `A`, minor `B`, micro `C * 100 + D`, so the adapter-build segment
     * stays visible alongside the wrapped SDK patch version.
     */
    fun adapterVersion(version: String): VersionInfo {
        val parts = segments(version)
        if (parts.size < 4) return ZERO
        return VersionInfo(parts[0], parts[1], parts[2] * 100 + parts[3])
    }

    /** Parses a 3-segment (or longer) SDK version `A.B.C` into major / minor / micro. */
    fun sdkVersion(version: String): VersionInfo {
        val parts = segments(version)
        if (parts.size < 3) return ZERO
        return VersionInfo(parts[0], parts[1], parts[2])
    }

    private fun segments(version: String): List<Int> {
        val numeric = version.trim().substringBefore('-')
        return numeric.split('.').mapNotNull { it.toIntOrNull() }
    }
}
