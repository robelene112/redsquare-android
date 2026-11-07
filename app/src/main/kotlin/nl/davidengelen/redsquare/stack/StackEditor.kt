package nl.davidengelen.redsquare.stack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.ui.material3.RichText
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.DesignButton
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.SingleOwnerReveal
import java.io.Serializable

/** Disposable UI-review draft, scoped by MainActivity's real wallet/backend/module saveable holder. */
internal data class StackDraft(val title: String, val subtitle: String, val body: String,
    val sourceMode: Boolean = false, val cover: Boolean = false, val audience: Int = 0,
    val price: String = "10.00", val readers: String = "", val share: Boolean = false,
    val post: String = "") : Serializable

/** Flush at navigation/save as well as during typing, so a final edit cannot race disposal. */
internal class StackVisualBuffer {
    var rich: RichTextState? = null
    var baseline: String = ""
    fun snapshot(draft: StackDraft): StackDraft {
        if (draft.sourceMode || sourceOnly(draft.body)) return draft
        val markdown = rich?.toMarkdown() ?: return draft
        return if (markdown != baseline) draft.copy(body = markdown) else draft
    }
    val saver = Saver<StackDraft, StackDraft>(save = { snapshot(it) }, restore = { it })
}

private fun sourceOnly(source: String): Boolean = source.contains(Regex(
    "(?m)^\\s*\\|.*\\|\\s*$|<[/a-zA-Z][^>]*>|^\\[\\^[^]]+]:|^ {0,3}\\|? *:?-+:? *(?:\\| *:?-+:? *)+\\|? *$"))

internal fun LazyListScope.editorContent(draft: StackDraft, visual: StackVisualBuffer, onChange: (StackDraft) -> Unit, onReview: () -> Unit) {
    item { OutlinedTextField(draft.title, { onChange(draft.copy(title = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stack_title_field)) }, singleLine = true) }
    item { OutlinedTextField(draft.subtitle, { onChange(draft.copy(subtitle = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stack_subtitle_field)) }, singleLine = true) }
    item { CoverPicker(draft.cover) { onChange(draft.copy(cover = it)) } }
    item { StackArticleEditor(draft, visual, onChange) }
    item { DesignButton(onReview, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_review_article)) } }
}

