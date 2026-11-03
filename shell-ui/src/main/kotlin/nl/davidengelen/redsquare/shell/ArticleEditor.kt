package nl.davidengelen.redsquare.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import org.json.JSONObject

@Composable
internal fun ArticleEditor(b: JSONObject, d: Design, s: ShellSession) {
    val id = b.text("id")
    val initial = b.text("body", "## A new perspective\n\nTell your story.")
    val rich = remember(id) { s.editors.getOrPut(id) { RichTextState().setMarkdown(s.fields[id] ?: initial) } }
    remember(id) { s.visualBaselines.getOrPut(id) { rich.toMarkdown() } }
    val source = s.fields.getOrPut(id) { initial }
    val unsupported = source.contains(Regex("(?m)^\\s*\\|.*\\|\\s*$|<[/a-zA-Z][^>]*>|^\\[\\^[^]]+]:"))
    val sourceMode = s.flags["source:$id"] == true || unsupported
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(rich) {
        snapshotFlow { rich.toMarkdown() }.collect { markdown ->
            // Import/export normalisation alone must not rewrite untouched source.
            if (markdown != s.visualBaselines[id]) { s.fields[id] = markdown; s.visualBaselines[id] = markdown }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !sourceMode, enabled = !unsupported, onClick = {
                rich.setMarkdown(s.fields[id] ?: initial); s.visualBaselines[id] = rich.toMarkdown(); s.flags["source:$id"] = false
            }, label = { Text("Visual") })
            FilterChip(selected = sourceMode, onClick = { val next = rich.toMarkdown(); if (next != s.visualBaselines[id]) { s.fields[id] = next; s.visualBaselines[id] = next }; s.flags["source:$id"] = true }, label = { Text("Markdown") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(source)); s.notice = "Markdown copied" }) { ShellIcon("copy", "Copy Markdown") }
        }
        if (unsupported) StateBanner("Keep this formatting in Markdown", "This preview keeps tables, custom HTML and footnotes in source mode so they are not lost.", "info")
        if (sourceMode || unsupported) OutlinedTextField(value = source, onValueChange = { s.fields[id] = it }, modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), minLines = 14, label = { Text("Markdown source") })
        else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }, Modifier.semantics { contentDescription = "Bold" }) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }, Modifier.semantics { contentDescription = "Italic" }) { Text("I", fontStyle = FontStyle.Italic) }
                TextButton(onClick = { rich.insertMarkdownAfterSelection("\n## Heading\n") }) { Text("Heading") }
                IconButton(onClick = { rich.addLink("Link text", "https://saito.io") }) { ShellIcon("link", "Insert link") }
                TextButton(onClick = { rich.toggleUnorderedList() }) { Text("List") }
                IconButton(onClick = { s.dialog = "Add an article image" }) { ShellIcon("image", "Insert image") }
            }
            RichTextEditor(rich, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                textStyle = MaterialTheme.typography.bodyLarge, placeholder = { Text("Your story starts here…") }, minLines = 12)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Draft kept while you explore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${source.split(Regex("\\s+")).count { it.isNotBlank() }} words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
