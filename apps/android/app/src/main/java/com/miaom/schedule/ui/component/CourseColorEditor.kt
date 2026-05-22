package com.miaom.schedule.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.drawscope.Stroke
import com.miaom.schedule.domain.model.ensureReadableTextColor
import com.miaom.schedule.domain.model.resolveReadableTextColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CourseColorEditorMode(val label: String) {
    Hsv("HSV"),
    Rgba("RGBA"),
    Wheel("圆盘")
}

data class CourseColorEditorState(
    val useThemeDefaults: Boolean = true,
    val backgroundColorArgb: Int = 0xFFDBEAFE.toInt(),
    val textColorArgb: Int = 0xFF102A43.toInt(),
    val borderColorArgb: Int = 0xFF6B8BB3.toInt()
)

@Composable
fun CourseColorEditor(
    state: CourseColorEditorState,
    mode: CourseColorEditorMode,
    onModeChange: (CourseColorEditorMode) -> Unit,
    onStateChange: (CourseColorEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(state.backgroundColorArgb)
    val textColor = Color(
        if (state.useThemeDefaults) resolveReadableTextColor(state.backgroundColorArgb)
        else ensureReadableTextColor(state.backgroundColorArgb, state.textColorArgb)
    )
    val borderColor = Color(state.borderColorArgb)
    val hsv = remember(state.backgroundColorArgb) { argbToHsv(state.backgroundColorArgb) }
    val rgba = remember(state.backgroundColorArgb) { argbToRgba(state.backgroundColorArgb) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CourseColorEditorMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = { onModeChange(candidate) },
                    label = { Text(candidate.label) }
                )
            }
        }

        EditorSwitchRow(
            title = "使用主题默认文字颜色",
            description = "开启后自动按背景亮度选择更易读的文字色。",
            checked = state.useThemeDefaults,
            onCheckedChange = { enabled ->
                onStateChange(
                    state.copy(
                        useThemeDefaults = enabled,
                        textColorArgb = if (enabled) resolveReadableTextColor(state.backgroundColorArgb) else state.textColorArgb
                    )
                )
            }
        )

        when (mode) {
            CourseColorEditorMode.Hsv -> HsvEditor(
                hsv = hsv,
                onBackgroundChanged = { h, s, v ->
                    val updatedBackground = hsvToArgb(h, s, v, rgba.alpha)
                    onStateChange(
                        state.copy(
                            backgroundColorArgb = updatedBackground,
                            textColorArgb = if (state.useThemeDefaults) resolveReadableTextColor(updatedBackground) else ensureReadableTextColor(updatedBackground, state.textColorArgb)
                        )
                    )
                }
            )

            CourseColorEditorMode.Rgba -> RgbaEditor(
                rgba = rgba,
                textColorArgb = state.textColorArgb,
                borderColorArgb = state.borderColorArgb,
                useThemeDefaults = state.useThemeDefaults,
                onBackgroundChanged = { updatedRgba ->
                    val updatedBackground = rgbaToArgb(updatedRgba)
                    onStateChange(
                        state.copy(
                            backgroundColorArgb = updatedBackground,
                            textColorArgb = if (state.useThemeDefaults) resolveReadableTextColor(updatedBackground) else ensureReadableTextColor(updatedBackground, state.textColorArgb)
                        )
                    )
                },
                onTextColorChanged = { value ->
                    onStateChange(state.copy(textColorArgb = ensureReadableTextColor(state.backgroundColorArgb, value)))
                },
                onBorderColorChanged = { value ->
                    onStateChange(state.copy(borderColorArgb = value))
                }
            )

            CourseColorEditorMode.Wheel -> WheelEditor(
                hsv = hsv,
                onBackgroundChanged = { h, s, v ->
                    val updatedBackground = hsvToArgb(h, s, v, rgba.alpha)
                    onStateChange(
                        state.copy(
                            backgroundColorArgb = updatedBackground,
                            textColorArgb = if (state.useThemeDefaults) resolveReadableTextColor(updatedBackground) else ensureReadableTextColor(updatedBackground, state.textColorArgb)
                        )
                    )
                }
            )
        }

        Text("实时预览", style = MaterialTheme.typography.titleSmall)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = backgroundColor,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("高等数学", color = textColor, style = MaterialTheme.typography.titleMedium)
                Text("第 2 节 · 08:00 - 08:45", color = textColor.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                Text("教师：张老师 · 地点：A-203", color = textColor.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HsvEditor(
    hsv: HsvColor,
    onBackgroundChanged: (Float, Float, Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatField("Hue", hsv.hue, 0f, 360f) { onBackgroundChanged(it, hsv.saturation, hsv.value) }
        FloatField("Saturation", hsv.saturation, 0f, 1f) { onBackgroundChanged(hsv.hue, it, hsv.value) }
        FloatField("Value", hsv.value, 0f, 1f) { onBackgroundChanged(hsv.hue, hsv.saturation, it) }
    }
}

@Composable
private fun RgbaEditor(
    rgba: RgbaColor,
    textColorArgb: Int,
    borderColorArgb: Int,
    useThemeDefaults: Boolean,
    onBackgroundChanged: (RgbaColor) -> Unit,
    onTextColorChanged: (Int) -> Unit,
    onBorderColorChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntField("R", rgba.red, 0, 255) { onBackgroundChanged(rgba.copy(red = it)) }
        IntField("G", rgba.green, 0, 255) { onBackgroundChanged(rgba.copy(green = it)) }
        IntField("B", rgba.blue, 0, 255) { onBackgroundChanged(rgba.copy(blue = it)) }
        IntField("A", rgba.alpha, 0, 255) { onBackgroundChanged(rgba.copy(alpha = it)) }
        if (!useThemeDefaults) {
            ArgbHexField("文字色", textColorArgb, onTextColorChanged)
        }
        ArgbHexField("边框色", borderColorArgb, onBorderColorChanged)
    }
}

@Composable
private fun WheelEditor(
    hsv: HsvColor,
    onBackgroundChanged: (Float, Float, Float) -> Unit
) {
    val wheelSize = 220.dp
    val density = LocalDensity.current
    val wheelSizePx = with(density) { wheelSize.toPx() }
    val radius = wheelSizePx / 2f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(wheelSize)
                .pointerInput(hsv.hue, hsv.saturation) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            updateWheelColor(offset, radius, hsv.value, onBackgroundChanged)
                        },
                        onDrag = { change, _ ->
                            updateWheelColor(change.position, radius, hsv.value, onBackgroundChanged)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.size(wheelSize)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                for (angle in 0 until 360 step 2) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.hsv(angle.toFloat(), 1f, 1f),
                                Color.hsv((angle + 2).toFloat(), 1f, 1f)
                            ),
                            center = center
                        ),
                        startAngle = angle.toFloat(),
                        sweepAngle = 2f,
                        useCenter = true,
                        size = Size(size.width, size.height)
                    )
                }
                drawCircle(color = Color.White.copy(alpha = 0.22f), radius = size.minDimension / 2f)
                drawCircle(color = Color.White, radius = size.minDimension / 2f * (1f - hsv.saturation))
                val selectorRadius = size.minDimension / 2f * hsv.saturation
                val selectorRadians = Math.toRadians(hsv.hue.toDouble())
                val selectorCenter = Offset(
                    x = center.x + cos(selectorRadians).toFloat() * selectorRadius,
                    y = center.y + sin(selectorRadians).toFloat() * selectorRadius
                )
                drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = 9f, center = selectorCenter, style = Stroke(width = 3f))
                drawCircle(color = Color.White, radius = 9f, center = selectorCenter, style = Stroke(width = 1.5f))
            }
        }
        FloatField("Value", hsv.value, 0f, 1f) { onBackgroundChanged(hsv.hue, hsv.saturation, it) }
    }
}

