package com.miaom.schedule.platform.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareImportSupportTest {
    @Test
    fun `guess xlsx content type from path segment`() {
        val result = ShareImportSupport.guessContentTypeFromPathSegment("schedule.xlsx")

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result)
    }

    @Test
    fun `guess csv and json content types from path segment`() {
        assertEquals("text/csv", ShareImportSupport.guessContentTypeFromPathSegment("export.csv"))
        assertEquals("text/tab-separated-values", ShareImportSupport.guessContentTypeFromPathSegment("export.tsv"))
        assertEquals("application/json", ShareImportSupport.guessContentTypeFromPathSegment("schedule.json"))
    }

    @Test
    fun `guess content type from query filename in path segment`() {
        assertEquals(
            "text/tab-separated-values",
            ShareImportSupport.guessContentTypeFromPathSegment("download?filename=schedule.tsv&token=abc")
        )
        assertEquals(
            "text/csv",
            ShareImportSupport.guessContentTypeFromPathSegment("share?file=%E8%AF%BE%E8%A1%A8.csv")
        )
    }

    @Test
    fun `guess image content types and detect image payload`() {
        assertEquals("image/png", ShareImportSupport.guessContentTypeFromPathSegment("schedule.png"))
        assertEquals("image/jpeg", ShareImportSupport.guessContentTypeFromPathSegment("schedule.jpg"))
        assertTrue(ShareImportSupport.isImagePayload("image/webp"))
        assertTrue(ShareImportSupport.isImagePayload(null, "课程表截图.heic"))
        assertFalse(ShareImportSupport.isImagePayload("text/plain", "schedule.txt"))
    }

    @Test
    fun `recognize remote and local schemes`() {
        assertTrue(ShareImportSupport.isRemoteUriScheme("https"))
        assertTrue(ShareImportSupport.isRemoteUriScheme("webcal"))
        assertFalse(ShareImportSupport.isRemoteUriScheme("content"))
        assertFalse(ShareImportSupport.isRemoteUriScheme("file"))
    }
}
