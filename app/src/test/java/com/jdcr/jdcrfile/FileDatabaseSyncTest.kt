package com.jdcr.jdcrfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDatabaseSyncTest {

    @Test
    fun stableUuid_samePath_isDeterministicLowercaseSha256() {
        val path = "/storage/emulated/0/Movies/demo.mp4"

        val first = FileDatabaseSync.stableUuid(path)
        val second = FileDatabaseSync.stableUuid(path)

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun stableUuid_differentPaths_areDifferent() {
        val first = FileDatabaseSync.stableUuid("/storage/one.mp4")
        val second = FileDatabaseSync.stableUuid("/storage/two.mp4")

        assertNotEquals(first, second)
    }

    @Test
    fun mediaTypeOf_classifiesKnownAndFallbackMimeTypes() {
        assertEquals("audio", FileDatabaseSync.mediaTypeOf("audio/mpeg"))
        assertEquals("video", FileDatabaseSync.mediaTypeOf("video/mp4"))
        assertEquals("image", FileDatabaseSync.mediaTypeOf("image/jpeg"))
        assertEquals("file", FileDatabaseSync.mediaTypeOf("application/pdf"))
    }
}
