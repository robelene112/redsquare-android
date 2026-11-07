package nl.davidengelen.redsquare.stack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.DesignButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable
internal fun StackChoices(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.stack_everyone, R.string.stack_restricted, R.string.stack_subscribers)
    val details = listOf(R.string.stack_everyone_detail, R.string.stack_restricted_detail, R.string.stack_subscribers_detail)
    Column(Modifier.selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .selectable(selected == index, role = Role.RadioButton, onClick = { onSelect(index) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == index, onClick = null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(details[index]), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun StackToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun StackPair(label: String, value: String) {
    // Stack at large text rather than truncating the value at the screen edge.
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

internal fun LazyListScope.accessContent(page: StackPage, access: Boolean, navigate: (StackPage) -> Unit, confirm: () -> Unit) {
    when (page) {
        StackPage.ACCESS -> {
            item { StackIntro(stringResource(R.string.stack_access_hero), stringResource(R.string.stack_access_body), stringResource(R.string.stack_support_eyebrow)) }
            item { StackPair(stringResource(R.string.stack_access), stringResource(R.string.stack_monthly)) }
            item { StackPair(stringResource(R.string.stack_price_label), stringResource(R.string.stack_sample_price)) }
            item { Text(stringResource(R.string.stack_access_explanation), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { DesignButton({ navigate(StackPage.ACCESS_REVIEW) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_access_review)) } }
        }
        StackPage.ACCESS_REVIEW -> {
            item { StackIntro(stringResource(R.string.stack_sample_price), stringResource(R.string.stack_access_body), stringResource(R.string.stack_preview_purchase)) }
            item { Text(stringResource(R.string.stack_no_payment), style = MaterialTheme.typography.bodyMedium) }
            item { Text(stringResource(R.string.stack_real_wallet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { DesignButton(confirm, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_confirm_access)) } }
        }
        StackPage.SUBSCRIPTIONS -> {
            item { StackSection(stringResource(R.string.stack_reading_access)) }
            item { StackMenuRow(stringResource(R.string.stack_author_name), stringResource(if (access) R.string.stack_preview_access_active
                else R.string.stack_preview_access_available), R.drawable.ic_bookmark) { navigate(StackPage.SUBSCRIPTION) } }
            item { StackSection(stringResource(R.string.stack_your_publishing)) }
            item { StackMenuRow(stringResource(R.string.stack_subscriber_access), stringResource(R.string.stack_configure_access),
                R.drawable.ic_article) { navigate(StackPage.AUTHOR_ACCESS) } }
        }
        StackPage.SUBSCRIPTION -> {
            item { StackIntro(stringResource(R.string.stack_author_name), stringResource(if (access) R.string.stack_preview_access_active
                else R.string.stack_preview_access_available), stringResource(R.string.stack_sample_subscription)) }
            item { StackMenuRow(stringResource(R.string.stack_view_author), stringResource(R.string.stack_latest_writing), R.drawable.ic_profile) { navigate(StackPage.AUTHOR) } }
            item { StackMenuRow(stringResource(R.string.stack_manage_access), stringResource(R.string.stack_review_options), R.drawable.ic_bookmark) { navigate(StackPage.ACCESS) } }
        }
        StackPage.AUTHOR_ACCESS -> item { AuthorAccessPreview() }
        else -> error("Not an access page: $page")
    }
}

@Composable
private fun AuthorAccessPreview() {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("10.00") }
    var description by rememberSaveable { mutableStateOf("") }
    var listing by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name, { name = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.stack_access_name)) })
        OutlinedTextField(price, { price = it; saved = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.stack_price)) }, singleLine = true)
        OutlinedTextField(description, { description = it; saved = false }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.stack_readers_receive)) })
        StackToggle(stringResource(R.string.stack_store_listing), stringResource(R.string.stack_store_listing_detail), listing) { listing = it; saved = false }
        if (saved) InlineStatus(stringResource(R.string.stack_preview_settings_saved), StatusTone.SUCCESS)
        DesignButton({ saved = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_save_access)) }
    }
}
