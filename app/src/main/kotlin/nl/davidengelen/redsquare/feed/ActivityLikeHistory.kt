package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.saito.ArchiveLikePage

/** A retry retains the position it actually failed to read, even if a new head arrives meanwhile. */
internal sealed interface ActivityPageRead {
    val before: Long
    data class Newest(override val before: Long) : ActivityPageRead
    data class Older(override val before: Long) : ActivityPageRead
    data class CatchUp(override val before: Long, val until: Long) : ActivityPageRead
}

/** Two bounded ranges: unseen newer pages and the reader's independent older continuation. */
internal data class ActivityLikeHistory(
    val observed: Boolean = false,
    val headTime: Long? = null,
    val olderBefore: Long? = null,
    val catchUp: ActivityPageRead.CatchUp? = null,
) {
    val continuation: ActivityPageRead? get() = catchUp ?: olderBefore?.let(ActivityPageRead::Older)

    fun observe(read: ActivityPageRead, page: ArchiveLikePage, nextBefore: Long?, newEvents: Boolean): ActivityLikeHistory {
        val pageHead = page.likes.maxOfOrNull { it.createdAt } ?: page.oldestCreatedAt
        val updatedHead = listOfNotNull(headTime, pageHead).maxOrNull()
        if (!observed) return ActivityLikeHistory(true, updatedHead, nextBefore)
        // A retry of an old newest request cannot erase a catch-up range learned from a newer request.
        if (read is ActivityPageRead.Newest && (headTime == null || read.before > headTime)) {
            if (nextBefore == null) return ActivityLikeHistory(true, updatedHead)
            val previousHead = headTime ?: 0L
            val gap = if (newEvents && (page.oldestCreatedAt ?: nextBefore) > previousHead) {
                ActivityPageRead.CatchUp(maxOf(nextBefore, catchUp?.before ?: nextBefore),
                    minOf(previousHead, catchUp?.until ?: previousHead))
            } else catchUp
            return copy(headTime = updatedHead, catchUp = gap)
        }
        // Apply the covered slice without discarding an unrequested newer part of an expanded gap.
        val older = if (olderBefore != null && read.before >= olderBefore)
            nextBefore?.let { minOf(olderBefore, it) } else olderBefore
        val gap = catchUp?.let { current ->
            val reached = nextBefore == null || (page.oldestCreatedAt ?: nextBefore) <= current.until
            when {
                read.before >= current.before -> if (reached) null else current.copy(before = minOf(current.before, nextBefore!!))
                reached -> current.copy(until = maxOf(current.until, read.before)).takeIf { it.before > it.until }
                else -> current
            }
        }
        return copy(headTime = updatedHead, olderBefore = older, catchUp = gap)
    }
}
