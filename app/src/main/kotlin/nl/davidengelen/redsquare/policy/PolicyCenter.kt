package nl.davidengelen.redsquare.policy

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import nl.davidengelen.redsquare.R

private data class PolicySection(@StringRes val title: Int, @StringRes val text: Int)

/** Bundled drafts only. There is no acceptance, request submission or remote URL here. */
private enum class PolicyDocument(@StringRes val title: Int, @StringRes val summary: Int,
    val sections: List<PolicySection>, @StringRes val pending: Int, @StringRes val pendingText: Int) {
    TERMS(R.string.policy_terms, R.string.policy_terms_summary, listOf(
        PolicySection(R.string.policy_terms_service, R.string.policy_terms_service_text),
        PolicySection(R.string.policy_terms_identity, R.string.policy_terms_identity_text),
        PolicySection(R.string.policy_terms_content, R.string.policy_terms_content_text)),
        R.string.policy_terms_pending, R.string.policy_terms_pending_text),
    PRIVACY(R.string.policy_privacy, R.string.policy_privacy_summary, listOf(
        PolicySection(R.string.policy_privacy_device, R.string.policy_privacy_device_text),
        PolicySection(R.string.policy_privacy_network, R.string.policy_privacy_network_text),
        PolicySection(R.string.policy_privacy_providers, R.string.policy_privacy_providers_text)),
        R.string.policy_privacy_pending, R.string.policy_privacy_pending_text),
    COMMUNITY(R.string.policy_community, R.string.policy_community_summary, listOf(
        PolicySection(R.string.policy_community_conduct, R.string.policy_community_conduct_text),
        PolicySection(R.string.policy_community_controls, R.string.policy_community_controls_text),
        PolicySection(R.string.policy_community_reports, R.string.policy_community_reports_text)),
        R.string.policy_community_pending, R.string.policy_community_pending_text),
    SAFETY(R.string.policy_safety, R.string.policy_safety_summary, listOf(
        PolicySection(R.string.policy_safety_standard, R.string.policy_safety_standard_text),
        PolicySection(R.string.policy_safety_reporting, R.string.policy_safety_reporting_text)),
        R.string.policy_safety_pending, R.string.policy_safety_pending_text),
    DELETION(R.string.policy_deletion, R.string.policy_deletion_summary, listOf(
        PolicySection(R.string.policy_deletion_wallet, R.string.policy_deletion_wallet_text),
        PolicySection(R.string.policy_deletion_network, R.string.policy_deletion_network_text),
        PolicySection(R.string.policy_deletion_requests, R.string.policy_deletion_requests_text)),
        R.string.policy_deletion_pending, R.string.policy_deletion_pending_text),
    CONTACT(R.string.policy_contact, R.string.policy_contact_summary, listOf(
        PolicySection(R.string.policy_contact_publisher, R.string.policy_contact_publisher_text),
        PolicySection(R.string.policy_contact_details, R.string.policy_contact_details_text)),
        R.string.policy_contact_pending, R.string.policy_contact_pending_text),
}

@Composable
internal fun PolicyCenter(onClose: () -> Unit) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var contactName by rememberSaveable { mutableStateOf<String?>(null) }
    var requestsOpen by rememberSaveable { mutableStateOf(false) }
    val selected = PolicyDocument.entries.find { it.name == selectedName }
    fun back() { if (selected != null) selectedName = null else onClose() }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            // Dialogs own a separate Window; Activity system-bar appearance does not carry over.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (selected != null) IconButton(onClick = { selectedName = null }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.policy_back))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(stringResource(selected?.title ?: R.string.policy_center),
                            Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.policy_draft_label),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.policy_close))
                    }
                }
                HorizontalDivider()
                Crossfade(selected, animationSpec = tween(160), label = "Policy document") { document ->
                    // Each document has its own scroll position; its name restores across recreation.
                    key(document) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.policy_draft), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.policy_draft_notice), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (document == null) {
                                PublisherContactPurpose.entries.forEach { purpose ->
                                    OutlinedCard(onClick = { contactName = purpose.name }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(purpose.entry), style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(purpose.summary), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                OutlinedCard(onClick = { requestsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.publisher_requests_entry), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.publisher_requests_summary), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                PolicyDocument.entries.forEach { entry ->
                                    OutlinedCard(onClick = { selectedName = entry.name }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(entry.summary), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            } else SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    document.sections.forEach { section -> PolicyText(section.title, section.text) }
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                                        shape = MaterialTheme.shapes.medium) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                            PolicyText(document.pending, document.pendingText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        PublisherContactPurpose.entries.find { it.name == contactName }?.let { purpose ->
            PublisherEmailDialog(purpose) { contactName = null }
        }
        if (requestsOpen) PublisherRequestsDialog { requestsOpen = false }
    }
}

@Composable
private fun PolicyText(@StringRes title: Int, @StringRes text: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
