package com.miaom.schedule.platform.share

import com.miaom.schedule.data.transfer.PendingImportDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareImportHandlerTest {
    @Test
    fun `classify multi share payload distinguishes all images mixed and non images`() {
        assertEquals(
            MultiShareKind.AllImages,
            classifyMultiSharePayload(
                listOf(
                    SharePayloadDescriptor(lastPathSegment = "a.png", contentType = "image/png"),
                    SharePayloadDescriptor(lastPathSegment = "b.jpg", contentType = "image/jpeg")
                )
            )
        )
        assertEquals(
            MultiShareKind.Mixed,
            classifyMultiSharePayload(
                listOf(
                    SharePayloadDescriptor(lastPathSegment = "a.png", contentType = "image/png"),
                    SharePayloadDescriptor(lastPathSegment = "b.ics", contentType = "text/calendar")
                )
            )
        )
        assertEquals(
            MultiShareKind.MultipleNonImages,
            classifyMultiSharePayload(
                listOf(
                    SharePayloadDescriptor(lastPathSegment = "a.ics", contentType = "text/calendar"),
                    SharePayloadDescriptor(lastPathSegment = "b.xlsx", contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                )
            )
        )
    }

    @Test
    fun `merge pending image drafts concatenates texts and preserves warnings`() {
        val merged = mergePendingImageDrafts(
            listOf(
                PendingImportDraft(
                    sourceLabel = "截图1",
                    statusMessage = "OCR 已识别出文本，请确认或修改后再导入。",
                    stagedImportText = "周一 1-2节 高等数学",
                    rawRecognizedText = "raw-1",
                    displayRecognizedText = "display-1",
                    parsedText = "parsed-1",
                    warnings = listOf("warning-a")
                ),
                PendingImportDraft(
                    sourceLabel = "截图2",
                    statusMessage = "OCR 已识别出文本，但还不能自动整理成完整课表。请先修改预览文本后再导入。",
                    stagedImportText = "周二 3-4节 大学英语",
                    rawRecognizedText = "raw-2",
                    displayRecognizedText = "display-2",
                    parsedText = "",
                    warnings = listOf("warning-a", "warning-b")
                )
            )
        )

        assertEquals("分享的课表图片(2张)", merged.sourceLabel)
        assertEquals("parsed-1\n周二 3-4节 大学英语", merged.parsedText)
        assertEquals("周一 1-2节 高等数学\n周二 3-4节 大学英语", merged.stagedImportText)
        assertEquals("raw-1\n\nraw-2", merged.rawRecognizedText)
        assertEquals("display-1\n\ndisplay-2", merged.displayRecognizedText)
        assertEquals(listOf("warning-a", "warning-b"), merged.warnings)
        assertTrue(merged.statusMessage.contains("请确认或修改后再导入"))
    }
}
