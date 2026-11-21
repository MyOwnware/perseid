package app.myownware.perseid.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val INK_ARGB = 0xFF121417
private const val PAPER_ARGB = 0xFFF2EFE8
private const val AMBER_ARGB = 0xFFE8A13A
private const val LIGHT_BACKGROUND_ARGB = 0xFFF8F6F1
private const val LIGHT_SECONDARY_TEXT_ARGB = 0xFF55534F
private const val LIGHT_OUTLINE_ARGB = 0xFF77736B
private const val LIGHT_ERROR_ARGB = 0xFF9B302B
private const val LIGHT_PROGRESS_TRACK_ARGB = 0xFF5D5951
private const val DARK_SURFACE_ARGB = 0xFF1C1F23
private const val DARK_SECONDARY_TEXT_ARGB = 0xFFB7B5B0
private const val DARK_OUTLINE_ARGB = 0xFF858A91
private const val DARK_ERROR_ARGB = 0xFFFFB4AB
private const val DARK_PROGRESS_TRACK_ARGB = 0xFF42464D

private val Ink = Color(INK_ARGB)
private val Paper = Color(PAPER_ARGB)
private val Amber = Color(AMBER_ARGB)

internal data class DesignPalette(
    val scheme: ColorScheme,
    val progressTrack: Color,
)

internal val LightPalette =
    DesignPalette(
        scheme =
            lightColorScheme(
                primary = Amber,
                onPrimary = Ink,
                background = Color(LIGHT_BACKGROUND_ARGB),
                onBackground = Ink,
                surface = Paper,
                onSurface = Ink,
                onSurfaceVariant = Color(LIGHT_SECONDARY_TEXT_ARGB),
                outline = Color(LIGHT_OUTLINE_ARGB),
                error = Color(LIGHT_ERROR_ARGB),
            ),
        progressTrack = Color(LIGHT_PROGRESS_TRACK_ARGB),
    )

internal val DarkPalette =
    DesignPalette(
        scheme =
            darkColorScheme(
                primary = Amber,
                onPrimary = Ink,
                background = Ink,
                onBackground = Paper,
                surface = Color(DARK_SURFACE_ARGB),
                onSurface = Paper,
                onSurfaceVariant = Color(DARK_SECONDARY_TEXT_ARGB),
                outline = Color(DARK_OUTLINE_ARGB),
                error = Color(DARK_ERROR_ARGB),
            ),
        progressTrack = Color(DARK_PROGRESS_TRACK_ARGB),
    )

internal object DesignSize {
    val Tiny = 4.dp
    val Small = 8.dp
    val RowGap = 12.dp
    val Section = 16.dp
    val Gutter = 20.dp
    val Icon = 24.dp
    val TouchTarget = 48.dp
}

internal object DesignMotion {
    const val SELECTION_MILLIS = 150
    const val STATE_MILLIS = 200
}

private fun type(
    size: TextUnit,
    line: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
)

private val AppTypography =
    Typography(
        headlineMedium = type(28.sp, 36.sp, FontWeight.SemiBold),
        titleMedium = type(16.sp, 24.sp, FontWeight.SemiBold),
        bodyLarge = type(14.sp, 20.sp),
        bodyMedium = type(14.sp, 20.sp),
        labelLarge = type(14.sp, 20.sp, FontWeight.Medium),
        labelMedium = type(12.sp, 16.sp),
        bodySmall = type(12.sp, 16.sp),
    )

private val ControlShape = RoundedCornerShape(8.dp)
private val AppShapes = Shapes(extraSmall = ControlShape, small = ControlShape, medium = ControlShape)

@Composable
internal fun PerseidTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkPalette.scheme else LightPalette.scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

@Composable
internal fun progressTrackColor(): Color =
    if (MaterialTheme.colorScheme.background == DarkPalette.scheme.background) {
        DarkPalette.progressTrack
    } else {
        LightPalette.progressTrack
    }
