package nl.davidengelen.redsquare.spaces

data class SpaceSearchFilter(val channel: String? = null, val kind: SpaceDestination.Kind? = null) {
    init {
        require(channel == null || channel.matches(Regex("[0-9a-f]{64}")))
        require(kind == null || kind in setOf(SpaceDestination.Kind.MESSAGE, SpaceDestination.Kind.ARTICLE, SpaceDestination.Kind.FILE))
    }
}

data class SpaceSearchCursor(val channel: String, val before: Long? = null) {
    init {
        require(channel.matches(Regex("[0-9a-f]{64}")))
        require(before == null || before >= 0)
    }
}

data class SpaceSearchHit(val address: SpaceDestination, val channelName: String, val author: String, val excerpt: String) {
    override fun toString() = "Space search result"
}

data class SpaceSearchPage(val hits: List<SpaceSearchHit>, val next: SpaceSearchCursor?)
