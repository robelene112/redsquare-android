package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.PrivateChat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Practical image history through the protected cache interface, with no network or UI writes. */
class ChatImageHistoryStorageTest {
    @Test fun twelveOrdinaryPrivatePhotosRemainAvailableWithoutLosingEarlierHistoryOrDrafts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate().exportJson()
        val backend=AndroidBackendStore(context).load()
        val wallet=SaitoWallet.fromPrivateKey("0".repeat(62)+"12") // Public unsafe fixtures. Never fund.
        val other=SaitoWallet.fromPrivateKey("0".repeat(62)+"13")
        val scope=SaitoBackend.OWNER
        val retained=ChatFixtureState(context,listOf("chat.${scope.id}.${wallet.publicKey}.",
            "chatwork.${scope.id}.${wallet.publicKey}."),wallet.publicKey)
        var primary:Throwable?=null
        try {
            retained.prepare()
            val random=java.util.Random(42)
            val bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
            val bytes=try {
                bitmap.setPixels(IntArray(320*240){0xff000000.toInt() or random.nextInt(0x1000000)},0,320,0,0,320,240)
                ByteArrayOutputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray()}
            } finally {bitmap.recycle()}
            val source="data:image/png;base64,"+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
            check(source.length<500_000)
            val originals=(1..12).map{index->PrivateChat.create(other,wallet.publicKey,
                composeChatPhoto(source,"Photo $index"),1700000123400L+index).envelope()}
            check(originals.sumOf{it.toByteArray().size}>5*1024*1024)
            val history=ChatHistoryStore(context,wallet,scope,other.publicKey)
            val work=ChatWorkStore(context,wallet,scope,other.publicKey)
            val draft="Keep my unsent text while photo history grows 😀"
            work.saveDraft(draft)
            val accepted=mutableListOf<String>()
            for(envelope in originals) {
                try {history.save(listOf(envelope))}
                catch(failure:Exception) {
                    assertEquals(accepted,history.load().entries.map{it.envelope})
                    assertEquals(draft,work.load().draft)
                    println("Photo history stopped after ${accepted.size} ordinary photos; prior history and draft survived")
                    throw failure
                }
                accepted+=envelope
            }
            assertEquals(originals,ChatHistoryStore(context,wallet,scope,other.publicKey).load().entries.map{it.envelope})
            assertEquals(draft,work.load().draft)
            assertEquals(original,wallets.loadExisting().exportJson())
            assertEquals(backend,AndroidBackendStore(context).load())
        } catch(failure:Throwable) {primary=failure;throw failure}
        finally {cleanupAfterTest(primary,{retained.restoreFiles()},{retained.restorePreferences()})}
    }
}
