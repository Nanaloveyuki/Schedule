package com.miaom.schedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miaom.schedule.domain.model.ThemeConfig
import com.miaom.schedule.domain.model.resolveMetrics
import com.miaom.schedule.ui.viewmodel.BuiltInThemePreset
import kotlin.math.min

private data class PreviewCourseSample(
    val title: String,
    val supportingText: String,
    val metaText: String
)

@Composable
fun ThemeTokenChip(
    label: String,
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                    .background(parsePreviewColor(hex), RoundedCornerShape(999.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(hex, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PresetCard(
    title: String,
    description: String,
    selected: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                Text(if (selected) "当前已启用" else "应用预设")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePreviewCard(
    themeConfig: ThemeConfig,
    previewWidthDp: Float,
    title: String = "课表预览"
) {
    val tokens = themeConfig.colorTokens
    Card(
        colors = CardDefaults.cardColors(containerColor = parsePreviewColor(tokens.surfaceHex))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val resolvedMetrics = themeConfig.gridSizing.resolveMetrics(previewWidthDp.coerceAtLeast(240f))
            val containerWidthDp = min(maxWidth.value, previewWidthDp.coerceAtLeast(280f))
            val previewColumns = if (containerWidthDp >= 420f) 3 else 2
            val gapDp = 8f
            val timeBadgeWidthDp = if (previewColumns == 3) 76f else 68f
            val previewMaxCellWidthDp = (
                (containerWidthDp - timeBadgeWidthDp - gapDp * previewColumns) / previewColumns
                ).coerceAtLeast(72f)
            val scale = min(1f, previewMaxCellWidthDp / resolvedMetrics.cellWidthDp)
            val previewCellWidthDp = (resolvedMetrics.cellWidthDp * scale).coerceAtLeast(72f)
            val previewCellHeightDp = (resolvedMetrics.cellHeightDp * scale).coerceAtLeast(84f)
            val minWidthSummary = themeConfig.gridSizing.gridMinCellWidthDp.toInt()
            val maxWidthSummary = themeConfig.gridSizing.gridMaxCellWidthDp.toInt()
            val minHeightSummary = themeConfig.gridSizing.gridMinCellHeightDp.toInt()
            val maxHeightSummary = themeConfig.gridSizing.gridMaxCellHeightDp.toInt()
            val previewCourses = listOf(
                PreviewCourseSample(
                    title = "高等数学",
                    supportingText = "A-201 · 李老师",
                    metaText = "第 1 周到第 16 周"
                ),
                PreviewCourseSample(
                    title = "数据结构",
                    supportingText = "机房 3 · 王老师",
                    metaText = "覆盖时间 09:00"
                ),
                PreviewCourseSample(
                    title = "英语写作",
                    supportingText = "文科楼 208",
                    metaText = "双周"
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = parsePreviewColor(tokens.onSurfaceHex))
                Surface(
                    color = parsePreviewColor(tokens.backgroundHex).copy(alpha = 0.68f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(timeBadgeWidthDp.dp))
                            repeat(previewColumns) { index ->
                                PreviewColumnHeader(
                                    label = listOf("周一", "周二", "周三")[index],
                                    tokens = tokens,
                                    widthDp = previewCellWidthDp
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            PreviewTimeBadge(
                                tokens = tokens,
                                widthDp = timeBadgeWidthDp,
                                heightDp = previewCellHeightDp,
                                compact = previewColumns == 2
                            )
                            previewCourses.take(previewColumns).forEach { course ->
                                PreviewCourseBlock(
                                    title = course.title,
                                    supportingText = course.supportingText,
                                    metaText = course.metaText,
                                    widthDp = previewCellWidthDp,
                                    heightDp = previewCellHeightDp,
                                    tokens = tokens
                                )
                            }
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewSummaryChip(
                        label = "最小",
                        value = "${minWidthSummary}×${minHeightSummary} dp",
                        tokens = tokens
                    )
                    PreviewSummaryChip(
                        label = "当前",
                        value = "${resolvedMetrics.cellWidthDp.toInt()}×${resolvedMetrics.cellHeightDp.toInt()} dp",
                        tokens = tokens
                    )
                    PreviewSummaryChip(
                        label = "最大",
                        value = "${maxWidthSummary}×${maxHeightSummary} dp",
                        tokens = tokens
                    )
                    PreviewSummaryChip(
                        label = "预览列数",
                        value = "${previewColumns} 列",
                        tokens = tokens
                    )
                }
                Text(
                    text = if (themeConfig.gridSizing.adaptiveSizing) {
                        "已开启自适应：当前尺寸会随设备宽度在 ${minWidthSummary}-${maxWidthSummary} dp 范围内变化，预览只保留 2-${previewColumns} 列来稳定观察比例。"
                    } else {
                        "已关闭自适应：当前优先按较大单元格显示，预览会按相同比例缩放，避免在窄屏里出现挤压错位。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = parsePreviewColor(tokens.onSurfaceHex).copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun PreviewColumnHeader(label: String, tokens: com.miaom.schedule.domain.model.ThemeColorTokens, widthDp: Float) {
    Surface(
        color = parsePreviewColor(tokens.surfaceVariantHex),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(widthDp.dp)
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, color = parsePreviewColor(tokens.onSurfaceHex), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PreviewTimeBadge(
    tokens: com.miaom.schedule.domain.model.ThemeColorTokens,
    widthDp: Float,
    heightDp: Float,
    compact: Boolean
) {
    Surface(
        color = parsePreviewColor(tokens.surfaceVariantHex),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(widthDp.dp)
            .heightIn(min = heightDp.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("第 1 节", style = MaterialTheme.typography.labelLarge, color = parsePreviewColor(tokens.onSurfaceHex))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("08:00", style = MaterialTheme.typography.bodySmall, color = parsePreviewColor(tokens.onSurfaceHex))
                Text("08:45", style = MaterialTheme.typography.bodySmall, color = parsePreviewColor(tokens.onSurfaceHex))
                if (!compact) {
                    Text(
                        "标准节次",
                        style = MaterialTheme.typography.labelSmall,
                        color = parsePreviewColor(tokens.onSurfaceHex).copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCourseBlock(
    title: String,
    supportingText: String,
    metaText: String,
    widthDp: Float,
    heightDp: Float,
    tokens: com.miaom.schedule.domain.model.ThemeColorTokens
) {
    val compact = widthDp < 104f || heightDp < 100f
    val showSupporting = widthDp >= 92f && heightDp >= 96f
    val showMeta = widthDp >= 124f && heightDp >= 124f
    Surface(
        color = parsePreviewColor(tokens.primaryHex).copy(alpha = 0.14f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .border(1.dp, parsePreviewColor(tokens.primaryHex).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Text(
                title,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                color = parsePreviewColor(tokens.onSurfaceHex)
            )
            if (showSupporting) {
                Text(
                    supportingText,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = parsePreviewColor(tokens.onSurfaceHex).copy(alpha = 0.75f)
                )
            }
            if (showMeta) {
                Text(
                    metaText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = parsePreviewColor(tokens.onSurfaceHex).copy(alpha = 0.66f)
                )
            }
        }
    }
}

@Composable
private fun PreviewSummaryChip(
    label: String,
    value: String,
    tokens: com.miaom.schedule.domain.model.ThemeColorTokens
) {
    Surface(
        color = parsePreviewColor(tokens.surfaceVariantHex),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = parsePreviewColor(tokens.onSurfaceHex).copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = parsePreviewColor(tokens.onSurfaceHex)
            )
        }
    }
}

private fun parsePreviewColor(value: String): Color {
    val normalized = value.removePrefix("#")
    return runCatching { Color(normalized.toLong(16) or 0xFF000000) }.getOrElse { Color(0xFF1D7A85) }
}
