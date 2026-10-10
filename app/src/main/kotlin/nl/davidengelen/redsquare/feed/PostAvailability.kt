package nl.davidengelen.redsquare.feed

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction

/** A verified original post on this peer permits reconciliation without retransmission. */
internal suspend fun SaitoPeer.hasPost(candidate: SaitoTransaction, owner: String, text: String): Boolean {
    val query = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "RedSquare")
            addProperty("sig", candidate.id); addProperty("flagged_ne", 1)
        })
    }
    val response = request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
    return RedSquareFeed.readTarget(response, candidate.id).posts.any { it.author == owner && it.text == text }
}
