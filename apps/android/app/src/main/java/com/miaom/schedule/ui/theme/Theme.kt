package com.miaom.schedule.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.miaom.schedule.domain.model.BuiltInFontOption
import com.miaom.schedule.domain.model.FontConfig
import com.miaom.schedule.domain.model.ThemeColorTokens
import com.miaom.schedule.domain.model.ThemeConfig

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
    val colorScheme = remember(resolvedConfig, isDarkTheme) {
        if (
            resolvedConfig.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !inspectionMode
        ) {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            buildColorScheme(resolvedConfig.colorTokens, isDarkTheme)
        }
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

private fun Color.compositeOn(background: Color): Color {
    val alpha = this.alpha
    val red = this.red * alpha + background.red * (1 - alpha)
    val green = this.green * alpha + background.green * (1 - alpha)
    val blue = this.blue * alpha + background.blue * (1 - alpha)
    return Color(red, green, blue, 1f)
}

private fun parseHexColor(hex: String, fallback: String): Color {
    val candidate = hex.trim().ifBlank { fallback }.removePrefix("#")
    return runCatching {
        Color(candidate.toLong(16) or 0xFF000000)
    }.getOrElse {
        Color(fallback.removePrefix("#").toLong(16) or 0xFF000000)
    }
}
