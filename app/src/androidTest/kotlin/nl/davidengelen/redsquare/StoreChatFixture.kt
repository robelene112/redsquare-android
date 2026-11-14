package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Public unsafe buyer fixture only. Assertions cannot prevent independent captured-state restoration. */
internal fun <T> withStoreChatWallet(seller: String, block: (SaitoWallet) -> T): T {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val original = wallets.loadOrCreate()
    val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4").withChatContact(seller)
    val state = ChatFixtureState(context, listOf("chat.", "chatwork.", "chatreactions.", "personal."), wallet.publicKey)
    var primary: Throwable? = null
    try {
        state.prepare()
        wallets.replace(original, wallet)
        return block(wallet)
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() },
            { check(wallets.loadExisting().exportJson() == wallet.exportJson()) }, {
                val current = wallets.loadExisting()
                if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
                check(wallets.loadExisting().exportJson() == original.exportJson())
            })
    }
}
