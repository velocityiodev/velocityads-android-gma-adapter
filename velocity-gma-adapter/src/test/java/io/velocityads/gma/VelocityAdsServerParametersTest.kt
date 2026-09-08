package io.velocityads.gma

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VelocityAdsServerParametersTest {
    @Test
    fun `parse full JSON yields appKey and adUnitId`() {
        val parsed = VelocityAdsServerParameters.parse("""{"appKey":"app-123","adUnitId":"unit-abc"}""")
        assertEquals("app-123", parsed.appKey)
        assertEquals("unit-abc", parsed.adUnitId)
    }

    @Test
    fun `parse JSON with only adUnitId yields null appKey`() {
        val parsed = VelocityAdsServerParameters.parse("""{"adUnitId":"unit-abc"}""")
        assertNull(parsed.appKey)
        assertEquals("unit-abc", parsed.adUnitId)
    }

    @Test
    fun `parse JSON with blank values normalises them to null`() {
        val parsed = VelocityAdsServerParameters.parse("""{"appKey":"   ","adUnitId":""}""")
        assertNull(parsed.appKey)
        assertNull(parsed.adUnitId)
    }

    @Test
    fun `parse tolerates surrounding whitespace and unknown keys`() {
        val parsed = VelocityAdsServerParameters.parse("""  {"adUnitId":"unit-abc","appKey":"app-123","extra":1}  """)
        assertEquals("app-123", parsed.appKey)
        assertEquals("unit-abc", parsed.adUnitId)
    }

    @Test
    fun `parse bare string is treated as the ad unit ID`() {
        val parsed = VelocityAdsServerParameters.parse("  unit-abc ")
        assertNull(parsed.appKey)
        assertEquals("unit-abc", parsed.adUnitId)
    }

    @Test
    fun `parse malformed JSON yields EMPTY`() {
        assertEquals(VelocityAdsServerParameters.EMPTY, VelocityAdsServerParameters.parse("""{"adUnitId": """))
        assertEquals(VelocityAdsServerParameters.EMPTY, VelocityAdsServerParameters.parse("{not json}"))
    }

    @Test
    fun `parse null or blank yields EMPTY`() {
        assertEquals(VelocityAdsServerParameters.EMPTY, VelocityAdsServerParameters.parse(null))
        assertEquals(VelocityAdsServerParameters.EMPTY, VelocityAdsServerParameters.parse(""))
        assertEquals(VelocityAdsServerParameters.EMPTY, VelocityAdsServerParameters.parse("   "))
    }
}
