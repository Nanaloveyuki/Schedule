package com.miaom.schedule.data.transfer

import android.content.Context
import android.net.Uri
import com.miaom.schedule.domain.model.BackgroundConfig
import com.miaom.schedule.domain.model.FontConfig
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.normalized
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SchedulePackResource(
    val type: String,
    val originalReference: String,
    val entryPath: String,
    val included: Boolean,
    val displayName: String
)

data class SchedulePackExport(
    val fileBytes: ByteArray,
    val clipboardText: String,
    val manifestJson: String,
    val includedResources: List<SchedulePackResource>
)

data class SchedulePackImportResult(
    val document: ScheduleDocument,
    val manifestVersion: Int,
    val importedResources: List<SchedulePackResource>
)

object SchedulePackCodec {
    private const val MAGIC = "SCHEDULEPACK:1:"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DOCUMENT_ENTRY = "document.json"

    fun encode(context: Context, document: ScheduleDocument): SchedulePackExport {
        val resources = resolvePackResources(document)
        val documentJson = documentToJson(document).toString(2)
        val manifestJson = buildManifest(document, resources).toString(2)
        val fileBytes = buildZip(context, documentJson, manifestJson, resources)
        val clipboardText = MAGIC + compressToBase64(documentJson)
        return SchedulePackExport(
            fileBytes = fileBytes,
            clipboardText = clipboardText,
            manifestJson = manifestJson,
            includedResources = resources
        )
    }

    fun decodeClipboardPayload(payload: String): SchedulePackImportResult {
        require(payload.startsWith(MAGIC)) { "不是可识别的课表分享内容。" }
        val json = inflateFromBase64(payload.removePrefix(MAGIC))
        val document = parseDocumentJson(json)
        return SchedulePackImportResult(
            document = document,
            manifestVersion = 1,
            importedResources = emptyList()
        )
    }

    fun decodeFilePack(inputStream: InputStream): SchedulePackImportResult {
        ZipInputStream(inputStream.buffered()).use { zip ->
            var documentJson: String? = null
            var manifestJson: String? = null
            val resourceEntries = mutableListOf<SchedulePackResource>()

            generateSequence { zip.nextEntry }.forEach { entry ->
                val bytes = zip.readBytes()
                when (entry.name) {
                    DOCUMENT_ENTRY -> documentJson = bytes.toString(Charsets.UTF_8)
                    MANIFEST_ENTRY -> manifestJson = bytes.toString(Charsets.UTF_8)
                    else -> if (!entry.isDirectory && entry.name.startsWith("resources/")) {
                        resourceEntries += SchedulePackResource(
                            type = "resource",
                            originalReference = entry.name,
                            entryPath = entry.name,
                            included = true,
                            displayName = entry.name.substringAfterLast('/')
                        )
                    }
                }
            }

            val parsedDocument = parseDocumentJson(requireNotNull(documentJson) { "导入包缺少课表主文档。" })
            val manifestVersion = manifestJson
                ?.let { JSONObject(it).optInt("manifestVersion", 1) }
                ?: 1
            return SchedulePackImportResult(
                document = parsedDocument,
                manifestVersion = manifestVersion,
                importedResources = resourceEntries
            )
        }
    }

