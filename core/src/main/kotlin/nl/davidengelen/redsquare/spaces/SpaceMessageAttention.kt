package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet

/** Derived only from authenticated received text, parent and acceptance-time
 * authority. No new public routing metadata is attached to private messages. */
data class SpaceMessageAttention(val mentioned: Boolean, val repliedToMe: Boolean, val everyone: Boolean,
    val ownMessage: Boolean) {
    override fun toString() = "Private community message attention"
    companion object {
        private val mention = Regex("(?<![A-Za-z0-9_])@([1-9A-HJ-NP-Za-km-z]{32,64})(?![A-Za-z0-9_])")
        private val group = Regex("(?<![A-Za-z0-9_])@everyone(?![A-Za-z0-9_])")
        internal fun mentions(text: String, key: String) = mention.findAll(text).any { it.groupValues[1] == key && SaitoWallet.isPublicKey(key) }
        internal fun everyone(text: String) = group.containsMatchIn(text)
    }
}
