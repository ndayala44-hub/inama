package rw.inama.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.inama.app.R

/**
 * Inama design system — adapted from the "Smart Farming App" reference (aerial imagery, glass
 * cards, leaf-green accent, floating pill navigation) and the Inama design boards.
 * Colours are semantic so a high-contrast variant can swap them without touching screens.
 */
@Immutable
data class InamaColors(
    val ground: Color = Color(0xFFF1F3EC),
    val paper: Color = Color(0xFFFDFDFA),
    val line: Color = Color(0xFFDFE3D6),
    val line2: Color = Color(0xFFC6CCBA),
    val ink: Color = Color(0xFF17231A),
    val muted: Color = Color(0xFF4C564B),
    val forest: Color = Color(0xFF234726),
    val forestDeep: Color = Color(0xFF15301A),
    val leaf: Color = Color(0xFF3F7F32),
    val sprout: Color = Color(0xFF8CC663),
    val sproutSoft: Color = Color(0xFFE3F0D8),
    val mint: Color = Color(0xFFE1EEDA),
    val mintInk: Color = Color(0xFF1E5230),
    val clay: Color = Color(0xFFA94A26),
    val claySoft: Color = Color(0xFFF5DFD3),
    val clayInk: Color = Color(0xFF7A2E12),
    val amber: Color = Color(0xFFE8AC43),
    val amberSoft: Color = Color(0xFFFAEAC6),
    val amberInk: Color = Color(0xFF5E3F06),
    val sky: Color = Color(0xFF2B6698),
    val skySoft: Color = Color(0xFFDCE9F3),
    val skyInk: Color = Color(0xFF1B4468),
    val sand: Color = Color(0xFFE5E9DD),
    val white: Color = Color(0xFFFFFFFF),
)

private val HighContrast = InamaColors(
    muted = Color(0xFF2B332B),
    line = Color(0xFFB9C0AE),
    line2 = Color(0xFF7D8672),
    leaf = Color(0xFF2E6624),
)

val LocalInamaColors = staticCompositionLocalOf { InamaColors() }

object Inama {
    val colors: InamaColors
        @Composable get() = LocalInamaColors.current
}

val Bricolage = FontFamily(
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

private val InamaTypography = Typography(
    displayMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

private val InamaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * @param textScale Settings › Text size, applied on top of the phone's own font scale so every
 *                  sp value in the app grows together.
 */
@Composable
fun InamaTheme(textScale: Float = 1f, highContrast: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (highContrast) HighContrast else InamaColors()
    val scheme = lightColorScheme(
        primary = colors.forest,
        onPrimary = colors.white,
        primaryContainer = colors.sproutSoft,
        onPrimaryContainer = colors.ink,
        secondary = colors.sprout,
        onSecondary = colors.ink,
        tertiary = colors.clay,
        background = colors.ground,
        onBackground = colors.ink,
        surface = colors.paper,
        onSurface = colors.ink,
        surfaceVariant = colors.sand,
        onSurfaceVariant = colors.muted,
        outline = colors.line2,
        outlineVariant = colors.line,
        error = colors.clay,
        onError = colors.white,
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalInamaColors provides colors,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = InamaTypography, shapes = InamaShapes, content = content)
    }
}
