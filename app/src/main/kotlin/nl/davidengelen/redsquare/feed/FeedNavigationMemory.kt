package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.AuthorActionKind

internal sealed interface BrowseDestination {
    class Search(val view: SearchViewState = SearchViewState()) : BrowseDestination
    data object Notifications : BrowseDestination
    data class Reference(val target: String, val view: ConversationViewState = ConversationViewState()) : BrowseDestination
    data class PrivateConversation(val contact: String) : BrowseDestination
    data class Conversation(val post: FeedPost, val view: ConversationViewState = ConversationViewState()) : BrowseDestination
    data class Quote(val post: FeedPost) : BrowseDestination
    data class Author(val post: FeedPost, val kind: AuthorActionKind) : BrowseDestination
    class Profile(val author: String, val view: ProfileViewState = ProfileViewState()) : BrowseDestination
}

/** Keep the exact reply target through recreation without saving image payloads in the Activity bundle. */
internal class ConversationViewState {
    var replyingTo by mutableStateOf<FeedPost?>(null)
}

/** Keeps the selected public context across activity recreation, scoped by wallet and server. */
internal class FeedNavigationMemory : ViewModel() {
    val destinations = mutableStateListOf<BrowseDestination>()
    val visitedProfiles = mutableStateListOf<String>()
    val searchView = SearchViewState()
    val activityView = ActivityViewState()
    val ownProfileView = ProfileViewState()
}
