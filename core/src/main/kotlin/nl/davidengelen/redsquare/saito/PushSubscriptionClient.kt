package nl.davidengelen.redsquare.saito

/** Portable subscription lifecycle; Android supplies encrypted device storage and HTTPS. */
class PushSubscriptionClient(private val origin: String, private val journal: Journal, private val http: Http) {
    interface Journal {
        fun load(): String?
        /** Return only after the replacement is durable. */
        fun save(value: String)
    }
    fun interface Http {
        fun post(origin: String, path: String, body: String, signature: String?): Reply
    }
    data class Reply(val status: Int, val body: String)
    class Demand(val wallet: SaitoWallet, val backend: SaitoBackend, val fid: String,
        val categories: String = "mentions,replies")

    fun reconcile(currentDemand: () -> Demand?) = Unit
}
