package com.miaom.schedule.platform.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class RemoteScheduleFetcherTest {
    private val fetcher = RemoteScheduleFetcher()

    @Test
    fun `normalize webcal url to https`() {
        val result = fetcher.normalizeUrl("webcal://example.com/schedule.ics")

        assertEquals("https://example.com/schedule.ics", result)
    }

    @Test
    fun `normalize bare host to https`() {
        val result = fetcher.normalizeUrl("example.com/schedule.html")

        assertEquals("https://example.com/schedule.html", result)
    }

    @Test
    fun `resolve download link from html landing page`() {
        val html = """
            <html>
            <body>
              <p>请点击下载课表</p>
              <a href="/exports/schedule.ics">下载 ICS</a>
            </body>
            </html>
        """.trimIndent().encodeToByteArray()

        val result = fetcher.resolveDownloadLinkFromHtml(
            currentUrl = "https://example.com/share/page",
            contentType = "text/html; charset=utf-8",
            bytes = html
        )

        assertEquals("https://example.com/exports/schedule.ics", result)
    }

    @Test
    fun `do not resolve download link from real schedule html table`() {
        val html = """
            <table>
              <tr><th>周几</th><th>节次</th><th>课程</th></tr>
              <tr><td>周一</td><td>1-2节</td><td>高等数学</td></tr>
            </table>
        """.trimIndent().encodeToByteArray()

        val result = fetcher.resolveDownloadLinkFromHtml(
            currentUrl = "https://example.com/schedule",
            contentType = "text/html",
            bytes = html
        )

        assertNull(result)
    }

    @Test
    fun `resolve download link from non utf8 html landing page`() {
        val html = """
            <html>
            <body>
              <p>请点击导出课表</p>
              <a href="/exports/schedule.xlsx">下载 Excel</a>
            </body>
            </html>
        """.trimIndent().toByteArray(Charset.forName("GB18030"))

        val result = fetcher.resolveDownloadLinkFromHtml(
            currentUrl = "https://example.com/share/page",
            contentType = "text/html; charset=GB18030",
            bytes = html
        )

        assertEquals("https://example.com/exports/schedule.xlsx", result)
    }

    @Test
    fun `resolve download link when signed file url keeps extension in query string`() {
        val html = """
            <html>
            <body>
              <p>请点击下载课表</p>
              <a href="https://example.com/share/file?target=schedule.ics&token=abc">下载 ICS</a>
            </body>
            </html>
        """.trimIndent().encodeToByteArray()

        val result = fetcher.resolveDownloadLinkFromHtml(
            currentUrl = "https://example.com/share/page",
            contentType = "text/html; charset=utf-8",
            bytes = html
        )

        assertEquals("https://example.com/share/file?target=schedule.ics&token=abc", result)
    }

    @Test
    fun `resolve download link when query contains tsv filename`() {
        val html = """
            <html>
            <body>
              <p>请点击下载课表</p>
              <a href="https://example.com/share/file?filename=schedule.tsv&token=abc">下载 TSV</a>
            </body>
            </html>
        """.trimIndent().encodeToByteArray()

        val result = fetcher.resolveDownloadLinkFromHtml(
            currentUrl = "https://example.com/share/page",
            contentType = "text/html; charset=utf-8",
            bytes = html
        )

        assertEquals("https://example.com/share/file?filename=schedule.tsv&token=abc", result)
    }

    @Test
    fun `append content disposition filename to effective content type`() {
        val result = fetcher.buildEffectiveContentType(
            contentType = "application/octet-stream",
            contentDisposition = "attachment; filename=course-table.xlsx"
        )

        assertEquals("application/octet-stream; filename=course-table.xlsx", result)
    }
}