    private fun buildZip(
        context: Context,
        documentJson: String,
        manifestJson: String,
        resources: List<SchedulePackResource>
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DOCUMENT_ENTRY))
            zip.write(documentJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            resources.filter { it.included }.forEach { resource ->
                val source = runCatching {
                    when {
                        resource.originalReference.startsWith("content://") -> {
                            context.contentResolver.openInputStream(Uri.parse(resource.originalReference))
                        }
                        resource.originalReference.isNotBlank() -> {
                            java.io.File(resource.originalReference).takeIf { it.exists() }?.inputStream()
                        }
                        else -> null
                    }
                }.getOrNull()
                source?.use { input ->
                    zip.putNextEntry(ZipEntry(resource.entryPath))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }

    private fun resolvePackResources(document: ScheduleDocument): List<SchedulePackResource> {
        val items = mutableListOf<SchedulePackResource>()
        appendResource(
            items = items,
            reference = document.themeConfig.background.imageReference,
            type = "background-image",
            entryPath = "resources/background/${document.themeConfig.background.imageReference.substringAfterLast('/')}",
            displayName = "背景图片"
        )
        appendResource(
            items = items,
            reference = document.themeConfig.font.customFontPath,
            type = "font-file",
            entryPath = "resources/font/${document.themeConfig.font.customFontPath.substringAfterLast('/')}",
            displayName = document.themeConfig.font.customFontLabel.ifBlank { "自定义字体" }
        )
        return items
    }

    private fun appendResource(
        items: MutableList<SchedulePackResource>,
        reference: String,
        type: String,
        entryPath: String,
        displayName: String
    ) {
        if (reference.isBlank()) return
        val included = reference.startsWith("content://") || java.io.File(reference).exists()
        items += SchedulePackResource(
            type = type,
            originalReference = reference,
            entryPath = entryPath,
            included = included,
            displayName = displayName
        )
    }

    private fun buildManifest(document: ScheduleDocument, resources: List<SchedulePackResource>): JSONObject {
        return JSONObject()
            .put("manifestVersion", 1)
            .put("appFormat", "schedulepack")
            .put("documentVersion", document.version)
            .put("updatedAtEpochMillis", document.updatedAtEpochMillis)
            .put("entries", JSONObject().put("document", DOCUMENT_ENTRY))
            .put("resources", JSONArray().also { array ->
                resources.forEach { resource ->
                    array.put(
                        JSONObject()
                            .put("type", resource.type)
                            .put("originalReference", resource.originalReference)
                            .put("entryPath", resource.entryPath)
                            .put("included", resource.included)
                            .put("displayName", resource.displayName)
                    )
                }
            })
    }

    private fun compressToBase64(text: String): String {
        val bytes = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { deflater ->
                deflater.write(text.toByteArray(Charsets.UTF_8))
            }
            output.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun inflateFromBase64(text: String): String {
        val decoded = Base64.getUrlDecoder().decode(text)
        val inflated = InflaterInputStream(ByteArrayInputStream(decoded)).use { it.readBytes() }
        return inflated.toString(Charsets.UTF_8)
    }

    private fun documentToJson(document: ScheduleDocument): JSONObject {
        val repository = JSONObject()
        repository.put("version", document.version)
        repository.put("weekConfig", JSONObject()
            .put("firstDayOfWeek", document.weekConfig.firstDayOfWeek)
            .put("teachingDays", JSONArray(document.weekConfig.teachingDays))
            .put("week1MondayDate", document.weekConfig.week1MondayDate)
        )
        repository.put("timeSlotTemplates", JSONArray().also { array ->
            document.timeSlotTemplates.forEach { slot ->
                array.put(
                    JSONObject()
                        .put("id", slot.id)
                        .put("label", slot.label)
                        .put("startTime", slot.startTime)
                        .put("endTime", slot.endTime)
                        .put("order", slot.order)
                        .put("enabled", slot.enabled)
                )
            }
        })
        repository.put("courseEntries", JSONArray().also { array ->
            document.courseEntries.forEach { course ->
                array.put(
                    JSONObject()
                        .put("id", course.id)
                        .put("name", course.name)
                        .put("teacher", course.teacher)
                        .put("location", course.location)
                        .put("dayOfWeek", course.dayOfWeek)
                        .put("timeSlotTemplateId", course.timeSlotTemplateId)
                        .put("weekParity", course.weekParity.name)
                        .put("timeOverride", course.timeOverride?.let {
                            JSONObject()
                                .put("startTime", it.startTime)
                                .put("endTime", it.endTime)
                        })
                        .put("colorStyle", JSONObject()
                            .put("useThemeDefaults", course.colorStyle.useThemeDefaults)
                            .put("backgroundColorArgb", course.colorStyle.backgroundColorArgb)
                            .put("textColorArgb", course.colorStyle.textColorArgb)
                            .put("borderColorArgb", course.colorStyle.borderColorArgb)
                        )
                )
            }
        })
        repository.put("courseTemplatePresets", JSONArray().also { array ->
            document.courseTemplatePresets.forEach { preset ->
                array.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("name", preset.name)
                        .put("note", preset.note)
                        .put("snapshot", JSONObject()
                            .put("courseName", preset.snapshot.courseName)
                            .put("teacher", preset.snapshot.teacher)
                            .put("location", preset.snapshot.location)
                            .put("preferredTimeSlotTemplateId", preset.snapshot.preferredTimeSlotTemplateId)
                            .put("preferredTimeSlotLabel", preset.snapshot.preferredTimeSlotLabel)
                            .put("weekParity", preset.snapshot.weekParity.name)
                            .put("timeOverride", preset.snapshot.timeOverride?.let {
                                JSONObject()
                                    .put("startTime", it.startTime)
                                    .put("endTime", it.endTime)
                            })
                            .put("colorStyle", JSONObject()
                                .put("useThemeDefaults", preset.snapshot.colorStyle.useThemeDefaults)
                                .put("backgroundColorArgb", preset.snapshot.colorStyle.backgroundColorArgb)
                                .put("textColorArgb", preset.snapshot.colorStyle.textColorArgb)
                                .put("borderColorArgb", preset.snapshot.colorStyle.borderColorArgb)
                            )
                        )
                )
            }
        })
        repository.put("reminderRules", JSONArray().also { array ->
            document.reminderRules.forEach { rule ->
                array.put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("courseEntryId", rule.courseEntryId)
                        .put("minutesBefore", rule.minutesBefore)
                        .put("channel", rule.channel.name)
                        .put("exact", rule.exact)
                        .put("enabled", rule.enabled)
                )
            }
        })
        repository.put("themeConfig", JSONObject()
            .put("themeMode", document.themeConfig.themeMode)
            .put("useDynamicColor", document.themeConfig.useDynamicColor)
            .put("accentColorHex", document.themeConfig.accentColorHex)
            .put("selectedBuiltInPresetId", document.themeConfig.selectedBuiltInPresetId)
            .put("colorTokens", JSONObject()
                .put("primaryHex", document.themeConfig.colorTokens.primaryHex)
                .put("secondaryHex", document.themeConfig.colorTokens.secondaryHex)
                .put("tertiaryHex", document.themeConfig.colorTokens.tertiaryHex)
                .put("backgroundHex", document.themeConfig.colorTokens.backgroundHex)
                .put("surfaceHex", document.themeConfig.colorTokens.surfaceHex)
                .put("surfaceVariantHex", document.themeConfig.colorTokens.surfaceVariantHex)
                .put("onSurfaceHex", document.themeConfig.colorTokens.onSurfaceHex)
                .put("outlineHex", document.themeConfig.colorTokens.outlineHex)
            )
            .put("background", JSONObject()
                .put("mode", document.themeConfig.background.mode)
                .put("solidColorHex", document.themeConfig.background.solidColorHex)
                .put("imageReference", document.themeConfig.background.imageReference)
                .put("blurRadiusDp", document.themeConfig.background.blurRadiusDp)
            )
            .put("font", JSONObject()
                .put("builtInFontId", document.themeConfig.font.builtInFontId)
                .put("customFontLabel", document.themeConfig.font.customFontLabel)
                .put("customFontPath", document.themeConfig.font.customFontPath)
                .put("preferCustomFont", document.themeConfig.font.preferCustomFont)
            )
            .put("userPresets", JSONArray().also { array ->
                document.themeConfig.userPresets.forEach { preset ->
                    array.put(
                        JSONObject()
                            .put("id", preset.id)
                            .put("name", preset.name)
                            .put("group", preset.group)
                            .put("note", preset.note)
                            .put("snapshot", presetSnapshotToJson(preset.snapshot))
                    )
                }
            })
            .put("gridSizing", JSONObject()
                .put("gridMinCellWidthDp", document.themeConfig.gridSizing.gridMinCellWidthDp)
                .put("gridMaxCellWidthDp", document.themeConfig.gridSizing.gridMaxCellWidthDp)
                .put("gridMinCellHeightDp", document.themeConfig.gridSizing.gridMinCellHeightDp)
                .put("gridMaxCellHeightDp", document.themeConfig.gridSizing.gridMaxCellHeightDp)
                .put("compactMode", document.themeConfig.gridSizing.compactMode)
                .put("adaptiveSizing", document.themeConfig.gridSizing.adaptiveSizing)
            )
        )
        repository.put("transferConfig", JSONObject()
            .put("defaultExportMethod", document.transferConfig.defaultExportMethod)
            .put("rememberDefaultExportMethod", document.transferConfig.rememberDefaultExportMethod)
        )
        repository.put("updatedAtEpochMillis", document.updatedAtEpochMillis)
        return repository
    }

    private fun parseDocumentJson(raw: String): ScheduleDocument {
        val root = JSONObject(raw)
        fun parseBackground(json: JSONObject?) = BackgroundConfig(
            mode = json?.optString("mode", BackgroundConfig().mode) ?: BackgroundConfig().mode,
            solidColorHex = json?.optString("solidColorHex", BackgroundConfig().solidColorHex) ?: BackgroundConfig().solidColorHex,
            imageReference = json?.optString("imageReference").orEmpty(),
            blurRadiusDp = json?.optDouble("blurRadiusDp", 18.0)?.toFloat() ?: 18f
        )
        fun parseFont(json: JSONObject?) = FontConfig(
            builtInFontId = json?.optString("builtInFontId", FontConfig().builtInFontId) ?: FontConfig().builtInFontId,
            customFontLabel = json?.optString("customFontLabel").orEmpty(),
            customFontPath = json?.optString("customFontPath").orEmpty(),
            preferCustomFont = json?.optBoolean("preferCustomFont", false) ?: false
        )
        fun parseGrid(json: JSONObject?) = com.miaom.schedule.domain.model.GridSizingConfig(
            gridMinCellWidthDp = json?.optDouble("gridMinCellWidthDp", 112.0)?.toFloat() ?: 112f,
            gridMaxCellWidthDp = json?.optDouble("gridMaxCellWidthDp", 168.0)?.toFloat() ?: 168f,
            gridMinCellHeightDp = json?.optDouble("gridMinCellHeightDp", 108.0)?.toFloat() ?: 108f,
            gridMaxCellHeightDp = json?.optDouble("gridMaxCellHeightDp", 156.0)?.toFloat() ?: 156f,
            compactMode = json?.optBoolean("compactMode", false) ?: false,
            adaptiveSizing = json?.optBoolean("adaptiveSizing", true) ?: true
        )
        fun parseColorTokens(json: JSONObject?) = com.miaom.schedule.domain.model.ThemeColorTokens(
            primaryHex = json?.optString("primaryHex", "#1D7A85") ?: "#1D7A85",
            secondaryHex = json?.optString("secondaryHex", "#58708A") ?: "#58708A",
            tertiaryHex = json?.optString("tertiaryHex", "#8A6E9E") ?: "#8A6E9E",
            backgroundHex = json?.optString("backgroundHex", "#F7F4EC") ?: "#F7F4EC",
            surfaceHex = json?.optString("surfaceHex", "#FFFDF8") ?: "#FFFDF8",
            surfaceVariantHex = json?.optString("surfaceVariantHex", "#DFE8EA") ?: "#DFE8EA",
            onSurfaceHex = json?.optString("onSurfaceHex", "#12202F") ?: "#12202F",
            outlineHex = json?.optString("outlineHex", "#7B8A90") ?: "#7B8A90"
        )
        fun parsePresetSnapshot(json: JSONObject?) = com.miaom.schedule.domain.model.ThemePresetSnapshot(
            themeMode = json?.optString("themeMode", "system") ?: "system",
            useDynamicColor = json?.optBoolean("useDynamicColor", true) ?: true,
            accentColorHex = json?.optString("accentColorHex").orEmpty(),
            colorTokens = parseColorTokens(json?.optJSONObject("colorTokens")),
            background = parseBackground(json?.optJSONObject("background")),
            font = parseFont(json?.optJSONObject("font")),
            gridSizing = parseGrid(json?.optJSONObject("gridSizing"))
        )
        fun parseCourseTemplateSnapshot(json: JSONObject?) = com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot(
            courseName = json?.optString("courseName").orEmpty(),
            teacher = json?.optString("teacher").orEmpty(),
            location = json?.optString("location").orEmpty(),
            preferredTimeSlotTemplateId = json?.optString("preferredTimeSlotTemplateId").orEmpty(),
            preferredTimeSlotLabel = json?.optString("preferredTimeSlotLabel").orEmpty(),
            weekParity = com.miaom.schedule.domain.model.WeekParity.valueOf(
                json?.optString("weekParity", "Every") ?: "Every"
            ),
            timeOverride = json?.optJSONObject("timeOverride")?.let {
                com.miaom.schedule.domain.model.CourseTimeOverride(
                    startTime = it.optString("startTime"),
                    endTime = it.optString("endTime")
                )
            },
            colorStyle = json?.optJSONObject("colorStyle")?.let { color ->
                com.miaom.schedule.domain.model.CourseColorStyle(
                    useThemeDefaults = color.optBoolean("useThemeDefaults", true),
                    backgroundColorArgb = color.optInt("backgroundColorArgb", 0xFFDBEAFE.toInt()),
                    textColorArgb = color.optInt("textColorArgb", 0xFF102A43.toInt()),
                    borderColorArgb = color.optInt("borderColorArgb", 0xFF6B8BB3.toInt())
                )
            } ?: com.miaom.schedule.domain.model.CourseColorStyle()
        )
        val themeJson = root.optJSONObject("themeConfig")
        return ScheduleDocument(
            version = root.optInt("version", ScheduleDocument.CURRENT_VERSION),
            weekConfig = com.miaom.schedule.domain.model.WeekConfig(
                firstDayOfWeek = root.optJSONObject("weekConfig")?.optInt("firstDayOfWeek", 1) ?: 1,
                teachingDays = buildList {
                    val array = root.optJSONObject("weekConfig")?.optJSONArray("teachingDays")
                    if (array != null) for (index in 0 until array.length()) add(array.optInt(index, index + 1))
                }.ifEmpty { listOf(1, 2, 3, 4, 5) },
                week1MondayDate = root.optJSONObject("weekConfig")?.optString("week1MondayDate").orEmpty()
                    .ifBlank { com.miaom.schedule.domain.model.WeekConfig().week1MondayDate }
            ),
            timeSlotTemplates = buildList {
                val array = root.optJSONArray("timeSlotTemplates")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            com.miaom.schedule.domain.model.TimeSlotTemplate(
                                id = item.getString("id"),
                                label = item.getString("label"),
                                startTime = item.getString("startTime"),
                                endTime = item.getString("endTime"),
                                order = item.optInt("order", index),
                                enabled = item.optBoolean("enabled", true)
                            )
                        )
                    }
                }
            },
            courseEntries = buildList {
                val array = root.optJSONArray("courseEntries")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val color = item.optJSONObject("colorStyle")
                        add(
                            com.miaom.schedule.domain.model.CourseEntry(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                teacher = item.optString("teacher"),
                                location = item.optString("location"),
                                dayOfWeek = item.optInt("dayOfWeek", 1),
                                timeSlotTemplateId = item.optString("timeSlotTemplateId", item.optString("slotId")),
                                weekParity = com.miaom.schedule.domain.model.WeekParity.valueOf(item.optString("weekParity", "Every")),
                                timeOverride = item.optJSONObject("timeOverride")?.let {
                                    com.miaom.schedule.domain.model.CourseTimeOverride(
                                        startTime = it.optString("startTime"),
                                        endTime = it.optString("endTime")
                                    )
                                },
                                colorStyle = com.miaom.schedule.domain.model.CourseColorStyle(
                                    useThemeDefaults = color?.optBoolean("useThemeDefaults", true) ?: true,
                                    backgroundColorArgb = color?.optInt("backgroundColorArgb", 0xFFDBEAFE.toInt()) ?: 0xFFDBEAFE.toInt(),
                                    textColorArgb = color?.optInt("textColorArgb", 0xFF102A43.toInt()) ?: 0xFF102A43.toInt(),
                                    borderColorArgb = color?.optInt("borderColorArgb", 0xFF6B8BB3.toInt()) ?: 0xFF6B8BB3.toInt()
                                )
                            )
                        )
                    }
                }
            },
            courseTemplatePresets = buildList {
                val array = root.optJSONArray("courseTemplatePresets")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            com.miaom.schedule.domain.model.UserCourseTemplatePreset(
                                id = item.getString("id"),
                                name = item.optString("name", "课程模板"),
                                note = item.optString("note"),
                                snapshot = parseCourseTemplateSnapshot(item.optJSONObject("snapshot"))
                            )
                        )
                    }
                }
            },
            reminderRules = buildList {
                val array = root.optJSONArray("reminderRules")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            com.miaom.schedule.domain.model.ReminderRule(
                                id = item.getString("id"),
                                courseEntryId = item.optString("courseEntryId", item.optString("courseId")),
                                minutesBefore = item.optInt("minutesBefore", 10),
                                channel = com.miaom.schedule.domain.model.ReminderChannel.valueOf(
                                    item.optString("channel", com.miaom.schedule.domain.model.ReminderChannel.InAppNotification.name)
                                ),
                                exact = item.optBoolean("exact", false),
                                enabled = item.optBoolean("enabled", true)
                            )
                        )
                    }
                }
            },
            themeConfig = com.miaom.schedule.domain.model.ThemeConfig(
                themeMode = themeJson?.optString("themeMode", "system") ?: "system",
                useDynamicColor = themeJson?.optBoolean("useDynamicColor", true) ?: true,
                accentColorHex = themeJson?.optString("accentColorHex").orEmpty(),
                colorTokens = parseColorTokens(themeJson?.optJSONObject("colorTokens")),
                background = parseBackground(themeJson?.optJSONObject("background")),
                font = parseFont(themeJson?.optJSONObject("font")),
                selectedBuiltInPresetId = themeJson?.optString("selectedBuiltInPresetId", "campus-breeze") ?: "campus-breeze",
                userPresets = buildList {
                    val array = themeJson?.optJSONArray("userPresets")
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            add(
                                com.miaom.schedule.domain.model.UserThemePreset(
                                    id = item.getString("id"),
                                    name = item.optString("name", "我的预设"),
                                    group = item.optString("group", "用户预设"),
                                    note = item.optString("note"),
                                    snapshot = parsePresetSnapshot(item.optJSONObject("snapshot"))
                                )
                            )
                        }
                    }
                },
                gridSizing = parseGrid(themeJson?.optJSONObject("gridSizing"))
            ),
            transferConfig = com.miaom.schedule.domain.model.TransferConfig(
                defaultExportMethod = root.optJSONObject("transferConfig")?.optString("defaultExportMethod", "FilePack") ?: "FilePack",
                rememberDefaultExportMethod = root.optJSONObject("transferConfig")?.optBoolean("rememberDefaultExportMethod", true) ?: true
            ),
            updatedAtEpochMillis = root.optLong("updatedAtEpochMillis", 0L)
        ).normalized()
    }

    private fun presetSnapshotToJson(snapshot: com.miaom.schedule.domain.model.ThemePresetSnapshot): JSONObject {
        return JSONObject()
            .put("themeMode", snapshot.themeMode)
            .put("useDynamicColor", snapshot.useDynamicColor)
            .put("accentColorHex", snapshot.accentColorHex)
            .put("colorTokens", JSONObject()
                .put("primaryHex", snapshot.colorTokens.primaryHex)
                .put("secondaryHex", snapshot.colorTokens.secondaryHex)
                .put("tertiaryHex", snapshot.colorTokens.tertiaryHex)
                .put("backgroundHex", snapshot.colorTokens.backgroundHex)
                .put("surfaceHex", snapshot.colorTokens.surfaceHex)
                .put("surfaceVariantHex", snapshot.colorTokens.surfaceVariantHex)
                .put("onSurfaceHex", snapshot.colorTokens.onSurfaceHex)
                .put("outlineHex", snapshot.colorTokens.outlineHex)
            )
            .put("background", JSONObject()
                .put("mode", snapshot.background.mode)
                .put("solidColorHex", snapshot.background.solidColorHex)
                .put("imageReference", snapshot.background.imageReference)
                .put("blurRadiusDp", snapshot.background.blurRadiusDp)
            )
            .put("font", JSONObject()
                .put("builtInFontId", snapshot.font.builtInFontId)
                .put("customFontLabel", snapshot.font.customFontLabel)
                .put("customFontPath", snapshot.font.customFontPath)
                .put("preferCustomFont", snapshot.font.preferCustomFont)
            )
            .put("gridSizing", JSONObject()
                .put("gridMinCellWidthDp", snapshot.gridSizing.gridMinCellWidthDp)
                .put("gridMaxCellWidthDp", snapshot.gridSizing.gridMaxCellWidthDp)
                .put("gridMinCellHeightDp", snapshot.gridSizing.gridMinCellHeightDp)
                .put("gridMaxCellHeightDp", snapshot.gridSizing.gridMaxCellHeightDp)
                .put("compactMode", snapshot.gridSizing.compactMode)
                .put("adaptiveSizing", snapshot.gridSizing.adaptiveSizing)
            )
    }

    private fun parseCourseTemplateSnapshot(jsonObject: JSONObject?): com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot {
        if (jsonObject == null) return com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot()
        return com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot(
            courseName = jsonObject.optString("courseName"),
            teacher = jsonObject.optString("teacher"),
            location = jsonObject.optString("location"),
            preferredTimeSlotTemplateId = jsonObject.optString("preferredTimeSlotTemplateId"),
            preferredTimeSlotLabel = jsonObject.optString("preferredTimeSlotLabel"),
            weekParity = com.miaom.schedule.domain.model.WeekParity.valueOf(jsonObject.optString("weekParity", "Every")),
            timeOverride = jsonObject.optJSONObject("timeOverride")?.let {
                com.miaom.schedule.domain.model.CourseTimeOverride(
                    startTime = it.optString("startTime"),
                    endTime = it.optString("endTime")
                )
            },
            colorStyle = jsonObject.optJSONObject("colorStyle")?.let { color ->
                com.miaom.schedule.domain.model.CourseColorStyle(
                    useThemeDefaults = color.optBoolean("useThemeDefaults", true),
                    backgroundColorArgb = color.optInt("backgroundColorArgb", 0xFFDBEAFE.toInt()),
                    textColorArgb = color.optInt("textColorArgb", 0xFF102A43.toInt()),
                    borderColorArgb = color.optInt("borderColorArgb", 0xFF6B8BB3.toInt())
                )
            } ?: com.miaom.schedule.domain.model.CourseColorStyle()
        )
    }

    private fun courseTemplateSnapshotToJson(snapshot: com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot): JSONObject {
        return JSONObject()
            .put("courseName", snapshot.courseName)
            .put("teacher", snapshot.teacher)
            .put("location", snapshot.location)
            .put("preferredTimeSlotTemplateId", snapshot.preferredTimeSlotTemplateId)
            .put("preferredTimeSlotLabel", snapshot.preferredTimeSlotLabel)
            .put("weekParity", snapshot.weekParity.name)
            .put(
                "timeOverride",
                snapshot.timeOverride?.let {
                    JSONObject()
                        .put("startTime", it.startTime)
                        .put("endTime", it.endTime)
                }
            )
            .put(
                "colorStyle",
                JSONObject()
                    .put("useThemeDefaults", snapshot.colorStyle.useThemeDefaults)
                    .put("backgroundColorArgb", snapshot.colorStyle.backgroundColorArgb)
                    .put("textColorArgb", snapshot.colorStyle.textColorArgb)
                    .put("borderColorArgb", snapshot.colorStyle.borderColorArgb)
            )
    }
}
