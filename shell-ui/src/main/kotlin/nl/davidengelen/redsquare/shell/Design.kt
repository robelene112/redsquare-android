package nl.davidengelen.redsquare.shell

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Prototype schema: data selects native components, never executable code. */
class Design(val json: JSONObject) {
    val revision = json.getString("revision").also { require(it.length in 1..100) }
    val screens = json.getJSONObject("screens")
    val modules = json.getJSONObject("modules")
    val tokens = json.getJSONObject("tokens")
    init {
        require(json.getInt("schema") == 1 && screens.length() in 1..100)
        require(screens.has("home") && modules.has("redsquare") && modules.has("stack"))
        val supported = setOf("intro","section","text","banner","tabs","post","article","profile","row","rule","button","field","toggle","choices","person","activity","balance","art","article-body","editor","messages","locked","draft-preview","pair","qr","draft-body","local-post","local-article","sync")
        modules.keys().forEach { key ->
            val module = modules.getJSONObject(key)
            require(module.getString("title").isNotBlank() && screens.has(module.getString("home")))
            require(module.getJSONArray("tabs").length() in 1..5)
            module.array("tabs").objects().forEach { require(screens.has(it.getString("route")) && it.getString("label").isNotBlank()) }
        }
        screens.keys().forEach { key ->
            val screen = screens.getJSONObject(key)
            require(screen.getString("module") == "shared" || modules.has(screen.getString("module")))
            val blocks = screen.getJSONArray("blocks").objects()
            require(blocks.size <= 150 && blocks.map { it.getString("id") }.toSet().size == blocks.size)
            blocks.forEach { block ->
                require(block.getString("type") in supported && block.getString("id").isNotBlank())
                if (block.text("type") == "field") require(block.optInt("lines", 1) in 1..12)
                if (block.has("whenField")) block.getJSONObject("whenField").getString("id")
                if (block.has("article")) block.getJSONObject("article")
            }
        }
    }
    fun screen(route: String) = screens.optJSONObject(route) ?: screens.getJSONObject("home")
    fun number(key: String, default: Double, min: Double, max: Double) = tokens.optDouble(key, default).takeIf { it.isFinite() }?.coerceIn(min, max) ?: default
    val margin get() = number("margin", 18.0, 12.0, 32.0).toFloat()
    val gap get() = number("gap", 16.0, 8.0, 28.0).toFloat()
    val radius get() = number("radius", 18.0, 4.0, 32.0).toFloat()
    val controlRadius get() = number("controlRadius", 14.0, 4.0, 32.0).toFloat()
}
internal fun JSONObject.text(key: String, fallback: String = "") = optString(key, fallback)
internal fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
internal fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.color(key: String, fallback: String) = try { Color(AndroidColor.parseColor(text(key, fallback))) } catch (_: IllegalArgumentException) { Color(AndroidColor.parseColor(fallback)) }

@Composable
fun DesignTheme(design: Design, appearance: String, content: @Composable () -> Unit) {
    val dark = when (appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val palette = design.tokens.optJSONObject(if (dark) "dark" else "light") ?: JSONObject()
    val base = if (dark) darkColorScheme(
        primary = palette.color("accent", "#FF8297"), onPrimary = Color(0xff3c0010),
        background = palette.color("background", "#101216"), surface = palette.color("background", "#101216"),
        onBackground = palette.color("text", "#F0F1F5"), onSurface = palette.color("text", "#F0F1F5"),
        surfaceContainerLow = palette.color("card", "#191D24"), surfaceContainer = palette.color("card", "#191D24"),
        surfaceContainerHigh = Color(0xff252b34), onSurfaceVariant = palette.color("muted", "#9DA8B9"),
        outlineVariant = palette.color("line", "#2B323C"), secondaryContainer = Color(0xff2a303d)
    ) else lightColorScheme(
        primary = palette.color("accent", "#BD2645"), onPrimary = Color.White,
        background = palette.color("background", "#FAFBFD"), surface = palette.color("background", "#FAFBFD"),
        onBackground = palette.color("text", "#171C25"), onSurface = palette.color("text", "#171C25"),
        surfaceContainerLow = palette.color("card", "#F0F2F7"), surfaceContainer = palette.color("card", "#F0F2F7"),
        surfaceContainerHigh = Color(0xffe5e9f1), onSurfaceVariant = palette.color("muted", "#5C687A"),
        outlineVariant = palette.color("line", "#DDE2EC"), secondaryContainer = Color(0xffe8edf7)
    )
    val scale = design.number("textScale", 1.0, 0.8, 1.5).toFloat()
    fun style(key: String, size: Int, line: Int, weight: FontWeight = FontWeight.Normal): TextStyle {
        val spec = design.tokens.optJSONObject("type")?.optJSONObject(key) ?: JSONObject()
        val fontSize = spec.optDouble("size", size.toDouble()).takeIf { it.isFinite() }?.coerceIn(9.0, 40.0)?.toFloat() ?: size.toFloat()
        val lineSize = spec.optDouble("lineHeight", line.toDouble()).takeIf { it.isFinite() }?.coerceIn(fontSize.toDouble(), 64.0)?.toFloat() ?: line.toFloat()
        return TextStyle(fontSize = (fontSize * scale).sp, lineHeight = (lineSize * scale).sp, fontWeight = FontWeight(spec.optInt("weight", weight.weight).coerceIn(100,900)))
    }
    MaterialTheme(colorScheme = base, typography = Typography(
        headlineLarge = style("headlineLarge", 30, 36, FontWeight.Bold), headlineMedium = style("headlineMedium", 26, 32, FontWeight.Bold),
        headlineSmall = style("headlineSmall", 22, 28, FontWeight.SemiBold), titleLarge = style("titleLarge", 20, 26, FontWeight.SemiBold),
        titleMedium = style("titleMedium", 16, 22, FontWeight.SemiBold), titleSmall = style("titleSmall", 14, 20, FontWeight.SemiBold),
        bodyLarge = style("bodyLarge", 16, 24), bodyMedium = style("bodyMedium", 14, 21), bodySmall = style("bodySmall", 12, 18),
        labelLarge = style("labelLarge", 14, 20, FontWeight.Medium), labelMedium = style("labelMedium", 12, 17, FontWeight.Medium), labelSmall = style("labelSmall", 11, 16)
    ), content = content)
}