private fun updateWheelColor(
    offset: Offset,
    radius: Float,
    currentValue: Float,
    onBackgroundChanged: (Float, Float, Float) -> Unit
) {
    val center = Offset(radius, radius)
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
    val saturation = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius).coerceIn(0f, 1f)
    onBackgroundChanged(hue, saturation, currentValue)
}

@Composable
private fun FloatField(label: String, value: Float, min: Float, max: Float, onValueChanged: (Float) -> Unit) {
    OutlinedTextField(
        value = if (max <= 1f) String.format("%.2f", value) else value.roundToInt().toString(),
        onValueChange = { input ->
            input.toFloatOrNull()?.let { parsed -> onValueChanged(parsed.coerceIn(min, max)) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun IntField(label: String, value: Int, min: Int, max: Int, onValueChanged: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { input ->
            input.toIntOrNull()?.let { parsed -> onValueChanged(parsed.coerceIn(min, max)) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ArgbHexField(label: String, value: Int, onValueChanged: (Int) -> Unit) {
    OutlinedTextField(
        value = remember(value) { "#%08X".format(value) },
        onValueChange = { input ->
            parseArgbHex(input)?.let(onValueChanged)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

private data class RgbaColor(val red: Int, val green: Int, val blue: Int, val alpha: Int)

private fun argbToRgba(argb: Int): RgbaColor = RgbaColor(
    red = (argb shr 16) and 0xFF,
    green = (argb shr 8) and 0xFF,
    blue = argb and 0xFF,
    alpha = (argb shr 24) and 0xFF
)

private fun rgbaToArgb(rgba: RgbaColor): Int =
    ((rgba.alpha and 0xFF) shl 24) or ((rgba.red and 0xFF) shl 16) or ((rgba.green and 0xFF) shl 8) or (rgba.blue and 0xFF)

private fun argbToHsv(argb: Int): HsvColor {
    val rgba = argbToRgba(argb)
    val red = rgba.red / 255f
    val green = rgba.green / 255f
    val blue = rgba.blue / 255f
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    val delta = maxChannel - minChannel
    val hue = when {
        delta == 0f -> 0f
        maxChannel == red -> 60f * (((green - blue) / delta) % 6f)
        maxChannel == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (maxChannel == 0f) 0f else delta / maxChannel
    return HsvColor(hue = hue, saturation = saturation, value = maxChannel)
}

private fun hsvToArgb(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
    val chroma = value * saturation
    val x = chroma * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
    val match = value - chroma
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(chroma, x, 0f)
        hue < 120f -> Triple(x, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, x)
        hue < 240f -> Triple(0f, x, chroma)
        hue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return rgbaToArgb(
        RgbaColor(
            red = ((r1 + match) * 255).roundToInt().coerceIn(0, 255),
            green = ((g1 + match) * 255).roundToInt().coerceIn(0, 255),
            blue = ((b1 + match) * 255).roundToInt().coerceIn(0, 255),
            alpha = alpha.coerceIn(0, 255)
        )
    )
}

private fun parseArgbHex(input: String): Int? {
    val normalized = input.trim().removePrefix("#")
    if (!normalized.matches(Regex("^[0-9A-Fa-f]{8}$"))) return null
    return normalized.toLongOrNull(16)?.toInt()
}
