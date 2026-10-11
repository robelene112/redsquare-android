package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import nl.davidengelen.redsquare.saito.FeedPost

internal sealed interface BrowseDestination {
    class Search(val view: SearchViewState = SearchViewState()) : BrowseDestination
    data class Conversation(val post: FeedPost) : BrowseDestination
    data class Quote(val post: FeedPost) : BrowseDestination
    class Profile(val author: String, val view: ProfileViewState = ProfileViewState()) : BrowseDestination
}

/** Keeps the selected public context across activity recreation, scoped by wallet and server. */
internal class FeedNavigationMemory : ViewModel() {
    val destinations = mutableStateListOf<BrowseDestination>()
}
