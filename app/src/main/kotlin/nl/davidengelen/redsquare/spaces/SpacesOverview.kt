package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing

internal enum class SpacesTab { MY_SPACES, DISCOVER, ACTIVITY }

/** The approved overview design, populated only from this account's real state. */
@Composable
internal fun SpacesOverview(account: SpacesAccount, state: SpacesAccount.State, tab: SpacesTab,
    onOpen: (String) -> Unit, onCreate: () -> Unit, onInvitation: () -> Unit,
    onActivity: (SpaceActivityGroup) -> Unit,
    modifier: Modifier = Modifier, status: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.directoryQuery) }
    LaunchedEffect(tab, query, state.ready) {
        if (tab == SpacesTab.DISCOVER && state.ready) { delay(250); account.discover(query) }
    }
    val memberships = state.communities.filter { it.joined }.map { it.id }
    var activityCursor by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    var activity by remember(account, memberships, activityCursor, state.accessRevision) { mutableStateOf<SpaceActivityPage?>(null) }
    var activityFailed by remember(account, activityCursor) { mutableStateOf(false) }
    var activityRetry by remember(account, activityCursor) { mutableIntStateOf(0) }
    LaunchedEffect(tab, account, memberships, activityCursor, activityRetry, state.ready, state.refreshing, state.accessRevision) {
        if (tab == SpacesTab.ACTIVITY && state.ready && !state.refreshing) {
            activityFailed = false
            try { activity = account.activityPage(activityCursor, state.accessRevision) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { activityFailed = true }
        }
    }
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(when (tab) {
                SpacesTab.MY_SPACES -> R.string.spaces_home_title
                SpacesTab.DISCOVER -> R.string.spaces_discover_title
                SpacesTab.ACTIVITY -> R.string.spaces_activity_title
            }), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        item { status() }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        when (tab) {
            SpacesTab.MY_SPACES -> {
                if (state.communities.isNotEmpty()) item { SectionLabel(stringResource(R.string.spaces_your_communities)) }
                items(state.communities, key = { it.id }) { community ->
                    val profile = state.profiles[community.id] ?: SpaceProfile(community.name)
                    SpaceCommunityRow(profile, community.id, account.avatarImages,
                        summary = stringResource(when {
                            community.dissolved -> R.string.spaces_dissolved_saved_history
                            !community.joined -> R.string.spaces_confirming_access
                            else -> R.string.spaces_open_saved_channel
                        }),
                        hasUnread = community.hasUnread, personalUnread = community.personalUnread, enabled = !state.savingNavigation, onClick = { onOpen(community.id) })
                }
                item {
                    OutlinedButton(onClick = onCreate, enabled = state.ready && !state.loading && !state.working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(painterResource(R.drawable.ic_compose), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.spaces_create))
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff452b38), Color(0xff233842))))
                            .padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.spaces_hero_eyebrow), style = MaterialTheme.typography.labelMedium, color = Color(0xffffb6a4))
                            Text(stringResource(R.string.spaces_hero_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text(stringResource(R.string.spaces_hero_body), style = MaterialTheme.typography.bodyMedium, color = Color(0xffe1d9dd))
                            TextButton(onClick = onCreate, enabled = state.ready && !state.working,
                                contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.spaces_hero_action), color = Color(0xffffb6c1))
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = onInvitation, enabled = state.ready && !state.working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_join_invitation)) }
                }
            }
            SpacesTab.DISCOVER -> {
                item {
                    OutlinedTextField(query, { query = it.take(128) }, Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.spaces_find_community)) }, singleLine = true,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp))
                }
                val pendingQuery = query.trim() != state.directoryQuery || !state.directoryLoaded
                if (state.directoryLoading || (pendingQuery && !state.directoryFailed && state.discoverySupported != false))
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.directoryCached) item { Text(stringResource(R.string.spaces_cached_directory),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.discoverySupported == false) item { OverviewNotice(R.string.spaces_discovery_unavailable, R.drawable.ic_globe) }
                if (state.directoryFailed) item {
                    OverviewNotice(R.string.spaces_directory_failed, R.drawable.ic_globe)
                    TextButton(onClick = { account.discover(query) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_retry)) }
                }
                items(state.directoryItems, key = { it.id }) { community ->
                    SpaceCommunityRow(community.profile, community.id, account.avatarImages,
                        summary = community.profile.description.ifBlank { stringResource(R.string.spaces_public_readable) },
                        enabled = !state.working, onClick = { account.openPublic(community.id) })
                }
                if (state.directoryItems.isEmpty() && !pendingQuery && !state.directoryLoading && !state.directoryFailed && state.discoverySupported == true)
                    item { OverviewNotice(R.string.spaces_no_public_results, R.drawable.ic_search) }
                if (state.directoryHasMore) item {
                    OutlinedButton(onClick = { account.discover(query, more = true) }, enabled = !state.directoryLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_more_communities)) }
                }
                item { Text(stringResource(R.string.spaces_public_listing_detail), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SpacesTab.ACTIVITY -> {
                if (activityCursor != null) item {
                    TextButton(onClick = { activityCursor = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_activity_latest))
                    }
                }
                if (activityFailed) item {
                    OverviewNotice(R.string.spaces_activity_failed, R.drawable.ic_notifications)
                    TextButton(onClick = { activityRetry++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_retry))
                    }
                }
                val page = activity
                if (page == null && state.ready && !activityFailed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (page?.groups?.isEmpty() == true && page.next == null) item {
                    OverviewNotice(R.string.spaces_no_personal_activity, R.drawable.ic_notifications)
                }
                if (page != null) {
                    items(page.groups, key = { it.id }) { group ->
                        SpaceActivityCard(group, state.profiles[group.target.community.spaceId]?.name,
                            enabled = !state.working && !state.savingNavigation, onClick = { onActivity(group) })
                    }
                    page.next?.let { next -> item {
                        OutlinedButton(onClick = { activityCursor = next }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.spaces_activity_older))
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun SpaceActivityCard(group: SpaceActivityGroup, space: String?, enabled: Boolean, onClick: () -> Unit) {
    val actor = registeredIdentity(group.latestActor).name
    val title = when (group.kind) {
        SpaceActivityKind.MENTION -> stringResource(R.string.spaces_activity_mention, actor)
        SpaceActivityKind.REPLY -> if (group.actorCount == 1L) stringResource(R.string.spaces_activity_reply, actor)
            else stringResource(R.string.spaces_activity_replies, actor, group.actorCount - 1)
        SpaceActivityKind.REACTION -> if (group.actorCount == 1L) stringResource(R.string.spaces_activity_reaction, actor)
            else stringResource(R.string.spaces_activity_reactions, actor, group.actorCount - 1)
        SpaceActivityKind.EVERYONE -> stringResource(R.string.spaces_activity_everyone, actor)
        SpaceActivityKind.MESSAGE -> stringResource(R.string.spaces_activity_message, actor)
    }
    val time = android.text.format.DateUtils.getRelativeTimeSpanString(group.acceptedAt)
    val unread = stringResource(R.string.spaces_activity_unread)
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            if (group.unread) stateDescription = unread
        }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_notifications), null, Modifier.size(22.dp),
                tint = if (group.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(space, time.toString()).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun SpaceCommunityRow(profile: SpaceProfile, id: String, images: SpaceAvatarImages,
    summary: String, hasUnread: Boolean? = null, personalUnread: Long? = null, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.spaces_open_community, profile.name)
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = openLabel }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SpaceAvatar(profile, id, images, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SpaceUnreadBadge(personalUnread, hasUnread)
            Icon(painterResource(R.drawable.ic_forward), null, Modifier.size(18.dp))
        }
    }
}

@Composable internal fun SpaceAvatar(profile: SpaceProfile, id: String, images: SpaceAvatarImages, modifier: Modifier = Modifier) {
    val source = profile.image
    var image by remember(source, id, images) { mutableStateOf(source?.let { images.loaded(id, it) }) }
    LaunchedEffect(source, id, images) {
        if (source != null && image == null) try { image = images.load(id, source) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { image = null }
    }
    val palette = listOf(Color(0xffa35b43), Color(0xff487e82), Color(0xff675583), Color(0xff836341))
    val color = palette[id.hashCode().ushr(1) % palette.size]
    Box(modifier.clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        val raster = image
        if (raster != null) Image(raster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val initials = remember(profile.name) { profile.name.trim().split(Regex("\\s+")).take(2).joinToString("") { part ->
                part.codePoints().limit(1).toArray().let { String(it, 0, it.size) }
            } }
            Text(initials, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun SpacesBottomBar(tab: SpacesTab, onSelect: (SpacesTab) -> Unit) {
    val entries = listOf(Triple(SpacesTab.MY_SPACES, R.string.spaces_my_spaces, R.drawable.ic_home),
        Triple(SpacesTab.DISCOVER, R.string.spaces_discover_title, R.drawable.ic_globe),
        Triple(SpacesTab.ACTIVITY, R.string.spaces_activity_title, R.drawable.ic_notifications))
    if (LocalDensity.current.fontScale <= 1.3f) NavigationBar {
        entries.forEach { (target, label, icon) -> NavigationBarItem(selected = tab == target, onClick = { onSelect(target) },
            icon = { Icon(painterResource(icon), null, Modifier.size(24.dp)) }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) }) }
    } else Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth()) {
            entries.forEach { (target, label, icon) ->
                Column(Modifier.weight(1f).selectable(selected = tab == target, role = Role.Tab, onClick = { onSelect(target) })
                    .padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(24.dp), color = if (tab == target) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Box(Modifier.size(56.dp, 32.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(24.dp)) }
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.TopCenter) {
                        Text(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable private fun SectionLabel(value: String) { Text(value.uppercase(), style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() }) }

@Composable private fun OverviewNotice(text: Int, icon: Int) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun SpaceUnreadBadge(personal: Long?, ordinary: Boolean?) {
    if (personal != null && personal > 0) {
        val label = stringResource(R.string.spaces_personal_unread_count, personal)
        Badge(modifier = Modifier.semantics { contentDescription = label }) {
            Text(if (personal > 99) "99+" else personal.toString())
        }
    } else if (ordinary == true) {
        val label = stringResource(R.string.spaces_unread_activity)
        Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = label })
    }
}
