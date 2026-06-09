package com.miaom.schedule.data.transfer

object BinarySpreadsheetImportSupport {
    private val ole2Magic = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte())

    fun isLegacyXls(bytes: ByteArray, contentType: String? = null): Boolean {
        val normalizedType = contentType.orEmpty().lowercase()
        if (bytes.size >= ole2Magic.size) {
            return ole2Magic.indices.all { index -> bytes[index] == ole2Magic[index] }
        }

        if (normalizedType.contains("application/vnd.ms-excel")) {
            return false
        }
        return false
    }
}
