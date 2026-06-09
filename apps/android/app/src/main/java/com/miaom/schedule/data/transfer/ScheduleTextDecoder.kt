package com.miaom.schedule.data.transfer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object ScheduleTextDecoder {
    fun decode(bytes: ByteArray, contentType: String? = null): String {
        if (bytes.isEmpty()) return ""

        detectBomCharset(bytes)?.let { charset ->
            return bytes.toString(charset).trimStart('\uFEFF')
        }

        detectUtf16Charset(bytes)?.let { charset ->
            return bytes.toString(charset).trimStart('\uFEFF')
        }

        extractCharsetFromContentType(contentType)?.let { charset ->
            return bytes.toString(charset).trimStart('\uFEFF')
        }

        decodeStrictUtf8OrNull(bytes)?.let { return it.trimStart('\uFEFF') }

        return bytes.toString(Charset.forName("GB18030")).trimStart('\uFEFF')
    }

    private fun detectBomCharset(bytes: ByteArray): Charset? {
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun detectUtf16Charset(bytes: ByteArray): Charset? {
        if (bytes.size < 4 || bytes.size % 2 != 0) return null

        var evenZeroCount = 0
        var oddZeroCount = 0
        val sampleSize = minOf(bytes.size, 64)
        for (index in 0 until sampleSize) {
            if (bytes[index] != 0.toByte()) continue
            if (index % 2 == 0) {
                evenZeroCount += 1
            } else {
                oddZeroCount += 1
            }
        }

        return when {
            oddZeroCount >= 2 && evenZeroCount == 0 -> Charsets.UTF_16LE
            evenZeroCount >= 2 && oddZeroCount == 0 -> Charsets.UTF_16BE
            oddZeroCount >= 3 && oddZeroCount >= evenZeroCount * 3 -> Charsets.UTF_16LE
            evenZeroCount >= 3 && evenZeroCount >= oddZeroCount * 3 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun extractCharsetFromContentType(contentType: String?): Charset? {
        val raw = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { Charset.forName(raw) }.getOrNull()
    }

    private fun decodeStrictUtf8OrNull(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
