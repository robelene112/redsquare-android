package nl.davidengelen.redsquare.prototype

// Throwaway native UI study for specification #134. All community data is synthetic and in memory.
// Question: do the agreed Spaces hierarchy, density, controls and difficult states work on a phone?
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.text.SpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.ui.material3.RichText
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.Appearance
import nl.davidengelen.redsquare.ui.RedSquareTheme

class SpacesPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var appearance by rememberSaveable { mutableStateOf("DARK") }
            RedSquareTheme(this, Appearance.valueOf(appearance)) { SpacesPrototype(appearance) { appearance = it } }
        }
    }
}
private enum class Page { HOME, DISCOVER, ACTIVITY, CHAT, THREAD, ANNOUNCEMENTS, ARTICLES, ARTICLE, EDITOR, REVIEW,
    FILES, FILE, ROOM, CALL, MEMBERS, INFO, SETTINGS, INVITE, RULES, CREATE, REPORTS, LOG, BACKUP, PREVIEW }
private data class ChatRow(val id: Int, val name: String, val time: String, val body: String, val replies: Int = 0, val own: Boolean = false)
private data class Destination(val page:Page,val space:String,val channel:String,val message:ChatRow,val article:Int,val file:Int)
private data class PreviewMessage(val id:Int,val space:String,val channel:String,val thread:Int?,val text:String) {val scope get()="$space:$channel"+thread?.let{":thread:$it"}.orEmpty()}
private val seeds = listOf(
    ChatRow(1,"Maya Chen","10:24","A small corner of the open web. Welcome to Independent Voices.",3),
    ChatRow(2,"Alex Morgan","10:26","I love that our conversations, articles and shared resources finally live together.",2,true),
    ChatRow(3,"Sam Rivera","10:29","The new reading list is in Articles. What should we explore next?",4),
    ChatRow(4,"Jules Park","10:31","Could we do a community call this evening? The lounge is ready.")
)
private val channels = listOf("general" to Page.CHAT, "announcements" to Page.ANNOUNCEMENTS, "the lounge" to Page.ROOM,
    "reading room" to Page.ARTICLES, "resources" to Page.FILES, "team room" to Page.CHAT)
