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
    primary = Color(0xffc61e36), onPrimary = Color.White,
    primaryContainer = Color(0xffffe9ed), onPrimaryContainer = Color(0xff731023),
    secondary = Color(0xff5b626d), onSecondary = Color.White,
    secondaryContainer = Color(0xffedf0f4), onSecondaryContainer = Color(0xff272b33),
    tertiary = Color(0xff5b626d), onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xff17191d),
    surface = Color.White, onSurface = Color(0xff17191d),
    surfaceVariant = Color(0xfff0f2f5), onSurfaceVariant = Color(0xff565d68),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xfff8f9fb),
    surfaceContainer = Color(0xfff0f2f5), surfaceContainerHigh = Color(0xffe9ecf0),
    surfaceContainerHighest = Color(0xffe2e6eb),
    outline = Color(0xff737b86), outlineVariant = Color(0xffdce0e6),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xffff8b9a), onPrimary = Color(0xff4b0010),
    primaryContainer = Color(0xff591827), onPrimaryContainer = Color(0xffffd9df),
    secondary = Color(0xffc1c7d1), onSecondary = Color(0xff272b33),
    secondaryContainer = Color(0xff2a3039), onSecondaryContainer = Color(0xffe4e8ee),
    tertiary = Color(0xffc1c7d1), onTertiary = Color(0xff272b33),
    background = Color(0xff111316), onBackground = Color(0xffeef0f4),
    surface = Color(0xff111316), onSurface = Color(0xffeef0f4),
    surfaceVariant = Color(0xff282c33), onSurfaceVariant = Color(0xffb5bdc9),
    surfaceContainerLowest = Color(0xff0b0d10), surfaceContainerLow = Color(0xff191c21),
    surfaceContainer = Color(0xff20242a), surfaceContainerHigh = Color(0xff282d34),
    surfaceContainerHighest = Color(0xff30363e),
    outline = Color(0xff8d97a5), outlineVariant = Color(0xff343a44),
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