@Composable
private fun CoverPicker(selected: Boolean, onSelect: (Boolean) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    StackMenuRow(stringResource(R.string.stack_cover), stringResource(if (selected) R.string.stack_cover_selected else R.string.stack_choose_cover),
        R.drawable.ic_image) { choosing = true }
    if (selected) StackCover(editorial = true, height = 128.dp)
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.stack_sample_cover)) },
        text = { StackCover(editorial = true, height = 160.dp) },
        confirmButton = { TextButton(onClick = { onSelect(true); choosing = false }) { Text(stringResource(R.string.stack_use_cover)) } },
        dismissButton = { TextButton(onClick = { onSelect(false); choosing = false }) { Text(stringResource(R.string.stack_no_cover)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StackArticleEditor(draft: StackDraft, visual: StackVisualBuffer, onChange: (StackDraft) -> Unit) {
    val rich = remember { RichTextState().setMarkdown(draft.body).also {
        visual.rich = it; visual.baseline = it.toMarkdown()
    } }
    var baseline by remember { mutableStateOf(rich.toMarkdown()) }
    // Source remains canonical. A library round-trip must not normalise an untouched draft.
    val currentChange by rememberUpdatedState(onChange)
    val currentDraft by rememberUpdatedState(draft)
    LaunchedEffect(rich) {
        snapshotFlow { rich.toMarkdown() }.collect { markdown ->
            if (markdown != baseline) { baseline = markdown; currentChange(currentDraft.copy(body = markdown)) }
        }
    }
    val unsupported = sourceOnly(draft.body)
    val sourceMode = draft.sourceMode || unsupported
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var formatting by remember { mutableStateOf(false) }
    var imagePreview by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val tabs: @Composable () -> Unit = {
            FilterChip(selected = !sourceMode, enabled = !unsupported, onClick = {
                rich.setMarkdown(draft.body); baseline = rich.toMarkdown(); visual.baseline = baseline
                onChange(draft.copy(sourceMode = false))
            }, label = { Text(stringResource(R.string.stack_visual)) })
            FilterChip(selected = sourceMode, onClick = {
                val markdown = rich.toMarkdown()
                onChange(draft.copy(sourceMode = true, body = if (!sourceMode && markdown != baseline) markdown else draft.body))
                baseline = markdown
            }, label = { Text(stringResource(R.string.stack_markdown)) })
        }
        val copy: @Composable () -> Unit = {
            FrameIconButton(if (copied) R.drawable.ic_check else R.drawable.ic_copy,
                stringResource(if (copied) R.string.stack_copied else R.string.stack_copy_markdown)) {
                clipboard.setText(AnnotatedString(visual.snapshot(draft).body)); copied = true
            }
        }
        if (LocalDensity.current.fontScale > 1.3f) FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) { tabs(); copy() }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs(); Spacer(Modifier.weight(1f)); copy()
        }
        LaunchedEffect(draft.body) { copied = false }
        if (unsupported) Text(stringResource(R.string.stack_keep_source), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleOwnerReveal(sourceMode) {
            if (sourceMode) OutlinedTextField(draft.body, { onChange(draft.copy(body = it)); copied = false },
                Modifier.fillMaxWidth(), minLines = 14, label = { Text(stringResource(R.string.stack_markdown_source)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            else Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val bold = stringResource(R.string.stack_bold)
                    IconButton(onClick = { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                        modifier = Modifier.semantics { contentDescription = bold }) { Text("B", fontWeight = FontWeight.Bold) }
                    val italic = stringResource(R.string.stack_italic)
                    IconButton(onClick = { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                        modifier = Modifier.semantics { contentDescription = italic }) { Text("I", fontStyle = FontStyle.Italic) }
                    FrameIconButton(R.drawable.ic_link, stringResource(R.string.stack_insert_link)) { rich.addLink("Link text", "https://saito.io") }
                    Box {
                        FrameIconButton(R.drawable.ic_more, stringResource(R.string.stack_more_formatting)) { formatting = true }
                        DropdownMenu(expanded = formatting, onDismissRequest = { formatting = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.stack_heading)) },
                                onClick = { formatting = false; rich.insertMarkdownAfterSelection("\n## Heading\n") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.stack_inline_image)) },
                                onClick = { formatting = false; imagePreview = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.stack_bullets)) },
                                onClick = { formatting = false; rich.toggleUnorderedList() })
                        }
                    }
                }
                val bodyLabel = stringResource(R.string.stack_article_body)
                RichTextEditor(rich, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).semantics { contentDescription = bodyLabel },
                    colors = RichTextEditorDefaults.richTextEditorColors(textColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp), textStyle = MaterialTheme.typography.bodyLarge, minLines = 12,
                    placeholder = { Text(stringResource(R.string.stack_story_placeholder)) })
            }
        }
        Text(stringResource(R.string.stack_word_count, draft.body.split(Regex("\\s+")).count { it.isNotBlank() }),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.stack_draft_retained), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (imagePreview) AlertDialog(onDismissRequest = { imagePreview = false },
        title = { Text(stringResource(R.string.stack_inline_image)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StackCover(editorial = true, height = 160.dp)
            Text(stringResource(R.string.stack_inline_image_detail), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { imagePreview = false }) { Text(stringResource(R.string.done)) } })
}

@Composable
internal fun StackMarkdown(source: String, reader: Boolean = false) {
    val rich = remember(source) { RichTextState().setMarkdown(source) }
    SelectionContainer {
        RichText(rich, style = if (reader) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif,
            fontSize = 18.sp, lineHeight = 29.7.sp) else MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun StackDraftCard(draft: StackDraft, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stack_published_preview), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(draft.title, style = MaterialTheme.typography.titleLarge)
            Text(draft.subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun LazyListScope.publicationContent(draft: StackDraft, onChange: (StackDraft) -> Unit, onPublish: () -> Unit) {
    item { StackIntro(draft.title, draft.subtitle) }
    item { StackMarkdown(draft.body) }
    if (draft.cover) item { StackCover(editorial = true, height = 160.dp) }
    item { StackSection(stringResource(R.string.stack_who_reads)) }
    item {
        StackChoices(draft.audience) { onChange(draft.copy(audience = it)) }
    }
    if (draft.audience == 2) item { OutlinedTextField(draft.price, { onChange(draft.copy(price = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stack_price)) }, singleLine = true) }
    if (draft.audience == 1) item { OutlinedTextField(draft.readers, { onChange(draft.copy(readers = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stack_readers)) }) }
    item { StackToggle(stringResource(R.string.stack_share_redsquare), stringResource(R.string.stack_share_detail), draft.share) { onChange(draft.copy(share = it)) } }
    if (draft.share) item { OutlinedTextField(draft.post, { onChange(draft.copy(post = it)) }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.stack_accompanying_post)) }, minLines = 3) }
    item { Text(stringResource(R.string.stack_no_publication), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
    item { DesignButton(onPublish, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_publish_preview)) } }
}
