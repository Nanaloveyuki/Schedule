package com.miaom.schedule.ui.theme

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miaom.schedule.domain.model.BackgroundConfig
import com.miaom.schedule.domain.model.BackgroundImageDisplayMode
import com.miaom.schedule.domain.model.BackgroundMode
import com.miaom.schedule.domain.model.BuiltInFontOption
import com.miaom.schedule.domain.model.FontConfig
import com.miaom.schedule.domain.model.ThemeColorTokens
import com.miaom.schedule.domain.model.ThemeConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DefaultTokens = ThemeColorTokens()

@Composable
fun ScheduleTheme(
    themeConfig: ThemeConfig? = null,
    content: @Composable () -> Unit
) {
    val resolvedConfig = themeConfig ?: ThemeConfig()
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (resolvedConfig.themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> systemDarkTheme
    }
    val colorScheme = remember(resolvedConfig, isDarkTheme, inspectionMode, context) {
        val baseScheme = if (
            resolvedConfig.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !inspectionMode
        ) {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            buildColorScheme(resolvedConfig.colorTokens, isDarkTheme)
        }
        baseScheme.withBackgroundTreatment(
            backgroundConfig = resolvedConfig.background,
            isDarkTheme = isDarkTheme
        )
    }
    val typography = remember(resolvedConfig.font) {
        buildTypography(resolvedConfig.font)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

@Composable
fun ScheduleThemeBackground(
    themeConfig: ThemeConfig,
    modifier: Modifier = Modifier
) {
    val backgroundConfig = themeConfig.background
    val mode = resolveBackgroundMode(backgroundConfig)
    val contentScale = resolveBackgroundContentScale(backgroundConfig)
    val solidColor = parseHexColor(
        hex = backgroundConfig.solidColorHex,
        fallback = themeConfig.colorTokens.backgroundHex
    )
    val blurRadius = backgroundConfig.blurRadiusDp.dp
    val context = LocalContext.current
    val image = rememberBackgroundImage(context, backgroundConfig.imageReference)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(solidColor)
    ) {
        if (mode == BackgroundMode.Image || mode == BackgroundMode.ImageBlur) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (mode == BackgroundMode.ImageBlur) Modifier.blur(blurRadius) else Modifier)
                )
            }
            if (image == null && mode == BackgroundMode.ImageBlur) {
                SoftBlurOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius),
                    colors = listOf(
                        solidColor.copy(alpha = 0.34f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    )
                )
            }
        }

        if (mode == BackgroundMode.SolidBlur) {
            SoftBlurOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius),
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                )
            )
        }

        if (mode == BackgroundMode.Image || mode == BackgroundMode.ImageBlur) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(solidColor.copy(alpha = 0.12f))
            )
        }
    }
}

private fun buildColorScheme(tokens: ThemeColorTokens, isDarkTheme: Boolean): ColorScheme {
    val primary = parseHexColor(tokens.primaryHex, DefaultTokens.primaryHex)
    val secondary = parseHexColor(tokens.secondaryHex, DefaultTokens.secondaryHex)
    val tertiary = parseHexColor(tokens.tertiaryHex, DefaultTokens.tertiaryHex)
    val background = parseHexColor(tokens.backgroundHex, DefaultTokens.backgroundHex)
    val surface = parseHexColor(tokens.surfaceHex, DefaultTokens.surfaceHex)
    val surfaceVariant = parseHexColor(tokens.surfaceVariantHex, DefaultTokens.surfaceVariantHex)
    val onSurface = parseHexColor(tokens.onSurfaceHex, DefaultTokens.onSurfaceHex)
    val outline = parseHexColor(tokens.outlineHex, DefaultTokens.outlineHex)

    return if (isDarkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onSurface,
            secondary = secondary,
            tertiary = tertiary,
            background = onSurface,
            onBackground = background,
            surface = Color(0xFF18222A),
            onSurface = background,
            surfaceVariant = Color(0xFF22313A),
            onSurfaceVariant = surfaceVariant.copy(alpha = 0.92f).compositeOn(Color.White),
            outline = outline.copy(alpha = 0.9f),
            surfaceContainerLow = Color(0xFF1C2932),
            surfaceContainerHighest = Color(0xFF263640)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            tertiary = tertiary,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurface.copy(alpha = 0.78f),
            outline = outline,
            surfaceContainerLow = surface.copy(alpha = 0.94f).compositeOn(background),
            surfaceContainerHighest = surfaceVariant.copy(alpha = 0.72f).compositeOn(background)
        )
    }
}

