package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoTransaction
import java.nio.ByteBuffer
import java.util.Base64

/** Decode the transaction from a one-hop controlled peer broadcast. */
internal fun postingTransaction(frame: ByteArray): SaitoTransaction {
    check(frame[0] == 4.toByte())
    val bytes = frame.copyOfRange(1, frame.size - 130)
    check(ByteBuffer.wrap(bytes).getInt(12) == 1)
    ByteBuffer.wrap(bytes).putInt(12, 0)
    return SaitoTransaction.restore(Base64.getEncoder().encodeToString(bytes))
}

internal fun postingArchiveRow(transaction: SaitoTransaction): JsonObject {
    val envelope = transaction.envelope()
    val post = RedSquareFeed.decode(envelope)
    return JsonObject().apply {
        addProperty("sig", post.id); addProperty("created_at", post.createdAt)
        addProperty("field1", "RedSquare"); addProperty("field2", post.author)
        addProperty("field4", post.parentId); addProperty("field5", post.threadId)
        addProperty("tx", envelope)
    }
}
