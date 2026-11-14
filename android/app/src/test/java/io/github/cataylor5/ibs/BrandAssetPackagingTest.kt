package io.github.cataylor5.ibs

import java.nio.ByteBuffer
import java.io.File
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BrandAssetPackagingTest {
    private fun source(name: String): ByteArray {
        val directory = File("src/main/res/raw").takeIf { it.isDirectory } ?: File("app/src/main/res/raw")
        return Base64.getDecoder().decode(File(directory, "$name.b64").readText())
    }

    private fun dimensions(bytes: ByteArray): Pair<Int, Int> {
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.copyOfRange(0, 8))
        val header = ByteBuffer.wrap(bytes)
        assertEquals(0x49484452, header.getInt(12)) // IHDR
        return header.getInt(16) to header.getInt(20)
    }

    @Test fun originalWordmarkKeepsItsPngDimensionsAndByteCount() {
        val bytes = source("ibs_wordmark")
        assertEquals(571622, bytes.size)
        assertEquals(2106 to 747, dimensions(bytes))
    }

    @Test fun minimalistDaytimeOverviewArtworkKeepsItsSquarePngDimensionsAndByteCount() {
        val bytes = source("ibs_overview_day")
        assertEquals(942772, bytes.size)
        val (width, height) = dimensions(bytes)
        assertEquals(width, height)
        assertTrue(width >= 1000)
    }

    @Test fun originalOverviewArtworkKeepsItsSquarePngDimensionsAndByteCount() {
        val bytes = source("ibs_overview")
        assertEquals(667773, bytes.size)
        val (width, height) = dimensions(bytes)
        assertEquals(width, height)
        assertTrue(width >= 1000)
    }
}
