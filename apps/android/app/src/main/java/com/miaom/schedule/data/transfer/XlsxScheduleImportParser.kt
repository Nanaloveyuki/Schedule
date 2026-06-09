package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TreeMap
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object XlsxScheduleImportParser {
    private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private data class SheetReference(
        val name: String,
        val path: String
    )

    private data class CellMerge(
        val startRow: Int,
        val endRow: Int,
        val startColumn: Int,
        val endColumn: Int
    )

    private data class ParsedSheet(
        val name: String,
        val cells: Map<Pair<Int, Int>, String>,
        val merges: List<CellMerge>,
        val maxRow: Int,
        val maxColumn: Int
    )

    internal fun looksLikeXlsx(bytes: ByteArray, contentType: String? = null): Boolean {
        if (contentType.orEmpty().contains(XLSX_CONTENT_TYPE, ignoreCase = true)) return true
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
        val entries = unzipEntries(bytes)
        return entries.containsKey("xl/workbook.xml") && entries.keys.any { it.startsWith("xl/worksheets/") }
    }

    fun parse(bytes: ByteArray, currentDocument: ScheduleDocument): CommonScheduleImportResult {
        require(looksLikeXlsx(bytes)) { "这不是可识别的 Excel 课表文件。" }
        val entries = unzipEntries(bytes)
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetReferences = parseSheetReferences(entries)
        val warnings = linkedSetOf<String>()

        sheetReferences.forEach { sheet ->
            val parsedSheet = parseSheet(entries[sheet.path], sheet.name, sharedStrings) ?: return@forEach
            if (parsedSheet.cells.values.none { it.isNotBlank() }) return@forEach

            val htmlTable = buildHtmlTable(parsedSheet)
            runCatching { HtmlScheduleImportParser.parse(htmlTable, currentDocument) }
                .onSuccess { result ->
                    val mergedWarnings = linkedSetOf<String>()
                    mergedWarnings += result.warnings
                    mergedWarnings += warnings
                    if (sheetReferences.size > 1) {
                        mergedWarnings += "已从工作表“${sheet.name}”导入课表。"
                    }
                    return result.copy(
                        detectedFormat = "Excel 课表",
                        warnings = mergedWarnings.toList()
                    )
                }
                .onFailure {
                    warnings += "工作表“${sheet.name}”未识别为课表，已尝试下一张工作表。"
                }
        }

        throw IllegalArgumentException("Excel 文件中没有找到可识别的课表工作表。")
    }

    private fun unzipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val document = parseXml(bytes)
        return document.getElementsByTagNameNS("*", "si")
            .asElementList()
            .map { sharedString ->
                sharedString.getElementsByTagNameNS("*", "t")
                    .asElementList()
                    .joinToString(separator = "") { it.textContent }
            }
    }

    private fun parseSheetReferences(entries: Map<String, ByteArray>): List<SheetReference> {
        val workbookBytes = entries["xl/workbook.xml"] ?: return fallbackSheetReferences(entries)
        val workbookRelsBytes = entries["xl/_rels/workbook.xml.rels"] ?: return fallbackSheetReferences(entries)
        val relsDocument = parseXml(workbookRelsBytes)
        val relationById = relsDocument.getElementsByTagNameNS("*", "Relationship")
            .asElementList()
            .associate { relation ->
                relation.getAttribute("Id") to relation.getAttribute("Target")
            }

        val workbookDocument = parseXml(workbookBytes)
        val references = workbookDocument.getElementsByTagNameNS("*", "sheet")
            .asElementList()
            .mapNotNull { sheet ->
                val name = sheet.getAttribute("name").ifBlank { "课表" }
                val relationId = sheet.getAttributeNS(REL_NS, "id").ifBlank { sheet.getAttribute("r:id") }
                val target = relationById[relationId] ?: return@mapNotNull null
                SheetReference(name = name, path = normalizeWorkbookTarget(target))
            }
            .filter { entries.containsKey(it.path) }
        return references.ifEmpty { fallbackSheetReferences(entries) }
    }

    private fun fallbackSheetReferences(entries: Map<String, ByteArray>): List<SheetReference> {
        return entries.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
            .mapIndexed { index, path ->
                SheetReference(name = "工作表${index + 1}", path = path)
            }
    }

    private fun normalizeWorkbookTarget(target: String): String {
        val normalized = target.removePrefix("/")
        return if (normalized.startsWith("xl/")) normalized else "xl/$normalized"
    }

    private fun parseSheet(bytes: ByteArray?, name: String, sharedStrings: List<String>): ParsedSheet? {
        if (bytes == null) return null
        val document = parseXml(bytes)
        val cells = linkedMapOf<Pair<Int, Int>, String>()
        var maxRow = -1
        var maxColumn = -1

        document.getElementsByTagNameNS("*", "row")
            .asElementList()
            .forEachIndexed { fallbackRowIndex, row ->
                row.childElements("c").forEachIndexed { fallbackColumnIndex, cell ->
                    val reference = cell.getAttribute("r")
                    val rowIndex = parseRowIndex(reference)?.minus(1) ?: fallbackRowIndex
                    val columnIndex = parseColumnIndex(reference) ?: fallbackColumnIndex
                    val value = parseCellValue(cell, sharedStrings)
                    if (value.isBlank()) return@forEachIndexed
                    cells[rowIndex to columnIndex] = value
                    maxRow = maxOf(maxRow, rowIndex)
                    maxColumn = maxOf(maxColumn, columnIndex)
                }
            }

        val merges = document.getElementsByTagNameNS("*", "mergeCell")
            .asElementList()
            .mapNotNull { merge -> parseMergeRef(merge.getAttribute("ref")) }

        merges.forEach { merge ->
            maxRow = maxOf(maxRow, merge.endRow)
            maxColumn = maxOf(maxColumn, merge.endColumn)
        }

        return ParsedSheet(
            name = name,
            cells = cells,
            merges = merges,
            maxRow = maxRow,
            maxColumn = maxColumn
        )
    }

    private fun parseCellValue(cell: Element, sharedStrings: List<String>): String {
        return when (cell.getAttribute("t")) {
            "s" -> cell.childElements("v")
                .firstOrNull()
                ?.textContent
                ?.trim()
                ?.toIntOrNull()
                ?.let(sharedStrings::getOrNull)
                .orEmpty()
            "inlineStr" -> cell.getElementsByTagNameNS("*", "t")
                .asElementList()
                .joinToString(separator = "") { it.textContent }
            else -> cell.childElements("v").firstOrNull()?.textContent.orEmpty()
        }.trim()
    }

    private fun parseMergeRef(ref: String): CellMerge? {
        if (ref.isBlank()) return null
        val parts = ref.split(':')
        val start = parts.firstOrNull().orEmpty()
        val end = parts.getOrElse(1) { start }
        val startRow = parseRowIndex(start)?.minus(1) ?: return null
        val endRow = parseRowIndex(end)?.minus(1) ?: return null
        val startColumn = parseColumnIndex(start) ?: return null
        val endColumn = parseColumnIndex(end) ?: return null
        return CellMerge(
            startRow = minOf(startRow, endRow),
            endRow = maxOf(startRow, endRow),
            startColumn = minOf(startColumn, endColumn),
            endColumn = maxOf(startColumn, endColumn)
        )
    }

    private fun parseColumnIndex(reference: String): Int? {
        val letters = reference.takeWhile { it.isLetter() }
        if (letters.isBlank()) return null
        var value = 0
        letters.uppercase().forEach { char ->
            value = value * 26 + (char.code - 'A'.code + 1)
        }
        return value - 1
    }

    private fun parseRowIndex(reference: String): Int? {
        return reference.dropWhile { !it.isDigit() }.toIntOrNull()
    }

    private fun buildHtmlTable(sheet: ParsedSheet): String {
        val mergeByStart = sheet.merges.associateBy { it.startRow to it.startColumn }
        val coveredCells = buildSet {
            sheet.merges.forEach { merge ->
                for (row in merge.startRow..merge.endRow) {
                    for (column in merge.startColumn..merge.endColumn) {
                        if (row != merge.startRow || column != merge.startColumn) {
                            add(row to column)
                        }
                    }
                }
            }
        }

        val builder = StringBuilder()
        builder.append("<table>")
        for (rowIndex in 0..sheet.maxRow) {
            builder.append("<tr>")
            for (columnIndex in 0..sheet.maxColumn) {
                val key = rowIndex to columnIndex
                if (key in coveredCells) continue
                val merge = mergeByStart[key]
                val tag = if (rowIndex == 0) "th" else "td"
                builder.append('<').append(tag)
                merge?.let {
                    val rowspan = it.endRow - it.startRow + 1
                    val colspan = it.endColumn - it.startColumn + 1
                    if (rowspan > 1) builder.append(" rowspan=\"").append(rowspan).append('"')
                    if (colspan > 1) builder.append(" colspan=\"").append(colspan).append('"')
                }
                builder.append('>')
                builder.append(escapeHtml(sheet.cells[key].orEmpty()).replace("\n", "<br/>"))
                builder.append("</").append(tag).append('>')
            }
            builder.append("</tr>")
        }
        builder.append("</table>")
        return builder.toString()
    }

    private fun escapeHtml(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        else -> char
                    }
                )
            }
        }
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun org.w3c.dom.NodeList.asElementList(): List<Element> = buildList {
        for (index in 0 until length) {
            val node = item(index)
            if (node is Element) add(node)
        }
    }

    private fun Element.childElements(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.localName == localName) {
                result += node
            }
        }
        return result
    }
}
