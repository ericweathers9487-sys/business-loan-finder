package com.yourco.lending.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourco.lending.R

/**
 * Brand colors that stay the same in light and dark mode. The button gradient
 * is dark enough for white text in both (at least 5.5:1).
 */
object Brand {
    val Emerald = Color(0xFF047857)
    val Ocean = Color(0xFF0369A1)
    val Mint = Color(0xFF34D399)
    val Aqua = Color(0xFF22D3EE)
    val Ink = Color(0xFF0B1324)

    /** Primary buttons and selected states. */
    val action = Brush.linearGradient(listOf(Emerald, Ocean))

    /** Highlights on dark backgrounds: score rings, big numbers. */
    val glow = Brush.linearGradient(listOf(Mint, Aqua))

    /** The dark hero panels. */
    val hero = Brush.linearGradient(listOf(Ink, Color(0xFF0D2A3A), Color(0xFF0B4A40)))
    val heroAmber = Brush.linearGradient(listOf(Color(0xFF2A1A05), Color(0xFF4A2E07), Color(0xFF6B4410)))
    val heroSlate = Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF334155)))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0B1324),
    surface = Color.White,
    onSurface = Color(0xFF0B1324),
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Color(0xFF566074),
    surfaceContainerHighest = Color(0xFFE6EBF2),
    outline = Color(0xFF848FA2),
    outlineVariant = Color(0xFFE2E7EF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF3D2A06),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3F1010),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF070B14),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0F1626),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF182136),
    onSurfaceVariant = Color(0xFF9AA4B8),
    surfaceContainerHighest = Color(0xFF1E2840),
    outline = Color(0xFF63708F),
    outlineVariant = Color(0xFF232D45),
)

/** Plus Jakarta Sans (SIL Open Font License; see assets/licenses). */
val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val AppTypography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 48, 54, -1.2),
    displayMedium = style(FontWeight.ExtraBold, 40, 46, -1.0),
    displaySmall = style(FontWeight.ExtraBold, 34, 40, -0.8),
    headlineLarge = style(FontWeight.ExtraBold, 30, 36, -0.6),
    headlineMedium = style(FontWeight.ExtraBold, 26, 32, -0.5),
    headlineSmall = style(FontWeight.Bold, 22, 28, -0.3),
    titleLarge = style(FontWeight.Bold, 20, 26, -0.2),
    titleMedium = style(FontWeight.Bold, 16, 22, -0.1),
    titleSmall = style(FontWeight.SemiBold, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 21),
    bodySmall = style(FontWeight.Normal, 12, 18),
    labelLarge = style(FontWeight.SemiBold, 15, 20, 0.1),
    labelMedium = style(FontWeight.SemiBold, 12, 16, 0.2),
    labelSmall = style(FontWeight.SemiBold, 11, 14, 0.4),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LoanFinderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
