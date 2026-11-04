package nl.davidengelen.redsquare.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.davidengelen.redsquare.R

/** A device preference, independent of portable wallet options and backend selection. */
internal enum class Appearance(val label: Int) {
    SYSTEM(R.string.appearance_system), LIGHT(R.string.appearance_light), DARK(R.string.appearance_dark)
}

internal class AndroidAppearanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun load(): Appearance = Appearance.entries.find { it.name == preferences.getString("mode", null) } ?: Appearance.SYSTEM
    fun save(appearance: Appearance) {
        check(preferences.edit().putString("mode", appearance.name).commit()) { "Could not save appearance" }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xffbd2645), onPrimary = Color.White,
    primaryContainer = Color(0xffffe9ed), onPrimaryContainer = Color(0xff731023),
    secondary = Color(0xff5b626d), onSecondary = Color.White,
    secondaryContainer = Color(0xffe8edf7), onSecondaryContainer = Color(0xff272b33),
    tertiary = Color(0xff5b626d), onTertiary = Color.White,
    background = Color(0xfffafbfd), onBackground = Color(0xff171c25),
    surface = Color(0xfffafbfd), onSurface = Color(0xff171c25),
    surfaceVariant = Color(0xfff0f2f7), onSurfaceVariant = Color(0xff5c687a),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xfff0f2f7),
    surfaceContainer = Color(0xfff0f2f7), surfaceContainerHigh = Color(0xffe5e9f1),
    surfaceContainerHighest = Color(0xffe2e6eb),
    outline = Color(0xff737b86), outlineVariant = Color(0xffdde2ec),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xffff8297), onPrimary = Color(0xff3c0010),
    primaryContainer = Color(0xff591827), onPrimaryContainer = Color(0xffffd9df),
    secondary = Color(0xffc1c7d1), onSecondary = Color(0xff272b33),
    secondaryContainer = Color(0xff2a303d), onSecondaryContainer = Color(0xffe4e8ee),
    tertiary = Color(0xffc1c7d1), onTertiary = Color(0xff272b33),
    background = Color(0xff101216), onBackground = Color(0xfff0f1f5),
    surface = Color(0xff101216), onSurface = Color(0xfff0f1f5),
    surfaceVariant = Color(0xff282c33), onSurfaceVariant = Color(0xff9da8b9),
    surfaceContainerLowest = Color(0xff0b0d10), surfaceContainerLow = Color(0xff191d24),
    surfaceContainer = Color(0xff191d24), surfaceContainerHigh = Color(0xff252b34),
    surfaceContainerHighest = Color(0xff30363e),
    outline = Color(0xff8d97a5), outlineVariant = Color(0xff2b323c),
)

private fun readableText(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp,
    letterSpacing = 0.sp, fontWeight = weight,
)
private val RedSquareTypography = Typography(
    headlineLarge = readableText(30, 36, FontWeight.Bold),
    headlineMedium = readableText(26, 32, FontWeight.Bold),
    headlineSmall = readableText(22, 28, FontWeight.SemiBold),
    titleLarge = readableText(20, 26, FontWeight.SemiBold),
    titleMedium = readableText(16, 22, FontWeight.SemiBold),
    titleSmall = readableText(14, 20, FontWeight.SemiBold),
    bodyLarge = readableText(16, 24), bodyMedium = readableText(14, 21), bodySmall = readableText(12, 18),
    labelLarge = readableText(14, 20, FontWeight.Medium),
    labelMedium = readableText(12, 17, FontWeight.Medium), labelSmall = readableText(11, 16),
)

@Composable
internal fun RedSquareTheme(activity: ComponentActivity, appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    SideEffect {
        // The override also controls system-bar icon contrast, including Android 17's edge-to-edge window.
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = RedSquareTypography, content = content)
}
