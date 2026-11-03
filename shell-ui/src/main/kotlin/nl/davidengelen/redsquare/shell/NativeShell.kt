package nl.davidengelen.redsquare.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** All sample edits are local to this prototype session. Switching modules does not recreate it. */
class ShellSession {
    var module by mutableStateOf("redsquare")
    var route by mutableStateOf("home")
    var sheet by mutableStateOf("")
    var dialog by mutableStateOf("")
    var stateMode by mutableStateOf("Normal")
    var notice by mutableStateOf("")
    val routes = mutableStateMapOf("redsquare" to "home", "stack" to "stack")
    val history = mutableStateListOf<Pair<String, String>>()
    val fields = mutableStateMapOf<String, String>()
    val editors = mutableMapOf<String, com.mohamedrejeb.richeditor.model.RichTextState>()
    val visualBaselines = mutableMapOf<String, String>()
    val flags = mutableStateMapOf<String, Boolean>()
    val counts = mutableStateMapOf<String, Int>()
    val lists = mutableMapOf<String, LazyListState>()
    val messages = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<String>>()
    fun back() {
        if (history.isNotEmpty()) {
            val last = history.removeAt(history.lastIndex); module = last.first; route = last.second
        } else { module = "redsquare"; route = "home" }
    }
    fun navigate(target: String, design: Design) {
        if (!design.screens.has(target) || target == route) return
        history.add(module to route)
        val destination = design.screen(target)
        val nextModule = destination.text("module", module)
        if (nextModule != "shared") module = nextModule
        route = target
        if (nextModule != "shared") routes[module] = target
    }
    fun action(value: String, design: Design) {
        when {
            value == "back" -> back()
            value.startsWith("sheet:") -> sheet = value.removePrefix("sheet:")
            value.startsWith("dialog:") -> dialog = value.removePrefix("dialog:")
            value.startsWith("notice:") -> notice = value.removePrefix("notice:")
            value.startsWith("module:") -> {
                routes[module] = route.takeIf { design.screen(route).text("module") != "shared" } ?: routes[module] ?: "home"
                history.add(module to route)
                module = value.removePrefix("module:")
                route = routes[module] ?: design.modules.getJSONObject(module).text("home")
                sheet = ""
            }
            value.startsWith("like:") -> { val id = value.removePrefix("like:"); counts[id] = (counts[id] ?: 0) + 1 }
            value.startsWith("follow:") -> { val id = value.removePrefix("follow:"); flags[id] = flags[id] != true }
            value == "confirm:access" -> { flags["article-access"] = true; navigate("locked-article", design); notice = "Preview access is now active" }
            value == "confirm:publish" -> {
                flags["published"] = true
                fields["published-access"] = fields["article-visibility"] ?: "public"
                if (flags["share-redsquare"] == true) {
                    flags["posted"] = true; flags["posted-article"] = true
                    fields["posted-preview"] = fields["share-copy"] ?: "A new story, shared on Stack."
                }
                navigate("published", design)
            }
            value == "confirm:profile" -> { flags["profile-saved"] = true; navigate("profile", design); notice = "Profile preview saved" }
            value == "confirm:send" -> { navigate("wallet", design); notice = "Demo transfer complete. No funds were sent." }
            value == "confirm:post" -> { fields["posted-preview"] = fields["post-$route"] ?: "A new preview post"; flags["posted"] = true; flags["posted-article"] = false; navigate(if (route == "reply") "thread" else "home", design); notice = "Preview post added" }
            value == "refresh" -> { flags["refreshDesign"] = true; notice = "Checking for a new design" }
            else -> navigate(value, design)
        }
    }
}

@Composable
fun ShellIcon(name: String, label: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val context = LocalContext.current
    val id = remember(name) { context.resources.getIdentifier("ic_$name", "drawable", context.packageName).takeIf { it != 0 } ?: R.drawable.ic_more }
    Icon(painterResource(id), label, modifier.size(22.dp), tint = tint)
}