private fun channelIcon(page: Page) = when(page) { Page.ROOM,Page.CALL -> R.drawable.ic_video; Page.ARTICLES -> R.drawable.ic_article; Page.FILES -> R.drawable.ic_file; else -> R.drawable.ic_chat }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SpacesPrototype(appearance: String, onAppearance: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var space by rememberSaveable { mutableStateOf("Independent Voices") }
    var channel by rememberSaveable { mutableStateOf("general") }
    val communityNames=remember {mutableStateListOf("Independent Voices","Saito Builders","The Commons")}
    var newName by rememberSaveable {mutableStateOf("Your new community")}
    var newDescription by rememberSaveable {mutableStateOf("Independent conversations and shared ideas")}
    var newPrivate by rememberSaveable {mutableStateOf(false)}
    var newListed by rememberSaveable {mutableStateOf(true)}
    var callSpace by rememberSaveable {mutableStateOf("")}
    var callChannel by rememberSaveable {mutableStateOf("the lounge")}
    var role by rememberSaveable { mutableStateOf("Owner") }
    var scenario by rememberSaveable { mutableStateOf("Normal") }
    val memberships=remember {mutableStateMapOf<String,Boolean>()}
    val privateSpaces=remember {mutableStateMapOf<String,Boolean>()}
    val listings=remember {mutableStateMapOf<String,Boolean>()}
    val archives=remember {mutableStateMapOf<String,Boolean>()}
    val member=memberships[space]?:true
    val privateSpace=privateSpaces[space]?:false
    val listed=listings[space]?:true
    val archived=archives["$space:$channel"]?:false
    var activeCall by rememberSaveable { mutableStateOf(false) }
    var mic by rememberSaveable { mutableStateOf(false) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf(false) }
    var selectedArticle by remember {mutableIntStateOf(0)}
    val articleSamples=listOf("# A quieter kind of internet\n\nGood ideas need room to grow.\n\n**Independent voices** make a community worth coming back to.","# Building in the open\n\nSharing what we learn makes everyone stronger.","# Untitled article\n\nStart writing here.")
    val publishedArticles=remember {mutableStateMapOf<String,String>()}
    val articleDrafts=remember {mutableStateMapOf<String,TextFieldValue>()}
    val richDrafts=remember {mutableMapOf<String,RichTextState>()}
    val notificationModes=remember {mutableStateMapOf<String,String>()}
    val groupAlertModes=remember {mutableStateMapOf<String,Boolean>()}
    val articleKey="$space:$selectedArticle"
    val article=articleDrafts[articleKey]?:TextFieldValue(publishedArticles[articleKey]?:articleSamples[selectedArticle])
    var selectedFile by remember { mutableStateOf(1) }
    val lastPlaces=remember{mutableStateMapOf<String,Pair<Page,String>>()}
    val positions=remember { mutableMapOf<String,LazyListState>() }
    var lastSendScope by remember {mutableStateOf("")}
    var sendSequence by remember {mutableIntStateOf(0)}
    val filenames=listOf("Community handbook.pdf","Design resources.zip","Friday reading list.md")
    var selectedMessage by remember { mutableStateOf(seeds.first()) }
    var actionMessage by remember {mutableStateOf(seeds.first())}
    val drafts = remember { mutableStateMapOf<String,String>() }
    val queued = remember { mutableStateListOf<PreviewMessage>() }
    val sent = remember { mutableStateListOf<PreviewMessage>() }
    var nextMessage by remember {mutableIntStateOf(100)}
    val reactions = remember { mutableStateMapOf<String,Boolean>() }
    val removed = remember { mutableStateListOf<String>() }
    val memberTimeouts=remember {mutableStateMapOf<String,String>()}
    val membersRemoved = remember { mutableStateListOf<String>() }
    val removedFiles = remember { mutableStateListOf<String>() }
    val history = remember { mutableStateListOf<Destination>() }
    var modal by remember { mutableStateOf<String?>(null) }
    var editMessage by remember { mutableStateOf<ChatRow?>(null) }
    var editText by remember { mutableStateOf("") }
    val edits = remember { mutableStateMapOf<String,String>() }
    var search by rememberSaveable { mutableStateOf("") }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val coroutine = rememberCoroutineScope()
    val transitionAlpha=remember { Animatable(1f) }
    LaunchedEffect(page) { transitionAlpha.snapTo(0.5f); transitionAlpha.animateTo(1f,tween(150)) }
    LaunchedEffect(role,scenario,memberships[callSpace],archives["$callSpace:$callChannel"]) {
        if(role=="Visitor" || memberships[callSpace]==false || scenario=="Access denied") {activeCall=false;mic=false;camera=false}
        if(scenario=="Timeout" || archives["$callSpace:$callChannel"]==true) {mic=false;camera=false}
    }
    val imeVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val inThisRoom=activeCall && callSpace==space && callChannel==channel
    LaunchedEffect(space,channel,page) {
        val parent=when(page){Page.CHAT->Page.CHAT;Page.THREAD->if(channel=="announcements") Page.ANNOUNCEMENTS else Page.CHAT;Page.ARTICLES,Page.ARTICLE,Page.EDITOR,Page.REVIEW->Page.ARTICLES;Page.FILES,Page.FILE->Page.FILES;Page.ROOM,Page.CALL->Page.ROOM;Page.ANNOUNCEMENTS->Page.ANNOUNCEMENTS;else->null}
        if(parent!=null) lastPlaces[space]=parent to channel
    }
    val admin = role == "Owner" || role == "Admin"
    val outsider = role == "Visitor" || !member
    val denied = scenario == "Access denied" || (channel == "team room" && !admin) || (privateSpace && outsider)
    val scoped = "$space:$channel"
    val draftKey = if(page == Page.THREAD) "$scoped:thread:${selectedMessage.id}" else scoped
    val input = drafts[draftKey].orEmpty()
    val canWrite = !outsider && !denied && !archived && scenario != "Timeout" && (channel!="announcements" || page==Page.THREAD || admin)
    fun go(target:Page,newSpace:String=space,newChannel:String=channel,message:ChatRow=selectedMessage,articleId:Int=selectedArticle,fileId:Int=selectedFile) {
        if(page!=target || space!=newSpace || channel!=newChannel || selectedMessage!=message || selectedArticle!=articleId || selectedFile!=fileId) {
            history.add(Destination(page,space,channel,selectedMessage,selectedArticle,selectedFile))
            page=target;space=newSpace;channel=newChannel;selectedMessage=message;selectedArticle=articleId;selectedFile=fileId
        }
    }
    fun back() { if(history.isNotEmpty()) {val prior=history.removeAt(history.lastIndex);page=prior.page;space=prior.space;channel=prior.channel;selectedMessage=prior.message;selectedArticle=prior.article;selectedFile=prior.file} else page=Page.HOME }
    BackHandler(page != Page.HOME || drawer.isOpen) { if(drawer.isOpen) coroutine.launch { drawer.close() } else back() }
    LaunchedEffect(scenario,role,memberships.toMap(),archives.toMap()) {
        if(scenario == "Normal" && role!="Visitor") {
            val allowed=queued.filter{memberships[it.space]!=false && archives["${it.space}:${it.channel}"]!=true && (it.channel!="team room" || admin) && (it.channel!="announcements" || it.thread!=null || admin)}
            sent.addAll(allowed);queued.removeAll(allowed.toSet())
        }
    }
    ModalNavigationDrawer(drawerState=drawer, drawerContent={
        ModalDrawerSheet(Modifier.widthIn(max=340.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Your communities",style=MaterialTheme.typography.titleLarge)
                communityNames.forEach { name ->
                    CommunityRow(name, if(name == space) "Current Space" else "Open community", name == space) { val place=lastPlaces[name]?: (Page.CHAT to "general");go(place.first,newSpace=name,newChannel=place.second);coroutine.launch{drawer.close()} }
                }
                HorizontalDivider(); Text(space,style=MaterialTheme.typography.titleMedium)
                Text("CONVERSATIONS",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                channels.filter { it.first != "team room" || admin }.forEach { (name,destination) ->
                    Surface(onClick={ go(destination,newChannel=name); coroutine.launch { drawer.close() } },
                        color=if(channel==name) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Glyph(if(name=="team room") R.drawable.ic_lock else channelIcon(destination))
                            Text(name,Modifier.weight(1f)); if(name=="general") Badge { Text("2") }
                        }
                    }
                }
                TextButton(onClick={ go(Page.INFO); coroutine.launch { drawer.close() } }) { Text("Space information") }
                if(admin) OutlinedButton(onClick={ modal="Create channel" }) { Text("Add channel or category") }
            }
        }
    }) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background, topBar={
            Column(Modifier.statusBarsPadding().padding(horizontal=20.dp)) {
                if(imeVisible) Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {Text("Spaces",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={go(Page.PREVIEW)}){Text("Scenarios")}}
                else {
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={modal="Saito apps"},shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface,containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text("Spaces",style=MaterialTheme.typography.titleLarge); Spacer(Modifier.width(10.dp)); Glyph(R.drawable.ic_chevron_down,18)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={go(Page.PREVIEW)}) { Avatar("AM",Color(0xffa35b43),36) }
                }
                Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Native design prototype",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
                    TextButton(onClick={go(Page.PREVIEW)},contentPadding=PaddingValues(horizontal=8.dp)) { Text("Scenarios",style=MaterialTheme.typography.labelSmall) }
                }
                }
            }
        }, bottomBar={
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if(activeCall && page != Page.CALL) Surface(color=MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={go(Page.CALL,newSpace=callSpace,newChannel=callChannel)},modifier=Modifier.weight(1f)) { Glyph(R.drawable.ic_video); Spacer(Modifier.width(8.dp)); Text(if(imeVisible) "Call" else "$callSpace · Preview call") }
                        IconButton(enabled=scenario!="Timeout" && archives["$callSpace:$callChannel"]!=true,onClick={mic=!mic}) { Glyph(if(mic) R.drawable.ic_microphone else R.drawable.ic_microphone_off,label=if(mic) "Mute" else "Unmute") }
                        IconButton(onClick={activeCall=false;mic=false;camera=false}) { Glyph(R.drawable.ic_call_end,label="End preview call") }
                    }
                }
                if((page==Page.CHAT || page==Page.THREAD) && !denied) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value=input,onValueChange={drafts[draftKey]=it},enabled=canWrite,
                            placeholder={Text(if(archived) "Archived channel" else if(scenario=="Timeout") "Writing paused until 14:30" else if(outsider) "Join to contribute" else "Message $channel")},
                            modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),maxLines=if(imeVisible && LocalDensity.current.fontScale>1.3f) 2 else 4)
                        FilledIconButton(enabled=canWrite && input.isNotBlank(),onClick={ val message=PreviewMessage(nextMessage++,space,channel,if(page==Page.THREAD) selectedMessage.id else null,input); if(scenario=="Offline") queued.add(message) else sent.add(message); drafts[draftKey]="";lastSendScope=draftKey;sendSequence++ },modifier=Modifier.size(52.dp)) { Glyph(R.drawable.ic_send,label="Send preview message") }
                    }
                }
                if(WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)==0) NavigationBar(modifier=Modifier.heightIn(min=if(LocalDensity.current.fontScale>1.3f) 112.dp else 80.dp)) {
                    listOf(Triple(Page.HOME,"My Spaces",R.drawable.ic_home),Triple(Page.DISCOVER,"Discover",R.drawable.ic_globe),Triple(Page.ACTIVITY,"Activity",R.drawable.ic_notifications)).forEach { (target,label,icon) ->
                        NavigationBarItem(selected=page==target,onClick={go(target)},icon={Glyph(icon)},label={Text(label,maxLines=2,style=MaterialTheme.typography.labelSmall)})
                    }
                }
            }
        }) { padding ->
            val position=positions.getOrPut("$scoped:$page") { LazyListState() }
            LaunchedEffect(sendSequence) {if(sendSequence>0 && lastSendScope==draftKey) {withFrameNanos{};position.animateScrollToItem((position.layoutInfo.totalItemsCount-1).coerceAtLeast(0))}}
            LazyColumn(state=position,modifier=Modifier.fillMaxSize().padding(padding).alpha(transitionAlpha.value).testTag("prototype-content"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item("heading") {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(page !in listOf(Page.HOME,Page.DISCOVER,Page.ACTIVITY)) IconButton(onClick={back()}) { Glyph(R.drawable.ic_back,label="Back") }
                        Column(Modifier.weight(1f)) {
                            if(!imeVisible && page in listOf(Page.CHAT,Page.ANNOUNCEMENTS,Page.ARTICLES,Page.FILES,Page.ROOM,Page.THREAD)) Text(space,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when(page) { Page.HOME->"Your people. Your spaces."; Page.DISCOVER->"Discover"; Page.CHAT->channel; Page.THREAD->"Replies"; Page.ROOM->"the lounge"; else->page.name.lowercase().replaceFirstChar{it.uppercase()} },style=if(imeVisible) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics{heading()})
                        }
                        if(page in listOf(Page.CHAT,Page.ARTICLES,Page.FILES,Page.ANNOUNCEMENTS,Page.ROOM)) IconButton(onClick={coroutine.launch { drawer.open() }}) { Glyph(R.drawable.ic_read_all,label="Switch Space or channel") }
                        if(page==Page.CHAT) IconButton(onClick={go(Page.INFO)}) { Glyph(R.drawable.ic_info,label="Space information") }
                    }
                }
                if(scenario!="Normal" && page!=Page.PREVIEW) item("scenario") { Notice(when(scenario) { "Loading"->"Preview: loading new content. Saved content stays visible."; "Offline"->"Offline preview · showing saved content"; "Timeout"->"Preview: writing paused in this channel until 14:30"; "Storage full"->"Preview: storage full. New uploads are paused."; "Expired"->"Preview: shared storage has expired."; else->"Preview: access to this channel is unavailable." }) }
                if(denied && page in listOf(Page.CHAT,Page.THREAD,Page.ARTICLES,Page.ARTICLE,Page.EDITOR,Page.REVIEW,Page.FILES,Page.FILE,Page.ROOM,Page.CALL,Page.ANNOUNCEMENTS)) item { Panel("This channel is private","A link does not grant access. Ask an owner or administrator to admit you.",R.drawable.ic_lock); Action("Back to your Spaces") {go(Page.HOME)} }
                else if(!canWrite && page in listOf(Page.EDITOR,Page.REVIEW)) item { Notice("Writing is unavailable in this channel. Reading access is unchanged.") }
                else if(!admin && page in listOf(Page.REPORTS,Page.LOG)) item { Notice("Only the owner and administrators can see reports and the admin log.") }
                else if(scenario=="Empty" && page in listOf(Page.CHAT,Page.THREAD,Page.ARTICLES,Page.FILES,Page.ACTIVITY)) item {Panel("A little room to begin","There is no content here yet. Start a conversation or share something useful.",R.drawable.ic_chat);Action("Show populated preview"){scenario="Normal"}}
                else when(page) {
                    Page.HOME -> {
                        item { Hero("A place to belong.","Conversations with a little more room.\nBuilt around people, not algorithms.") { go(Page.CREATE) } }
                        item { Section("YOUR COMMUNITIES") }
                        items(communityNames.filter{memberships[it]!=false},key={it}) { name -> CommunityRow(name,if(name=="Independent Voices") "2 mentions · general" else "Open your community",name==space,if(name=="Saito Builders") Color(0xff487e82) else Color(0xffa35b43)) {val place=lastPlaces[name]?: (Page.CHAT to "general");go(place.first,newSpace=name,newChannel=place.second)} }
                        item { OutlinedButton(onClick={go(Page.CREATE)},modifier=Modifier.fillMaxWidth()) { Glyph(R.drawable.ic_compose); Spacer(Modifier.width(8.dp));Text("Create a Space") } }
                    }
                    Page.DISCOVER -> {
                        item { OutlinedTextField(search,{search=it},placeholder={Text("Find your community")},leadingIcon={Glyph(R.drawable.ic_search)},modifier=Modifier.fillMaxWidth(),singleLine=true) }
                        items(communityNames.filter{it.contains(search,true) && privateSpaces[it]!=true && listings[it]!=false}) { name ->
                            CommunityRow(name, "Public Space · open conversations",false) {go(Page.INVITE,newSpace=name)}
                        }
                        item { Notice("Public Spaces are discoverable by default. Their owners can turn listing off.") }
                    }
                    Page.ACTIVITY -> {
                        item { Notice("Personal mentions and replies. Ordinary unread activity uses a dot.") }
                        items(listOf("Maya mentioned you in general","Sam replied to your message","3 people reacted to your message")) { label ->
                            Panel(label, "$space · 12 min ago",R.drawable.ic_notifications) {go(Page.THREAD,newChannel="general",message=seeds.first())}
                        }
                    }
                    Page.CHAT,Page.THREAD,Page.ANNOUNCEMENTS -> {
                        if(page==Page.ANNOUNCEMENTS) item { Panel("Community call this Friday","Maya Chen · Announcement\nJoin us in the lounge at 19:00. Everyone is welcome.",R.drawable.ic_bookmark) {go(Page.THREAD,message=ChatRow(20,"Maya Chen","Friday","Community call this Friday. Join us in the lounge at 19:00.",3))} }
                        if(page==Page.CHAT) item { Surface(onClick={go(Page.THREAD,message=ChatRow(20,"Maya Chen","Friday","Community call this Friday. Join us in the lounge at 19:00.",3))},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) { Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {Glyph(R.drawable.ic_bookmark);Text("Pinned · Start here, make yourself at home.",style=MaterialTheme.typography.bodySmall)} } }
                        if(outsider) item { Action("Join this Space") {go(Page.RULES)} }
                        if(page==Page.THREAD) item { Panel(selectedMessage.name,selectedMessage.body,R.drawable.ic_chat) }
                        val rows=if(page==Page.THREAD) listOf(ChatRow(11,"Maya Chen","10:30","Exactly. Good conversations are worth keeping."),ChatRow(12,"Sam Rivera","10:32","Count me in for Friday.")) else if(page==Page.ANNOUNCEMENTS) emptyList() else seeds
                        items(rows,key={"message:${it.id}"}) { row ->
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Avatar(row.name.split(" ").map{it.first()}.joinToString(""),if(row.own) Color(0xffa35b43) else Color(0xff477c7d),36)
                                    Column(Modifier.weight(1f)) {Text(row.name,style=MaterialTheme.typography.titleMedium); Text(row.time+if(edits.containsKey("$scoped:${row.id}")) " · edited" else "",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                    IconButton(onClick={ actionMessage=row; modal="Message actions" }) {Glyph(R.drawable.ic_more,label="Message actions for ${row.name}")}
                                }
                                AnimatedContent(targetState=if(removed.contains("$scoped:${row.id}")) "Message removed" else edits["$scoped:${row.id}"] ?: row.body,transitionSpec={fadeIn(tween(150)) togetherWith fadeOut(tween(100))},label="message-update") { body -> Text(body,style=MaterialTheme.typography.bodyLarge) }
                                if(!removed.contains("$scoped:${row.id}")) Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    OutlinedButton(modifier=Modifier.semantics {contentDescription="Like this message";selected=reactions["$scoped:${row.id}"]==true},enabled=canWrite,onClick={reactions["$scoped:${row.id}"]=reactions["$scoped:${row.id}"]!=true},contentPadding=PaddingValues(horizontal=12.dp)) {Glyph(if(reactions["$scoped:${row.id}"]==true) R.drawable.ic_like_filled else R.drawable.ic_like,16);Text(if(reactions["$scoped:${row.id}"]==true) " 4" else " 3")}
                                    if(row.replies>0) TextButton(onClick={go(Page.THREAD,message=row)}) {Text("${row.replies} replies")}
                                }
                                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        items(sent.filter{it.scope==draftKey},key={"sent:${it.id}"}) { entry -> Panel("You · preview message",entry.text,R.drawable.ic_chat,modifier=Modifier.animateItem()) }
                        items(queued.filter{it.scope==draftKey},key={"queued:${it.id}"}) { pending ->
                            Column(Modifier.animateItem(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Notice("Preview: waiting for connection");Text(pending.text)
                                TextButton(modifier=Modifier.semantics{contentDescription="Cancel queued message ${pending.id}"},onClick={drafts[draftKey]=listOf(pending.text,drafts[draftKey].orEmpty()).filter{it.isNotBlank()}.joinToString("\n\n");queued.remove(pending)}){Text("Cancel")}
                            }
                        }
                        if(page==Page.ANNOUNCEMENTS && admin) item {Action("Write an announcement") {modal="Preview announcement"} }
                    }
                    Page.ARTICLES -> {
                        item { Text("INDEPENDENT WRITING",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) }
                        item { Image(painterResource(R.drawable.stack_editorial),null,Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),contentScale=androidx.compose.ui.layout.ContentScale.Crop) }
                        item { Panel("A quieter kind of internet","Maya Chen · 6 min read\nGood ideas deserve a little more room.",R.drawable.ic_article) {go(Page.ARTICLE,articleId=0)} }
                        item { Panel("Building in the open","Alex Morgan · 4 min read",R.drawable.ic_article) {go(Page.ARTICLE,articleId=1)} }
                        if(canWrite) { item {Action("Write an article") {go(Page.EDITOR,articleId=2)}}; item {OutlinedButton(onClick={modal="Share a public Stack article"},modifier=Modifier.fillMaxWidth()){Text("Share a Stack article")}} }
                    }
                    Page.ARTICLE,Page.REVIEW -> {
                        item {Text(if(page==Page.REVIEW) "Review before publishing" else (if(selectedArticle==0) "Maya Chen" else "Alex Morgan")+" · $space",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
                        item { val markdown=if(page==Page.REVIEW) article.text else publishedArticles[articleKey]?:articleSamples[selectedArticle]; val rich=remember(markdown) {RichTextState().setMarkdown(markdown)}; RichText(rich,style=MaterialTheme.typography.bodyLarge) }
                        if(page==Page.REVIEW) {
                            item {Panel("Publish to $space","${if(privateSpace || channel=="team room") "Private channel" else "This Space only"}. This does not publish to public Stack or Red Square.",R.drawable.ic_lock)}
                            item {Action("Publish preview article") {modal="Preview publication"} }
                        } else item {OutlinedButton(onClick={modal="Share article"},modifier=Modifier.fillMaxWidth()){Text("Share article link")}}
                    }
                    Page.EDITOR -> {
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(selected=!source,onClick={richDrafts.remove(articleKey);source=false},label={Text("Visual")});FilterChip(selected=source,onClick={if(!source) articleDrafts[articleKey]=TextFieldValue(richDrafts[articleKey]?.toMarkdown()?:article.text);source=true},label={Text("Markdown")})} }
                        item { NativeArticleEditor(articleKey,article,source,richDrafts) {articleDrafts[articleKey]=it} }
                        item {Notice("The same native editor used by Stack. Switch to Markdown at any time; publication stays inside this Space.")}
                        item {Action("Review article") {if(!source) articleDrafts[articleKey]=TextFieldValue(richDrafts[articleKey]?.toMarkdown()?:article.text);go(Page.REVIEW)} }
                    }
                    Page.FILES -> {
                        item {Panel("Shared library","${if(scenario=="Storage full") "5.0" else "1.8"} GB of 5 GB · ${if(scenario=="Expired") "Expired" else "Renews with owner confirmation"}",R.drawable.ic_vault) {go(Page.SETTINGS)}}
                        items((1..3).filterNot{removedFiles.contains("$scoped:$it")}) { id -> Panel(filenames[id-1],listOf("Maya Chen · 2.4 MB · Today","Alex Morgan · 18 MB · Yesterday","Sam Rivera · 12 KB · Monday")[id-1],R.drawable.ic_file) {go(Page.FILE,fileId=id,message=seeds[id-1])} }
                        if(canWrite) item {Button(onClick={modal="Preview upload"},enabled=scenario!="Storage full" && scenario!="Expired",modifier=Modifier.fillMaxWidth()){Text("Upload a file")}}
                    }
                    Page.FILE -> {
                        item {Panel(filenames[selectedFile-1],"Shared resource · selected file\nAvailable while Maya is offline.",R.drawable.ic_file)}
                        item {Action(if(scenario=="Expired") "File unavailable" else "Open file preview",scenario!="Expired") {modal="Preview file"} }
                        item {OutlinedButton(enabled=scenario!="Expired",onClick={modal="Offline copy"},modifier=Modifier.fillMaxWidth()){Text("Keep an offline copy")}}
                        item {TextButton(onClick={modal="Remove offline copy"}){Text("Remove offline copy")}}
                        if(admin || selectedMessage.own) item {TextButton(onClick={modal="Remove shared file"}){Text("Remove from Space",color=MaterialTheme.colorScheme.error)}}
                    }
                    Page.ROOM,Page.CALL -> {
                        item {Panel(if(inThisRoom) "You're in the lounge" else "A little room for conversation",if(inThisRoom) "Simulated call · no media is captured" else "2 people here · join when you're ready",R.drawable.ic_video)}
                        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){PersonTile("Maya","MC",Modifier.weight(1f));PersonTile("Sam","SR",Modifier.weight(1f))}}
                        if(!inThisRoom) {
                            item {Notice(if(activeCall) "Your call in $callSpace continues. Return to it below." else "Microphone off. Camera off. Nothing activates before you join.")}
                            item {Action(if(activeCall) "Return to existing call" else "Join preview room",!outsider && !denied && !archived) {if(activeCall) go(Page.CALL,newSpace=callSpace,newChannel=callChannel) else {mic=false;camera=false;activeCall=true;callSpace=space;callChannel=channel;go(Page.CALL)}} }
                        } else {
                            item {PersonTile("You · ${if(camera) "camera preview on" else "camera off"}","AM",Modifier.fillMaxWidth())}
                            item { if(LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(enabled=canWrite,onClick={mic=!mic},modifier=Modifier.fillMaxWidth()){Glyph(if(mic) R.drawable.ic_microphone else R.drawable.ic_microphone_off);Spacer(Modifier.width(8.dp));Text(if(mic) "Mute" else "Unmute")};OutlinedButton(enabled=canWrite,onClick={camera=!camera},modifier=Modifier.fillMaxWidth()){Glyph(if(camera) R.drawable.ic_video else R.drawable.ic_video_off);Spacer(Modifier.width(8.dp));Text(if(camera) "Camera off" else "Camera on")}}
                                else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(enabled=canWrite,onClick={mic=!mic},modifier=Modifier.weight(1f)){Glyph(if(mic) R.drawable.ic_microphone else R.drawable.ic_microphone_off);Spacer(Modifier.width(6.dp));Text(if(mic) "Mute" else "Unmute")};OutlinedButton(enabled=canWrite,onClick={camera=!camera},modifier=Modifier.weight(1f)){Glyph(if(camera) R.drawable.ic_video else R.drawable.ic_video_off);Spacer(Modifier.width(6.dp));Text(if(camera) "Camera off" else "Camera on")}}}
                            item {OutlinedButton(onClick={go(Page.CHAT,newChannel="general")},modifier=Modifier.fillMaxWidth()){Text("Browse while in the call")}}
                            item {Action("Leave preview call") {activeCall=false;mic=false;camera=false;go(Page.ROOM)} }
                        }
                        item {Notice("The channel stays here after everyone leaves. This prototype never requests microphone or camera permission.")}
                    }
                    Page.INFO -> {
                        item {Hero(space,"Independent writing. Open conversations.\nA community worth coming back to.") {go(Page.INVITE)}}
                        item {Panel(if(privateSpace) "Private Space" else "Public Space",if(privateSpace) "Members only · encrypted content required" else if(listed) "Publicly readable · discoverable" else "Publicly readable · unlisted",if(privateSpace) R.drawable.ic_lock else R.drawable.ic_globe)}
                        item {Action("Open channels") {coroutine.launch{drawer.open()}}}
                        item {Panel("Members","128 members · owner and administrators",R.drawable.ic_profile){go(Page.MEMBERS)}}
                        item {Panel("House rules","Be thoughtful. Be curious. Make room for others.",R.drawable.ic_read_all){go(Page.RULES)}}
                        if(admin) {item {Panel("Invite people","Links expire after 7 days by default",R.drawable.ic_link){go(Page.INVITE)}};item {Panel("Manage Space","Channels, storage and permissions",R.drawable.ic_settings){go(Page.SETTINGS)}};item {Panel("Reports and admin log","Private to owner and administrators",R.drawable.ic_info){go(Page.REPORTS)}}}
                        item {TextButton(onClick={modal=if(role=="Owner") "Transfer before leaving" else "Leave Space"}){Text("Leave Space")}}
                    }
                    Page.MEMBERS -> {
                        items(listOf("Alex Morgan" to role,"Maya Chen" to if(role=="Owner") "Admin" else "Owner","Sam Rivera" to "Member","Jules Park" to "Member").filterNot{membersRemoved.contains("$space:${it.first}")}) { (name,title) ->
                            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(name,style=MaterialTheme.typography.titleMedium);Text("${name.substringBefore(' ').lowercase()}@saito · $title",style=MaterialTheme.typography.bodySmall);memberTimeouts["$space:$name"]?.let{Text("Writing paused in $it until 14:30",style=MaterialTheme.typography.bodySmall)}
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={modal="Profile: $name"}){Text("Profile")};TextButton(onClick={modal="Private chat: $name"}){Text("Message")}}
                                    if(admin && title=="Member") TextButton(onClick={editText=name;modal="Moderate member"}){Text("Moderate")}
                                }
                            }
                        }
                    }
                    Page.INVITE -> {
                        item {Panel(space,"Independent voices, thoughtful conversations.\n128 members · ${if(privateSpace) "Private invitation" else "Public Space"}",R.drawable.ic_link)}
                        if(outsider) item {Action("Continue to join") {go(Page.RULES)}} else if(admin) {
                            item {Notice("Invitation expires in 7 days. Anyone holding a valid link can join.")}
                            item {Action("Preview invitation link") {modal="Preview invitation"}}
                            item {OutlinedButton(onClick={modal="Revoke invitation"},modifier=Modifier.fillMaxWidth()){Text("Revoke invitation")}}
                        }
                    }
                    Page.RULES -> {
                        item {Panel("Make yourself at home","Be respectful. Share thoughtfully. Keep private conversations private.\n\nThese are example community rules.",R.drawable.ic_read_all)}
                        item {Action(if(outsider) "Accept rules and join preview Space" else "Back to Space") {memberships[space]=true;if(role=="Visitor") role="Member";go(Page.CHAT,newChannel="general")}}
                    }
                    Page.CREATE -> {
                        item {OutlinedTextField(newName,{newName=it},label={Text("Space name")},modifier=Modifier.fillMaxWidth())}
                        item {OutlinedTextField(newDescription,{newDescription=it},label={Text("Description")},modifier=Modifier.fillMaxWidth())}
                        item {Toggle("Private Space",newPrivate){newPrivate=it};if(!newPrivate) Toggle("Discoverable",newListed){newListed=it}}
                        item {Panel("Start with useful channels","general · announcements · the lounge · reading room · resources\nYou can change these later.",R.drawable.ic_chat)}
                        item {Action("Create preview Space",newName.isNotBlank() && newName !in communityNames) {communityNames.add(newName);privateSpaces[newName]=newPrivate;listings[newName]=newListed;memberships[newName]=true;role="Owner";go(Page.CHAT,newSpace=newName,newChannel="general")}}
                    }
                    Page.SETTINGS -> {
                        if(!admin) item {Notice("Only the owner and administrators can manage this Space.")}
                        else {
                            item {Section("SPACE");if(role=="Owner") {Toggle("Private Space",privateSpace){modal="Change visibility"};if(!privateSpace) Toggle("Discoverable",listed){listings[space]=it}} else Text("Only the owner can change visibility and discoverability.",style=MaterialTheme.typography.bodySmall)}
                            item {OutlinedButton(onClick={modal="Create channel"},modifier=Modifier.fillMaxWidth()){Text("Channels and categories")}}
                            item {Toggle("Archive current channel",archived){archives[scoped]=it};Text("Archived channels stay readable. Reopen to allow contributions.",style=MaterialTheme.typography.bodySmall)}
                            item {Section("STORAGE");Panel("Shared storage","1.8 GB / 5 GB · example provider\nProvider choice and costs need owner confirmation.",R.drawable.ic_vault)}
                            if(role=="Owner") item {OutlinedButton(onClick={modal="Select storage provider"},modifier=Modifier.fillMaxWidth()){Text("Storage provider and renewal")}}
                            item {Section("NOTIFICATIONS");val mode=notificationModes[scoped]?:"Mentions & replies";listOf("Mentions & replies","All messages","Silent").forEach { value -> FilterChip(selected=mode==value,onClick={notificationModes[scoped]=value},label={Text(value)}) };val groupAlerts=groupAlertModes[scoped]?:true;Toggle("Allow @everyone alerts",groupAlerts){groupAlertModes[scoped]=it}}
                            item {OutlinedButton(onClick={go(Page.BACKUP)},modifier=Modifier.fillMaxWidth()){Text("Identity and key recovery")}}
                            if(role=="Owner") {item {OutlinedButton(onClick={modal="Transfer ownership"},modifier=Modifier.fillMaxWidth()){Text("Transfer ownership")}};item {TextButton(onClick={modal="Dissolve Space"}){Text("Dissolve Space",color=MaterialTheme.colorScheme.error)}}}
                        }
                    }
                    Page.REPORTS -> {item {Panel("Message reported by Sam","Reason: off-topic promotion · general\nOnly the owner and administrators see this report.",R.drawable.ic_chat){modal="Review report"}};item {Action("View admin log") {go(Page.LOG)}}}
                    Page.LOG -> {items(listOf("Maya archived old-announcements · 11:20","Alex updated channel access · 10:15","Alex invited a new administrator · Yesterday")){Panel(it,"Example administrative event",R.drawable.ic_info)}}
                    Page.BACKUP -> {item {Panel("Keep your identity. Keep your keys.","Encrypted conversations need their history keys as well as your Saito identity. A wallet restore alone may not restore all history.",R.drawable.ic_lock)};item {Action("Preview backup steps") {modal="Backup recovery"}};item {OutlinedButton(onClick={modal="Restore recovery"},modifier=Modifier.fillMaxWidth()){Text("Preview restoration")}}}
                    Page.PREVIEW -> {
                        item {Notice("Design study only. No wallet, network, payments or captured media. Changes reset when the prototype process closes.")}
                        item {Section("ROLE");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Owner","Admin","Member","Visitor").forEach { value -> FilterChip(selected=role==value,onClick={role=value;memberships[space]=value!="Visitor"},label={Text(value)})}}}
                        item {Section("STATE");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Normal","Empty","Loading","Offline","Access denied","Timeout","Storage full","Expired").forEach {value->FilterChip(selected=scenario==value,onClick={scenario=value},label={Text(value)})}}}
                        item {Section("APPEARANCE");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("SYSTEM","LIGHT","DARK").forEach {value->FilterChip(selected=appearance==value,onClick={onAppearance(value)},label={Text(value.lowercase())})}}}
                        item {Section("JUMP TO A FLOW");FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(Page.CHAT,Page.ARTICLES,Page.FILES,Page.ROOM,Page.MEMBERS,Page.SETTINGS,Page.INVITE,Page.CREATE,Page.BACKUP).forEach {target->OutlinedButton(onClick={go(target,newChannel=when(target){Page.CHAT->"general";Page.ARTICLES->"reading room";Page.FILES->"resources";Page.ROOM->"the lounge";else->channel})}){Text(target.name.lowercase())}}}}
                    }
                }
            }
        }
    }
    if(modal!=null) {
        val title=modal!!
        var detail by remember(title){mutableStateOf("")}
        AlertDialog(onDismissRequest={modal=null},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when(title) {
                "Saito apps" -> {Text("The Spaces prototype sits beside your existing Saito modules.");listOf("Red Square","Stack","Saito Talk","Vault","Store","Spaces").forEach { app->TextButton(onClick={detail=if(app=="Spaces") "You are in Spaces." else "$app remains in your ordinary beta. This isolated prototype contains Spaces only."}){Text(app)}};Text(detail)}
                "Message actions" -> {Text(actionMessage.body);if(actionMessage.own || admin) TextButton(onClick={removed.add("$scoped:${actionMessage.id}");modal=null}){Text("Remove message")};if(actionMessage.own && canWrite) TextButton(onClick={editMessage=actionMessage;editText=edits["$scoped:${actionMessage.id}"]?:actionMessage.body;modal=null}){Text("Edit message")};TextButton(onClick={modal="Report message"}){Text("Report to administrators")};if(admin) TextButton(onClick={modal="Pinned in preview"}){Text("Pin message")}}
                "Moderate member" -> {Text("$editText · select a scope for a preview timeout.");var whole by remember{mutableStateOf(false)};Toggle("Whole Space",whole){whole=it};Text("Until 14:30. Reading and listening remain available.");TextButton(onClick={memberTimeouts["$space:$editText"]=if(whole) "the whole Space" else channel;modal="Timeout applied in preview"}){Text("Apply preview timeout")};TextButton(onClick={membersRemoved.add("$space:$editText");modal=null}){Text("Remove member")};TextButton(onClick={membersRemoved.add("$space:$editText");modal=null}){Text("Ban this identity")}}
                "Report message" -> {Text("Report privately to the owner and administrators.");OutlinedTextField(detail,{detail=it},label={Text("Reason")});TextButton(onClick={modal="Report recorded in preview"}){Text("Submit preview report")}}
                "Change visibility" -> Text(if(privateSpace) "Make this Space public? Existing private channels and their history stay private." else "Make this Space private? Previously public content may already have been copied.")
                "Remove shared file" -> Text("Remove the shared file from this Space? Already downloaded copies cannot be recalled. This differs from removing your own offline copy.")
                "Transfer ownership" -> Text("Invite Maya Chen to become the owner? Both of you must confirm. After the handover you remain a normal member.")
                "Dissolve Space" -> Text("Only the owner can dissolve this Space. Previously retained copies will remain. This preview removes only the in-memory community view.")
                "Transfer before leaving" -> {Text("Transfer ownership before leaving a continuing Space.");TextButton(onClick={modal="Transfer ownership"}){Text("Choose a successor")}}
                "Preview publication" -> Text("This is a simulated publication to $space. No article or transaction will be sent. Public Stack and Red Square sharing remains a separate choice.")
                "Preview invitation","Revoke invitation" -> Text("Preview only: invitation validity defaults to 7 days. Revoking stops new admissions and does not remove existing members. No real invitation is generated.")
                "Backup recovery","Restore recovery" -> Text("Design walkthrough: choose an encrypted backup, protect its recovery secret, and verify identity plus history-key recovery. Missing keys or unavailable archives must remain explicit. No backup is created or imported here.")
                "Create channel" -> {OutlinedTextField(detail,{detail=it},label={Text("Channel or category name")});Text("Types: chat, voice/video, articles, files. Private-channel access always includes the owner and administrators.")}
                else -> Text("Design preview only. This action will use the existing module in the functional app. No network request, file transfer, payment or public message occurs here.")
            }
        }},confirmButton={TextButton(onClick={
            when(title){"Change visibility"->if(role=="Owner") privateSpaces[space]=!privateSpace;"Remove shared file"->{removedFiles.add("$scoped:$selectedFile");go(Page.FILES)};"Dissolve Space"->{communityNames.remove(space);memberships[space]=false;go(Page.HOME)};"Leave Space"->{memberships[space]=false;go(Page.HOME)};"Preview publication"->{publishedArticles[articleKey]=article.text;go(Page.ARTICLE)};"Create channel"->if(detail.isNotBlank()){go(Page.CHAT,newChannel=detail)}}
            modal=null
        }){Text(if(title in listOf("Change visibility","Remove shared file","Dissolve Space","Leave Space")) "Confirm preview" else "Done")}},dismissButton={TextButton(onClick={modal=null}){Text("Cancel")}})
    }
    editMessage?.let { row -> AlertDialog(onDismissRequest={editMessage=null},title={Text("Edit message")},text={OutlinedTextField(editText,{editText=it})},confirmButton={TextButton(onClick={if(canWrite) edits["$scoped:${row.id}"]=editText;editMessage=null}){Text("Save edit")}},dismissButton={TextButton(onClick={editMessage=null}){Text("Cancel")}}) }
}
@Composable private fun Glyph(id:Int,size:Int=22,label:String?=null) { Icon(painterResource(id),label,Modifier.size(size.dp)) }
@Composable private fun Avatar(initials:String,color:Color,size:Int=44) { Box(Modifier.size(size.dp).background(color,CircleShape),contentAlignment=Alignment.Center){Text(initials,color=Color.White,fontWeight=FontWeight.SemiBold)} }
@Composable private fun Section(text:String) {Text(text,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))}
@Composable private fun Notice(text:String) { Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(12.dp)) {Text(text,Modifier.fillMaxWidth().padding(14.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
@Composable private fun Action(label:String,enabled:Boolean=true,click:()->Unit) {Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)){Text(label)}}
@Composable private fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit) {Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=value,role=Role.Switch,onValueChange=change).semantics(mergeDescendants=true){contentDescription=label},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(1f).clearAndSetSemantics{});Switch(checked=value,onCheckedChange=null)}}
@Composable private fun Panel(title:String,body:String,icon:Int,modifier:Modifier=Modifier,click:(()->Unit)?=null) {
    val content:@Composable ()->Unit={Column(Modifier.fillMaxWidth().padding(18.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){Glyph(icon);Text(title,style=MaterialTheme.typography.titleLarge);Text(body,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    if(click!=null) Surface(modifier=modifier,onClick=click,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,content=content)
    else Surface(modifier=modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,content=content)
}
@Composable private fun CommunityRow(name:String,summary:String,current:Boolean,color:Color=Color(0xffa35b43),click:()->Unit) {
    Surface(onClick=click,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Avatar(name.split(" ").take(2).map{it.firstOrNull()?:'S'}.joinToString(""),color)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(name,style=MaterialTheme.typography.titleMedium);Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(current) Badge{Text("2")} else Glyph(R.drawable.ic_forward,18)
        }
    }
}
@Composable private fun Hero(title:String,body:String,click:()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp)) {Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff452b38),Color(0xff233842)))).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("INDEPENDENT VOICES",style=MaterialTheme.typography.labelMedium,color=Color(0xffffb6a4))
        Text(title,style=MaterialTheme.typography.headlineLarge,color=Color.White)
        Text(body,style=MaterialTheme.typography.bodyMedium,color=Color(0xffe1d9dd))
        TextButton(onClick=click,contentPadding=PaddingValues(0.dp)){Text("Make room for your community",color=Color(0xffffb6c1))}
    }}
}
@Composable private fun PersonTile(name:String,initials:String,modifier:Modifier=Modifier) {Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.padding(18.dp).heightIn(min=125.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Avatar(initials,Color(0xff537c81),54);Text(name,style=MaterialTheme.typography.bodyMedium)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NativeArticleEditor(key:String,value:TextFieldValue,source:Boolean,states:MutableMap<String,RichTextState>,onChange:(TextFieldValue)->Unit) {
    if(source) OutlinedTextField(value,onChange,label={Text("Markdown source")},modifier=Modifier.fillMaxWidth().heightIn(min=280.dp),shape=RoundedCornerShape(16.dp))
    else {
        val rich=remember(key) {states.getOrPut(key){RichTextState().setMarkdown(value.text)}}
        val latestChange by rememberUpdatedState(onChange)
        LaunchedEffect(rich) {snapshotFlow{rich.toMarkdown()}.drop(1).collect{latestChange(TextFieldValue(it))}}
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={rich.toggleSpanStyle(SpanStyle(fontWeight=FontWeight.Bold))}){Text("Bold")}
                OutlinedButton(onClick={rich.insertMarkdownAfterSelection("\n## Heading\n")}){Text("Heading")}
            }
            RichTextEditor(rich,modifier=Modifier.fillMaxWidth().heightIn(min=280.dp).semantics{contentDescription="Article body"},minLines=8,
                colors=RichTextEditorDefaults.richTextEditorColors(textColor=MaterialTheme.colorScheme.onSurface,containerColor=Color.Transparent,cursorColor=MaterialTheme.colorScheme.primary),textStyle=MaterialTheme.typography.bodyLarge)
        }
    }
}
