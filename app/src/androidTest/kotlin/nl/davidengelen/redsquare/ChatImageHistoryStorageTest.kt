package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.GroupChat
import nl.davidengelen.redsquare.saito.PrivateChat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Practical image history through the protected cache interface, with no network or UI writes. */
class ChatImageHistoryStorageTest {
    @Test fun twelveOrdinaryPrivatePhotosRemainAvailableWithoutLosingEarlierHistoryOrDrafts() = exercise(group=false)

    @Test fun sixteenOrdinaryGroupPhotosRemainAvailableWithoutLosingEarlierHistoryOrDrafts() = exercise(group=true)

    private fun exercise(group:Boolean) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate().exportJson()
        val backend=AndroidBackendStore(context).load()
        val wallet=SaitoWallet.fromPrivateKey("0".repeat(62)+"12") // Public unsafe fixtures. Never fund.
        val other=SaitoWallet.fromPrivateKey("0".repeat(62)+"13")
        val scope=SaitoBackend.OWNER
        val retained=ChatFixtureState(context,listOf("chat.${scope.id}.${wallet.publicKey}.",
            "chatwork.${scope.id}.${wallet.publicKey}.","groupchat.${scope.id}.${wallet.publicKey}."),wallet.publicKey)
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
            val groups=GroupChatStore(context,wallet,scope)
            val groupId=SaitoWallet.fromPrivateKey("0".repeat(62)+"14").publicKey
            val creation=GroupChat.create(other,groupId,"Photo history",listOf(wallet.publicKey),1700000123400)
            if(group)groups.receive(creation.envelope())
            val count=if(group)16 else 12
            fun message(index:Int):String {
                val text=composeChatPhoto(source,"Photo $index")
                return if(group)GroupChat.message(other,GroupChat.readCreation(creation.envelope()),text,1700000123400L+index).envelope()
                    else PrivateChat.create(other,wallet.publicKey,text,1700000123400L+index).envelope()
            }
            val originals=(1..count).map(::message)
            check(originals.sumOf{it.toByteArray().size}>5*1024*1024)
            val history=ChatHistoryStore(context,wallet,scope,other.publicKey)
            val work=ChatWorkStore(context,wallet,scope,other.publicKey)
            val draft="Keep my unsent text while photo history grows 😀"
            if(group)groups.saveDraft(groupId,draft)else work.saveDraft(draft)
            fun saveHistory(envelope:String) {if(group)groups.receive(envelope)else history.save(listOf(envelope))}
            fun loadHistory()=if(group)requireNotNull(groups.load(groupId)).originals
                else ChatHistoryStore(context,wallet,scope,other.publicKey).load().entries.map{it.envelope}
            fun loadDraft()=if(group)requireNotNull(groups.load(groupId)).draft else work.load().draft
            val accepted=mutableListOf<String>()
            for(envelope in originals) {
                try {saveHistory(envelope)}
                catch(failure:Exception) {
                    assertEquals(accepted,loadHistory())
                    assertEquals(draft,loadDraft())
                    println("Photo history stopped after ${accepted.size} ordinary photos; prior history and draft survived")
                    throw failure
                }
                accepted+=envelope
            }
            assertEquals(originals,loadHistory())
            assertEquals(draft,loadDraft())
            if(!group)assertEquals(listOf(other.publicKey),ChatHistoryStore.contacts(context,wallet,scope).accounts)
            if(group) {
                groups.saveDraft(groupId,"")
                check(!GroupChatStore.hasWork(context,wallet,scope))
                groups.saveDraft(groupId,draft)
                check(GroupChatStore.hasWork(context,wallet,scope))
            }
            // API29 writes the base/.bak pair, not .new. Exercise the .new write fault only where it is effective.
            val record=if(group)"groupchat.${scope.id}.${wallet.publicKey}.$groupId.v1"
                else "chat.${scope.id}.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey,other.publicKey)}.v1"
            val blocker=java.io.File(context.noBackupFilesDir,"$record.new")
            val extra=message(count+1)
            if(Build.VERSION.SDK_INT>=30) {
                check(blocker.mkdir())
                try {
                    java.io.File(blocker,"held-by-fixture").writeText("Synthetic write fault")
                    check(runCatching{saveHistory(extra)}.isFailure)
                } finally {check(blocker.deleteRecursively())}
                assertEquals(originals,loadHistory())
                assertEquals(draft,loadDraft())
            } else InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                putString("stream","\nAPI29: capacity/growth covered; .new atomic write fault requires API30+.\n")
            })
            saveHistory(extra)
            assertEquals(originals+extra,loadHistory())
            assertEquals(draft,loadDraft())
            assertEquals(original,wallets.loadExisting().exportJson())
            assertEquals(backend,AndroidBackendStore(context).load())
        } catch(failure:Throwable) {primary=failure;throw failure}
        finally {cleanupAfterTest(primary,{retained.restoreFiles()},{retained.restorePreferences()})}
    }
}