@Composable
internal fun Avatar(name: String, size: Int = 42, label: String? = null, action: (() -> Unit)? = null) {
    val colors = listOf(Color(0xff487d79), Color(0xff7365a6), Color(0xffbf6a4d), Color(0xff487bac))
    val color = colors[(name.hashCode().toLong().let { if (it < 0) -it else it } % colors.size).toInt()]
    val letters = name.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    Box(Modifier.size((if (action != null) maxOf(size, 48) else size).dp)
        .then(if (action != null) Modifier.clip(CircleShape).clickable(onClick = action).semantics { contentDescription = label ?: "Open $name profile" } else Modifier), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(color, color.copy(alpha = .55f))), CircleShape), contentAlignment = Alignment.Center) {
            Text(letters, color = Color.White, style = if (size > 48) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NativeShell(design: Design, session: ShellSession, syncLabel: String, appearance: String,
    onAppearance: (String) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(session.notice) {
        if (session.notice.isNotEmpty()) { val message = session.notice; snack.showSnackbar(message); if (session.notice == message) session.notice = "" }
    }
    val screen = design.screen(session.route)
    val module = design.modules.getJSONObject(session.module)
    val account = design.json.optJSONObject("account") ?: JSONObject()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val action: (String) -> Unit = { value ->
        if (value.startsWith("copy:")) {
            val copied = when (value.removePrefix("copy:")) {
                "address" -> "DEMO-ADDRESS-NOT-A-REAL-WALLET"
                "invite" -> "https://saito.io/chat?group=preview"
                else -> "Demo identity: alex@saito"
            }
            clipboard.setText(AnnotatedString(copied)); session.notice = "Copied"
        } else session.action(value, design)
    }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val tabs = module.array("tabs").objects()
    val list = session.lists.getOrPut(session.route) { LazyListState() }
    BackHandler(session.history.isNotEmpty() || session.module != "redsquare") { session.back() }
    Scaffold(Modifier.fillMaxSize().imePadding(), containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = design.margin.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { session.sheet = "apps" }) { ShellIcon("apps", "Apps") }
                    Text(module.text("title"), Modifier.weight(1f).padding(horizontal = 6.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Avatar(account.text("name", "Alex Morgan"), 36, label = "Account") { session.sheet = "account" }
                }
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (session.stateMode == "Offline") Color(0xffea6576) else Color(0xff6abf9a), CircleShape))
                    Text(session.fields["node"] ?: design.json.text("node", "saito.io"), Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(6.dp), onClick = { action("design-settings") }) {
                        Text("Design preview", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            if (screen.optBoolean("navigation", true) && !ime) Column {
                val compact = LocalDensity.current.fontScale > 1.3f
                if (compact) Text(tabs.firstOrNull { it.text("route") == screen.text("tab", session.route) }?.text("label") ?: module.text("title"), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelMedium)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets.navigationBars) {
                tabs.forEach { tab ->
                    NavigationBarItem(selected = screen.text("tab", session.route) == tab.text("route"),
                        onClick = { action(tab.text("route")) },
                        icon = { ShellIcon(tab.text("icon"), if (compact) tab.text("label") else null) },
                        label = if (compact) null else { { Text(tab.text("label"), maxLines = 1, style = MaterialTheme.typography.labelSmall) } })
                }
            }
            }
        },
        floatingActionButton = {
            if (screen.has("fab") && !ime) FloatingActionButton(onClick = { action(screen.getJSONObject("fab").text("action")) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                ShellIcon(screen.getJSONObject("fab").text("icon", "compose"), screen.getJSONObject("fab").text("label", "Compose"))
            }
        }) { padding ->
        androidx.compose.animation.Crossfade(session.route, modifier = Modifier.fillMaxSize().padding(padding), animationSpec = androidx.compose.animation.core.tween(160), label = "Native destination") { renderRoute ->
        val screen = design.screen(renderRoute)
        val list = session.lists.getOrPut(renderRoute) { LazyListState() }
        Column(Modifier.fillMaxSize()) {
            if (session.stateMode == "Offline") StateBanner("Offline", "Your place and drafts are kept on this device.", "info")
            if (screen.has("title")) Row(Modifier.fillMaxWidth().padding(horizontal = design.margin.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (screen.optBoolean("back")) IconButton(onClick = { session.back() }) { ShellIcon("back", "Back") }
                Text(screen.text("title"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                if (screen.has("topAction")) TextButton(onClick = { action(screen.getJSONObject("topAction").text("action")) }) { Text(screen.getJSONObject("topAction").text("label")) }
            }
            val stateScreens = screen.optBoolean("states", true)
            if (session.stateMode == "Loading" && stateScreens) {
                Column(Modifier.fillMaxWidth().padding(design.margin.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(3) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(160.dp).height(20.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)))
                        Box(Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(design.radius.dp)))
                    } }
                    Text("Loading preview…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if ((session.stateMode == "Empty" || session.stateMode == "Error") && stateScreens) {
                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShellIcon(if (session.stateMode == "Error") "info" else "article", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (session.stateMode == "Error") "Could not load this view" else "A little quiet here", style = MaterialTheme.typography.titleLarge)
                    Text(if (session.stateMode == "Error") "Your saved content and drafts are still here." else "New stories and conversations will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { session.stateMode = "Normal" }) { Text(if (session.stateMode == "Error") "Try again" else "Explore preview") }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                    contentPadding = PaddingValues(start = design.margin.dp, end = design.margin.dp, top = 6.dp, bottom = if (screen.has("fab")) 96.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(design.gap.dp)) {
                    items(screen.array("blocks").objects(), key = { it.text("id", it.toString()) }) { block ->
                        ShellBlock(block, design, session, action)
                    }
                }
                if (screen.has("input")) {
                    val input = screen.getJSONObject("input"); val key = input.text("id", "message")
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
                            IconButton(onClick = { session.dialog = "Add an image or GIF" }) { ShellIcon("plus", "Attach") }
                            OutlinedTextField(value = session.fields[key] ?: "", onValueChange = { session.fields[key] = it },
                                placeholder = { Text(input.text("placeholder", "Message")) }, modifier = Modifier.weight(1f), maxLines = 5, shape = RoundedCornerShape(24.dp))
                            IconButton(enabled = !session.fields[key].isNullOrBlank(), onClick = {
                                session.messages.getOrPut(key) { mutableStateListOf() }.add(session.fields[key] ?: ""); session.fields[key] = ""
                                scope.launch { list.animateScrollToItem((screen.array("blocks").length() - 1).coerceAtLeast(0)) }
                            }) { ShellIcon("send", "Send message", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
    }
    if (session.sheet.isNotEmpty()) ModalBottomSheet(onDismissRequest = { session.sheet = "" }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = design.margin.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (session.sheet) {
                "apps" -> {
                    Text("Your Saito apps", style = MaterialTheme.typography.headlineSmall)
                    Text("One identity. A place for everything.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    design.modules.keys().forEach { key ->
                        val item = design.modules.getJSONObject(key)
                        Surface(onClick = { action("module:$key") }, shape = RoundedCornerShape(design.radius.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(shape = RoundedCornerShape(12.dp), color = if (key == "redsquare") MaterialTheme.colorScheme.primary else Color(0xffa7c9ff)) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ShellIcon(item.text("icon"), tint = if (key == "redsquare") MaterialTheme.colorScheme.onPrimary else Color(0xff122344)) }
                                }
                                Column(Modifier.weight(1f)) { Text(item.text("title"), style = MaterialTheme.typography.titleMedium); Text(item.text("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                ShellIcon(if (session.module == key) "check" else "chevron", if (session.module == key) "Current module" else null)
                            }
                        }
                    }
                }
                "account" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(account.text("name", "Alex Morgan"), 50); Column { Text(account.text("name", "Alex Morgan"), style = MaterialTheme.typography.titleLarge); Text("${account.text("handle", "alex@saito")} · ${account.text("description", "Demo identity")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    design.json.array("accountMenu").objects().forEach { entry ->
                        MenuRow(entry.text("title"), entry.text("body"), entry.text("icon")) { session.sheet = ""; action(entry.text("action")) }
                    }
                    Text("$syncLabel · ${design.revision}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("Post actions", style = MaterialTheme.typography.headlineSmall)
                    MenuRow("Public identity", "View this account", "profile") { session.sheet = ""; action("author") }
                    MenuRow("Mute account", "Hide posts and activity", "notifications_off") { session.sheet = ""; session.dialog = "Mute this demo account?" }
                    MenuRow("Block account", "Hide posts and incoming messages", "lock") { session.sheet = ""; session.dialog = "Block this demo account?" }
                    MenuRow("Report", "Send a report to your selected node", "info") { session.sheet = ""; action("report") }
                }
            }
        }
    }
    if (session.dialog.isNotEmpty()) AlertDialog(onDismissRequest = { session.dialog = "" }, title = { Text(session.dialog) },
        text = { Text("This is an interactive design preview. This action uses example data.") },
        confirmButton = { TextButton(onClick = { session.dialog = ""; session.notice = "Preview action complete" }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { session.dialog = "" }) { Text("Cancel") } })
    if (session.route == "design-settings") {
        // Controls are rendered by the same native blocks as other settings. No separate web UI.
        LaunchedEffect(appearance) { session.fields["appearance"] = appearance }
    }
    LaunchedEffect(session.fields["appearance"]) { session.fields["appearance"]?.let(onAppearance) }
    LaunchedEffect(session.flags["refreshDesign"]) { if (session.flags["refreshDesign"] == true) { session.flags["refreshDesign"] = false; onRefresh() } }
}

@Composable
internal fun MenuRow(title: String, subtitle: String, icon: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ShellIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ShellIcon("chevron", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StateBanner(title: String, subtitle: String, icon: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShellIcon(icon, tint = MaterialTheme.colorScheme.primary)
            Column { Text(title, style = MaterialTheme.typography.labelLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
