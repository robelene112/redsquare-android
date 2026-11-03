package nl.davidengelen.redsquare.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.json.JSONObject

@Composable
internal fun ShellBlock(b: JSONObject, d: Design, s: ShellSession, action: (String) -> Unit) {
    if (b.has("whenFlag") && s.flags[b.text("whenFlag")] != true) return
    if (b.has("whenField")) { val condition = b.getJSONObject("whenField"); if (s.fields[condition.text("id")] != condition.text("value")) return }
    when (b.text("type")) {
        "intro" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (b.has("eyebrow")) Text(b.text("eyebrow").uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp)
            Text(b.text("title"), style = MaterialTheme.typography.headlineLarge)
            if (b.has("body")) Text(b.text("body"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "section" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(b.text("title"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (b.has("action")) TextButton(onClick = { action(b.text("action")) }) { Text(b.text("label", "See all")) }
        }
        "text" -> SelectionContainer { Text(b.text("body"), style = if (b.text("style") == "caption") MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, color = if (b.text("style") == "caption") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }
        "banner" -> StateBanner(b.text("title"), b.text("body"), b.text("icon", "info"))
        "tabs" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            b.array("items").objects().forEach { item ->
                FilterChip(selected = item.optBoolean("selected"), onClick = { action(item.text("action")) }, label = { Text(item.text("label")) })
            }
        }
        "post" -> PostCard(b, d, s, action)
        "article" -> ArticleCard(b, d, action, s.flags["article-access"] == true)
        "profile" -> ProfileBlock(b, d, s, action)
        "row" -> MenuRow(b.text("title"), b.text("body"), b.text("icon", "article")) { action(b.text("action")) }
        "rule" -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        "button" -> {
            if (b.text("kind") == "secondary") OutlinedButton(onClick = { action(b.text("action")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(b.text("label"), Modifier.padding(vertical = 6.dp)) }
            else Button(onClick = { action(b.text("action")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(b.text("label"), Modifier.padding(vertical = 6.dp)) }
        }
        "field" -> {
            val id = b.text("id"); val value = s.fields.getOrPut(id) { b.text("value") }
            OutlinedTextField(value, { s.fields[id] = it }, Modifier.fillMaxWidth(), label = { Text(b.text("label")) },
                placeholder = { Text(b.text("placeholder")) }, minLines = b.optInt("lines", 1).coerceIn(1,12), maxLines = if (b.optInt("lines", 1) <= 1) 1 else 12,
                singleLine = b.optInt("lines", 1) <= 1, shape = RoundedCornerShape(14.dp))
        }
        "toggle" -> Row(Modifier.fillMaxWidth().clickable { s.flags[b.text("id")] = !(s.flags[b.text("id")] ?: b.optBoolean("value")) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(b.text("label"), style = MaterialTheme.typography.titleSmall); if (b.has("body")) Text(b.text("body"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = s.flags[b.text("id")] ?: b.optBoolean("value"), onCheckedChange = { s.flags[b.text("id")] = it })
        }
        "choices" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (b.has("label")) Text(b.text("label"), style = MaterialTheme.typography.titleMedium)
            b.array("items").objects().forEach { choice ->
                val value = s.fields[b.text("id")] ?: b.text("value")
                Row(Modifier.fillMaxWidth().clickable {
                    s.fields[b.text("id")] = choice.text("value")
                    if (b.text("id") == "stateMode") s.stateMode = choice.text("value")
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = value == choice.text("value"), onClick = {
                        s.fields[b.text("id")] = choice.text("value")
                        if (b.text("id") == "stateMode") s.stateMode = choice.text("value")
                    })
                    Column(Modifier.weight(1f)) { Text(choice.text("label"), style = MaterialTheme.typography.bodyMedium); if (choice.has("body")) Text(choice.text("body"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        "person" -> Row(Modifier.fillMaxWidth().clickable { action(b.text("action", "author")) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(b.text("name")); Column(Modifier.weight(1f)) { Text(b.text("name"), style = MaterialTheme.typography.titleMedium); Text(b.text("body"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (b.has("follow")) FollowButton(b.text("follow"), s, action) else ShellIcon("chevron", modifier = Modifier.size(18.dp))
        }
        "activity" -> Row(Modifier.fillMaxWidth().clickable { action(b.text("action", "thread")) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShellIcon(b.text("icon", "like"), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Avatar(b.text("name"), 28); if (b.has("others")) Avatar(b.text("others"), 28) }
                Text(b.text("body"), style = MaterialTheme.typography.bodyMedium)
                Text(b.text("quote"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(b.text("time"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (b.optBoolean("unread")) Box(Modifier.padding(top = 7.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
        "balance" -> Surface(shape = RoundedCornerShape(d.radius.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(b.text("label"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(b.text("amount"), style = MaterialTheme.typography.headlineLarge)
                Text(b.text("body"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { action("receive") }, Modifier.weight(1f)) { Text("Receive") }
                    OutlinedButton(onClick = { action("send") }, Modifier.weight(1f)) { Text("Send") }
                }
            }
        }
        "qr" -> DemoQr(b.text("value"))
        "art" -> CoverArt(b, Modifier.fillMaxWidth().height(b.optInt("height", 180).coerceIn(80, 300).dp).clip(RoundedCornerShape(d.radius.dp)))
        "article-body" -> {
            val rich = rememberRichTextState()
            val body = if (s.flags[b.text("accessKey")] == true) b.text("unlockedBody", b.text("body")) else b.text("body")
            LaunchedEffect(body) { rich.setMarkdown(body) }
            SelectionContainer { RichText(rich, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (d.number("readerSize", 18.0, 14.0, 25.0)).sp, lineHeight = (d.number("readerSize", 18.0, 14.0, 25.0) * 1.65).sp)) }
        }
        "editor" -> ArticleEditor(b, d, s)
        "messages" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(b.text("date", "Today"), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            b.array("items").objects().forEach { message -> ChatBubble(message.text("body"), message.optBoolean("mine"), message.text("time", "10:42")) }
            s.messages[b.text("id")]?.forEach { message -> ChatBubble(message, true, "Now") }
        }
        "locked" -> if (s.flags["article-access"] == true) StateBanner("Access active", "You can read this subscriber article in the preview.", "check") else Surface(shape = RoundedCornerShape(d.radius.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShellIcon("lock", modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Keep reading with a subscription", style = MaterialTheme.typography.titleLarge)
                Text("Support independent writing and get access to Maya's subscriber articles.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { action("access") }, Modifier.fillMaxWidth()) { Text("View access options") }
            }
        }
        "sync" -> StateBanner("Design ${d.revision}", "Changes are checked every two seconds while this preview is open.", "refresh")
        "local-post" -> if (s.flags["posted"] == true) {
            val post = JSONObject().put("id", "my-preview-post").put("name","Alex Morgan").put("handle","alex@saito").put("time","Now").put("body",s.fields["posted-preview"] ?: "").put("likes",0)
            if (s.flags["posted-article"] == true) post.put("article", JSONObject().put("title",s.fields["article-title"] ?: "Your new article").put("author","Alex Morgan").put("summary",s.fields["article-subtitle"] ?: "").put("action","published-article"))
            PostCard(post,d,s,action)
        }
        "local-article" -> if (s.flags["published"] == true) ArticleCard(JSONObject().put("title",s.fields["article-title"] ?: "Your new article").put("author","Alex Morgan").put("summary",s.fields["article-subtitle"] ?: "").put("action","published-article").put("locked",s.fields["published-access"] != "public"),d,action,true)
        "draft-body" -> {
            val rich = rememberRichTextState()
            val raw = s.fields["article-body-draft"] ?: "A new story starts here."
            LaunchedEffect(raw) { rich.setMarkdown(raw) }
            SelectionContainer { RichText(rich, style = MaterialTheme.typography.bodyLarge) }
        }
        "draft-preview" -> {
            Text(s.fields["article-title"] ?: "Your next great story", style = MaterialTheme.typography.headlineMedium)
            Text(s.fields["article-subtitle"] ?: "A fresh perspective, shared on your terms.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "pair" -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(b.text("label"), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(b.text("value"), style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
private fun FollowButton(id: String, s: ShellSession, action: (String) -> Unit) {
    OutlinedButton(onClick = { action("follow:$id") }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(if (s.flags[id] == true) "Following" else "Follow", style = MaterialTheme.typography.labelMedium) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCard(b: JSONObject, d: Design, s: ShellSession, action: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(b.text("name")) { action("author") }
            Column(Modifier.weight(1f).clickable { action("author") }) {
                Text(b.text("name"), style = MaterialTheme.typography.titleSmall)
                Text("${b.text("handle")} · ${b.text("time")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { action("sheet:post") }) { ShellIcon("more", "Post actions") }
        }
        Text(b.text("body"), Modifier.fillMaxWidth().clickable { action(b.text("action", "thread")) }, style = MaterialTheme.typography.bodyLarge)
        if (b.has("article")) ArticleCard(b.getJSONObject("article"), d, action, s.flags["article-access"] == true)
        val id = b.text("id")
        val count = s.counts[id] ?: 0
        val scale = remember { Animatable(1f) }
        LaunchedEffect(count) { if (count > 0) { scale.animateTo(1.4f, tween(85)); scale.animateTo(1f, tween(120)) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
            TextButton(onClick = { action("like:$id") }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                ShellIcon(if (count > 0) "like_filled" else "like", "Like post", Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, tint = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${b.optInt("likes") + count}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
            }
            TextButton(onClick = { action("thread") }, contentPadding = PaddingValues(horizontal = 4.dp)) { ShellIcon("chat", "Reply"); Text(b.text("replies", "Reply"), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = { s.dialog = "Repost this post?" }) { ShellIcon("repost", "Repost") }
            IconButton(onClick = { action("quote") }) { ShellIcon("quote", "Quote") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ArticleCard(b: JSONObject, d: Design, action: (String) -> Unit, unlocked: Boolean = false) {
    Surface(onClick = { action(b.text("action", "article")) }, shape = RoundedCornerShape(d.radius.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            if (b.optBoolean("cover", true)) CoverArt(b, Modifier.fillMaxWidth().height(b.optInt("height", 164).coerceIn(80, 260).dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ShellIcon(if (b.optBoolean("locked")) { if (unlocked) "check" else "lock" } else "article", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (b.optBoolean("locked")) { if (unlocked) "STACK · ACCESS ACTIVE" else "STACK · SUBSCRIBERS" } else "SAITO STACK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                }
                Text(b.text("title"), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (b.has("summary")) Text(b.text("summary"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${b.text("author")} · ${b.text("reading", "5 min read")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileBlock(b: JSONObject, d: Design, s: ShellSession, action: (String) -> Unit) {
    val profileName = if (b.optBoolean("own") && s.flags["profile-saved"] == true) s.fields["profile-name"] ?: b.text("name") else b.text("name")
    val profileBody = if (b.optBoolean("own") && s.flags["profile-saved"] == true) s.fields["profile-bio"] ?: b.text("body") else b.text("body")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CoverArt(b, Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(d.radius.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(profileName, 64); Spacer(Modifier.weight(1f)); if (b.optBoolean("own")) OutlinedButton(onClick = { action("edit-profile") }) { Text("Edit profile") } else FollowButton(b.text("name"), s, action) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(profileName, style = MaterialTheme.typography.headlineSmall); Text(b.text("handle"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Text(profileBody, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { Text("128 following", style = MaterialTheme.typography.labelMedium); Text("1.2K followers", style = MaterialTheme.typography.labelMedium) }
        if (!b.optBoolean("own")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { action("conversation") }) { Text("Message") }
            FilledTonalButton(onClick = { action("stack-author") }) { Text("Read on Stack") }
            IconButton(onClick = { action("sheet:post") }) { ShellIcon("more", "Profile actions") }
        }
    }
}

@Composable
internal fun CoverArt(b: JSONObject, modifier: Modifier = Modifier) {
    if (b.text("image").startsWith("https://")) {
        RemoteCover(b.text("image"), modifier, b.text("title", "Article cover"))
        return
    }
    val warm = b.text("art", "network") == "editorial"
    val primary = if (warm) Color(0xffe9ac80) else Color(0xff9cc8f0)
    val background = if (warm) listOf(Color(0xff332329), Color(0xff7b443f)) else listOf(Color(0xff102934), Color(0xff264d5d))
    Box(modifier.background(Brush.linearGradient(background))) {
        Canvas(Modifier.fillMaxSize()) {
            val points = listOf(Offset(size.width * .16f, size.height * .70f), Offset(size.width * .37f, size.height * .25f), Offset(size.width * .57f, size.height * .64f), Offset(size.width * .78f, size.height * .26f), Offset(size.width * .90f, size.height * .78f))
            points.zipWithNext().forEach { (a, z) -> drawLine(primary.copy(alpha = .35f), a, z, 2.dp.toPx(), StrokeCap.Round) }
            drawLine(primary.copy(alpha = .22f), points[0], points[3], 1.dp.toPx())
            points.forEachIndexed { i, p -> drawCircle(primary.copy(alpha = .10f), 20.dp.toPx(), p); drawCircle(primary.copy(alpha = .9f), (if (i == 2) 7 else 4).dp.toPx(), p) }
            if (warm) repeat(5) { i -> drawLine(primary.copy(alpha = .09f), Offset(size.width * .1f, size.height * (.2f + i * .13f)), Offset(size.width * .9f, size.height * (.2f + i * .13f)), 1.dp.toPx()) }
        }
        Text(b.text("coverLabel", if (warm) "INDEPENDENT VOICES" else "IDEAS, CONNECTED"), Modifier.align(Alignment.BottomStart).padding(16.dp), color = primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
    }
}

@Composable
private fun ChatBubble(body: String, mine: Boolean, time: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 5.dp, bottomEnd = if (mine) 5.dp else 18.dp),
            color = if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = 290.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (mine) ShellIcon("check", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