private fun buildTypography(fontConfig: FontConfig) = ScheduleTypography.run {
    val family = when (fontConfig.builtInFontId) {
        BuiltInFontOption.Serif.name -> FontFamily.Serif
        BuiltInFontOption.Rounded.name -> FontFamily.SansSerif
        BuiltInFontOption.Monospace.name -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family)
    )
}

private fun ColorScheme.withBackgroundTreatment(
    backgroundConfig: BackgroundConfig,
    isDarkTheme: Boolean
): ColorScheme {
    val mode = resolveBackgroundMode(backgroundConfig)
    if (mode != BackgroundMode.Image && mode != BackgroundMode.ImageBlur) {
        return this
    }
    return copy(
        background = background.copy(alpha = if (isDarkTheme) 0.68f else 0.76f),
        surface = surface.copy(alpha = if (isDarkTheme) 0.82f else 0.9f),
        surfaceVariant = surfaceVariant.copy(alpha = if (isDarkTheme) 0.78f else 0.84f),
        surfaceContainerLow = surfaceContainerLow.copy(alpha = if (isDarkTheme) 0.8f else 0.88f),
        surfaceContainerHighest = surfaceContainerHighest.copy(alpha = if (isDarkTheme) 0.82f else 0.86f)
    )
}

private fun Color.compositeOn(background: Color): Color {
    val alpha = this.alpha
    val red = this.red * alpha + background.red * (1 - alpha)
    val green = this.green * alpha + background.green * (1 - alpha)
    val blue = this.blue * alpha + background.blue * (1 - alpha)
    return Color(red, green, blue, 1f)
}

@Composable
private fun rememberBackgroundImage(
    context: Context,
    reference: String
): ImageBitmap? {
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = context,
        key2 = reference
    ) {
        value = withContext(Dispatchers.IO) {
            decodeBackgroundBitmap(context, reference)
        }
    }
    return image
}

private fun decodeBackgroundBitmap(
    context: Context,
    reference: String
): ImageBitmap? {
    val normalizedReference = reference.trim()
    if (normalizedReference.isBlank()) return null
    return runCatching {
        val bitmap = when {
            normalizedReference.startsWith("content://") -> {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(normalizedReference))
                ImageDecoder.decodeBitmap(source)
            }

            normalizedReference.startsWith("file://") -> {
                val file = File(Uri.parse(normalizedReference).path.orEmpty())
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source)
            }

            else -> {
                val source = ImageDecoder.createSource(File(normalizedReference))
                ImageDecoder.decodeBitmap(source)
            }
        }
        bitmap.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun SoftBlurOverlay(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.4f
        val anchors = listOf(
            Offset(size.width * 0.2f, size.height * 0.22f),
            Offset(size.width * 0.82f, size.height * 0.28f),
            Offset(size.width * 0.5f, size.height * 0.82f)
        )
        anchors.zip(colors).forEach { (center, color) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

private fun resolveBackgroundMode(background: BackgroundConfig): BackgroundMode {
    return BackgroundMode.entries.firstOrNull { it.name.equals(background.mode, ignoreCase = true) }
        ?: BackgroundMode.Solid
}

private fun resolveBackgroundContentScale(background: BackgroundConfig): ContentScale {
    return when (
        BackgroundImageDisplayMode.entries.firstOrNull {
            it.name.equals(background.imageDisplayMode, ignoreCase = true)
        } ?: BackgroundImageDisplayMode.Crop
    ) {
        BackgroundImageDisplayMode.Fill -> ContentScale.FillWidth
        BackgroundImageDisplayMode.Fit -> ContentScale.Fit
        BackgroundImageDisplayMode.Stretch -> ContentScale.FillBounds
        BackgroundImageDisplayMode.Crop -> ContentScale.Crop
    }
}

private fun parseHexColor(hex: String, fallback: String): Color {
    val candidate = hex.trim().ifBlank { fallback }.removePrefix("#")
    return runCatching {
        Color(candidate.toLong(16) or 0xFF000000)
    }.getOrElse {
        Color(fallback.removePrefix("#").toLong(16) or 0xFF000000)
    }
}
