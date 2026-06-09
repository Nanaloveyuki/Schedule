package com.miaom.schedule.data.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinarySpreadsheetImportSupportTest {
    @Test
    fun `do not treat mime only excel payload as legacy xls without ole2 signature`() {
        val htmlBytes = "<table><tr><td>周一</td><td>1-2节</td></tr></table>".encodeToByteArray()

        assertFalse(BinarySpreadsheetImportSupport.isLegacyXls(htmlBytes, "application/vnd.ms-excel"))
    }

    @Test
    fun `recognize legacy xls by ole2 signature`() {
        val bytes = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
            0x00.toByte(), 0x01.toByte()
        )

        assertTrue(BinarySpreadsheetImportSupport.isLegacyXls(bytes, null))
    }

    @Test
    fun `do not misclassify xlsx zip bytes as legacy xls`() {
        val bytes = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)

        assertFalse(BinarySpreadsheetImportSupport.isLegacyXls(bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }
}
