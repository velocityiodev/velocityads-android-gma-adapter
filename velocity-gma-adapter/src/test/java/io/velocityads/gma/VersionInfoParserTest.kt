package io.velocityads.gma

import kotlin.test.assertEquals
import org.junit.Test

class VersionInfoParserTest {
    @Test
    fun `adapterVersion folds the adapter build into the micro segment`() {
        val info = VersionInfoParser.adapterVersion("0.10.0.0")
        assertEquals(0, info.majorVersion)
        assertEquals(10, info.minorVersion)
        assertEquals(0, info.microVersion)
    }

    @Test
    fun `adapterVersion keeps patch and adapter build distinguishable`() {
        val info = VersionInfoParser.adapterVersion("1.2.3.4")
        assertEquals(1, info.majorVersion)
        assertEquals(2, info.minorVersion)
        assertEquals(304, info.microVersion)
    }

    @Test
    fun `adapterVersion with fewer than four segments yields zero`() {
        val info = VersionInfoParser.adapterVersion("1.2.3")
        assertEquals(0, info.majorVersion)
        assertEquals(0, info.minorVersion)
        assertEquals(0, info.microVersion)
    }

    @Test
    fun `sdkVersion parses three segments`() {
        val info = VersionInfoParser.sdkVersion("0.10.0")
        assertEquals(0, info.majorVersion)
        assertEquals(10, info.minorVersion)
        assertEquals(0, info.microVersion)
    }

    @Test
    fun `sdkVersion ignores a pre-release suffix`() {
        val info = VersionInfoParser.sdkVersion("1.4.2-SNAPSHOT")
        assertEquals(1, info.majorVersion)
        assertEquals(4, info.minorVersion)
        assertEquals(2, info.microVersion)
    }

    @Test
    fun `sdkVersion with garbage yields zero`() {
        val info = VersionInfoParser.sdkVersion("unknown")
        assertEquals(0, info.majorVersion)
        assertEquals(0, info.minorVersion)
        assertEquals(0, info.microVersion)
    }
}
