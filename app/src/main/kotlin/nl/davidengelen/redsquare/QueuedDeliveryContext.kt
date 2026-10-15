package nl.davidengelen.redsquare

import android.content.Context
import android.content.Intent
import androidx.work.Data
import nl.davidengelen.redsquare.feed.FeedEndpoints
import nl.davidengelen.redsquare.saito.*

/** Resolve public job routing against fresh protected selection for both outboxes. */
internal data class QueuedDeliveryContext(val wallet:SaitoWallet,val backend:SaitoBackend,
    val transactionId:String,val endpoint:String) {
    companion object {
        fun selected(context:Context,input:Data):QueuedDeliveryContext? {
            val backend=SaitoBackend.entries.firstOrNull{it.id==input.getString("backend")}
                ?:throw InvalidQueuedDelivery()
            val id=input.getString("transaction") ?:throw InvalidQueuedDelivery()
            val wallet=synchronized(SaitoLocalState.lock) {
                if(AndroidBackendStore(context).load()!=backend)return null
                AndroidWalletStore(context).loadExisting()
            }
            if(wallet.publicKey!=input.getString("owner"))return null
            val requested=requireNotNull(input.getString("endpoint"))
            val endpoint=if(!BuildConfig.DEBUG||requested==backend.peerUrl)backend.peerUrl else {
                val extra=if(backend==SaitoBackend.OWNER)"feedPeerUrl" else "feedOfficialPeerUrl"
                FeedEndpoints.fromIntent(Intent().putExtra(extra,requested),backend)
            }
            return QueuedDeliveryContext(wallet,backend,id,endpoint)
        }
    }
}

internal class InvalidQueuedDelivery:IllegalArgumentException("Missing queue routing")
