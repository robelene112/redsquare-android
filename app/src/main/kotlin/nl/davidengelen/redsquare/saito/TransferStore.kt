package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger

/** Wallet-wide reservations survive backend changes. No monetary queue or automatic send. */
class TransferStore(private val context: Context, private val author: String) {
    init { require(SaitoWallet.isPublicKey(author)) }
    data class Pending(val backend: SaitoBackend, val payment: SaitoPayment,
        val lastInclusion: SaitoPaymentChain.Inclusion? = null)
    data class PendingMint(val backend: SaitoBackend, val candidate: SaitoVaultMint.Candidate)
    data class PendingKey(val backend: SaitoBackend, val transfer: SaitoNftTransfer, val mayHaveSent: Boolean = false)
    private val name = "transfer.$author"
    private fun file(suffix: String) = EncryptedLocalFile(context, "$name.$suffix",
        "redsquare-transfers-v1", "transfer.")
    private fun exists(suffix: String) = context.noBackupFilesDir.listFiles()?.any {
        it.name == "$name.$suffix" || it.name == "$name.$suffix.bak" || it.name == "$name.$suffix.new"
    } ?: error("Transfer storage unavailable")

    fun pending(): List<Pending> = synchronized(SaitoLocalState.lock) {
        if (!exists("pending") && !exists("guard")) return@synchronized emptyList()
        // Partial creation or loss of either committed file is a recovery barrier.
        val guard = requireNotNull(file("guard").load())
        require(guard.version == 1.toByte() && guard.text == author)
        val contents = requireNotNull(file("pending").load())
        require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray
        require(rows.size() in 1..100)
        val ids = HashSet<String>()
        val locations = HashSet<Triple<java.math.BigInteger, java.math.BigInteger, Int>>()
        rows.map { element ->
            val row = element.asJsonObject
            require(row.keySet() == setOf("backend", "candidate") ||
                row.keySet() == setOf("backend", "candidate", "lastInclusion"))
            require(row.get("backend").asJsonPrimitive.isString && row.get("candidate").asJsonPrimitive.isString)
            val backend = requireNotNull(SaitoBackend.entries.find { it.id == row.get("backend").asString })
            val payment = SaitoPayment.restore(row.get("candidate").asString)
            require(payment.author == author && ids.add(payment.transaction.id))
            require(payment.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            Pending(backend, payment, if (row.has("lastInclusion"))
                readInclusion(row.getAsJsonObject("lastInclusion")) else null)
        }
    }

    fun available(slips: List<SaitoBalanceSnapshot.Slip>): List<SaitoBalanceSnapshot.Slip> = synchronized(SaitoLocalState.lock) {
        val reserved = reservedLocations()
        slips.filter { Triple(it.block, it.ordinal, it.index) !in reserved }
    }

    fun hasWork(): Boolean = pending().isNotEmpty() || pendingMints().isNotEmpty() || pendingKeys().isNotEmpty() || StoreOrderStore(context, author).read().isNotEmpty() || StoreListingStore(context, author).read().isNotEmpty()

    fun pendingMints(): List<PendingMint> = synchronized(SaitoLocalState.lock) {
        if (!exists("mint-reservations") && !exists("mint-reservations-guard")) return@synchronized emptyList()
        val guard = requireNotNull(file("mint-reservations-guard").load())
        require(guard.version == 1.toByte() && guard.text == author)
        val contents = requireNotNull(file("mint-reservations").load())
        require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray
        require(rows.size() in 0..100)
        val ids = HashSet<String>()
        val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
        rows.map { element ->
            val row = element.asJsonObject
            require(row.keySet() == setOf("backend", "candidate") && row.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString })
            val backend = requireNotNull(SaitoBackend.entries.find { it.id == row.get("backend").asString })
            val candidate = SaitoVaultMint.readCandidate(row.get("candidate").asString)
            require(candidate.author == author && ids.add(candidate.transaction.id))
            require(candidate.inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
            PendingMint(backend, candidate)
        }
    }

    /** The same exact candidate is retryable; reservations never move to a different signature or node. */
    fun reserveMint(backend: SaitoBackend, candidate: SaitoVaultMint.Candidate) = synchronized(SaitoLocalState.lock) {
        require(candidate.author == author)
        val previous = pendingMints()
        val reserved = reservedLocations()
        previous.firstOrNull { it.candidate.transaction.id == candidate.transaction.id }?.let {
            require(it.backend == backend && it.candidate.transaction.encoded() == candidate.transaction.encoded())
            return@synchronized
        }
        require(previous.size < 100 && candidate.inputs.none { Triple(it.block, it.ordinal, it.index) in reserved })
        val next = previous + PendingMint(backend, candidate)
        val rows = JsonArray().apply { next.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.candidate.transaction.encoded())
        }) } }
        file("mint-reservations").save(EncryptedLocalFile.Contents(1, rows.toString()))
        if (!exists("mint-reservations-guard")) file("mint-reservations-guard").save(EncryptedLocalFile.Contents(1, author))
        check(pendingMints().map { it.backend to it.candidate.transaction.encoded() } == next.map { it.backend to it.candidate.transaction.encoded() })
    }

    /** Release only a caller-verified never-submitted mint, under the physical-send lock. */
    fun releaseUnsubmittedMint(backend: SaitoBackend, candidate: SaitoVaultMint.Candidate,
        verifyNeverSubmitted: () -> Unit) = synchronized(SaitoLocalState.lock) {
        require(candidate.author == author)
        verifyNeverSubmitted()
        val previous = pendingMints()
        val retained = previous.firstOrNull { it.candidate.transaction.id == candidate.transaction.id } ?: return@synchronized
        require(retained.backend == backend && retained.candidate.transaction.encoded() == candidate.transaction.encoded())
        val next = previous.filterNot { it.candidate.transaction.id == candidate.transaction.id }
        val rows = JsonArray().apply { next.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.candidate.transaction.encoded())
        }) } }
        file("mint-reservations").save(EncryptedLocalFile.Contents(1, rows.toString()))
        check(pendingMints().map { it.backend to it.candidate.transaction.encoded() } == next.map { it.backend to it.candidate.transaction.encoded() })
    }

    fun pendingKeys(): List<PendingKey> = synchronized(SaitoLocalState.lock) {
        if (!exists("key-reservations") && !exists("key-reservations-guard")) return@synchronized emptyList()
        val guard = requireNotNull(file("key-reservations-guard").load())
        require(guard.version == 1.toByte() && guard.text == author)
        val contents = requireNotNull(file("key-reservations").load())
        require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray
        require(rows.size() in 0..100)
        val signatures = HashSet<String>()
        val positions = HashSet<Triple<BigInteger, BigInteger, Int>>()
        rows.map { element ->
            val row = element.asJsonObject
            require(row.keySet() == setOf("backend", "candidate", "mayHaveSent"))
            require(row.get("backend").asJsonPrimitive.isString && row.get("candidate").asJsonPrimitive.isString &&
                row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            val backend = requireNotNull(SaitoBackend.entries.find { it.id == row.get("backend").asString })
            val transfer = SaitoNftTransfer.restore(row.get("candidate").asString)
            require(transfer.author == author && signatures.add(transfer.transaction.id))
            require(transfer.inputs.all { positions.add(Triple(it.block, it.ordinal, it.index)) })
            PendingKey(backend, transfer, row.get("mayHaveSent").asBoolean)
        }
    }

    fun availableKeys(keys: List<SaitoNft>): List<SaitoNft> = synchronized(SaitoLocalState.lock) {
        val reserved = reservedLocations()
        keys.filter { it.owner == author && it.positions.none { row -> Triple(row.block, row.ordinal, row.index) in reserved } }
    }

    fun reserveKey(backend: SaitoBackend, transfer: SaitoNftTransfer) = synchronized(SaitoLocalState.lock) {
        require(transfer.author == author)
        val prior = pendingKeys()
        prior.firstOrNull { it.transfer.transaction.id == transfer.transaction.id }?.let {
            require(it.backend == backend && it.transfer.transaction.encoded() == transfer.transaction.encoded())
            return@synchronized
        }
        val reserved = reservedLocations()
        require(prior.size < 100 && transfer.inputs.none { Triple(it.block, it.ordinal, it.index) in reserved })
        saveKeys(prior + PendingKey(backend, transfer))
    }

    /** Commit uncertainty before the physical write; this fact never resets on retry. */
    fun markKeyMayHaveSent(backend: SaitoBackend, transfer: SaitoNftTransfer) = synchronized(SaitoLocalState.lock) {
        val prior = pendingKeys()
        val row = prior.single { it.transfer.transaction.id == transfer.transaction.id }
        require(row.backend == backend && row.transfer.author == author && row.transfer.transaction.encoded() == transfer.transaction.encoded())
        saveKeys(prior.map { if (it.transfer.transaction.id == transfer.transaction.id) it.copy(mayHaveSent = true) else it })
    }

    fun releaseUnsubmittedKey(backend: SaitoBackend, transfer: SaitoNftTransfer, verifyNeverSubmitted: () -> Unit) =
        synchronized(SaitoLocalState.lock) {
            require(transfer.author == author)
            verifyNeverSubmitted()
            val prior = pendingKeys()
            val row = prior.single { it.transfer.transaction.id == transfer.transaction.id }
            require(row.backend == backend && row.transfer.transaction.encoded() == transfer.transaction.encoded() && !row.mayHaveSent)
            saveKeys(prior.filterNot { it.transfer.transaction.id == transfer.transaction.id })
        }

    private fun saveKeys(records: List<PendingKey>) {
        val rows = JsonArray().apply { records.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.transfer.transaction.encoded())
            addProperty("mayHaveSent", record.mayHaveSent)
        }) } }
        file("key-reservations").save(EncryptedLocalFile.Contents(1, rows.toString()))
        if (!exists("key-reservations-guard")) file("key-reservations-guard").save(EncryptedLocalFile.Contents(1, author))
        check(pendingKeys().map { Triple(it.backend, it.transfer.transaction.encoded(), it.mayHaveSent) } ==
            records.map { Triple(it.backend, it.transfer.transaction.encoded(), it.mayHaveSent) })
    }

    private fun reservedLocations(): Set<Triple<BigInteger, BigInteger, Int>> {
        val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
        val inputs = pending().flatMap { it.payment.inputs } + pendingMints().flatMap { it.candidate.inputs } + pendingKeys().flatMap { it.transfer.inputs } + StoreOrderStore(context, author).read().flatMap { it.purchase.inputs } + StoreListingStore(context, author).read().flatMap { it.listing.inputs }
        require(inputs.all { locations.add(Triple(it.block, it.ordinal, it.index)) })
        return locations
    }

    /** Called under the shared physical-send lock. Every saved candidate remains reserved. */
    fun reserve(backend: SaitoBackend, payment: SaitoPayment) = synchronized(SaitoLocalState.lock) {
        require(payment.author == author)
        val prior = pending()
        require(prior.size < 100 && prior.none { it.payment.transaction.id == payment.transaction.id })
        val locations = reservedLocations()
        require(payment.inputs.none { Triple(it.block, it.ordinal, it.index) in locations })
        save(prior + Pending(backend, payment))
    }

    /** Historical observations never release inputs or authorize transmission. */
    fun recordInclusions(backend: SaitoBackend, observations: Map<String, SaitoPaymentChain.Inclusion>): List<Pending> =
        synchronized(SaitoLocalState.lock) {
            val prior = pending()
            if (observations.isEmpty()) return@synchronized prior
            require(observations.keys.all { id -> prior.any { it.backend == backend && it.payment.transaction.id == id } })
            observations.values.forEach(::validateInclusion)
            val records = prior.map { record ->
                observations[record.payment.transaction.id]?.let { record.copy(lastInclusion = it) } ?: record
            }
            save(records)
            records
        }

    private fun save(records: List<Pending>) {
        val rows = JsonArray().apply { records.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id)
            addProperty("candidate", record.payment.transaction.encoded())
            record.lastInclusion?.let { observation -> add("lastInclusion", JsonObject().apply {
                addProperty("height", observation.height.toString())
                addProperty("hash", observation.hash)
                addProperty("tipHeight", observation.tipHeight.toString())
                addProperty("tipHash", observation.tipHash)
                addProperty("tipTimestamp", observation.tipTimestamp.toString())
            }) }
        }) } }
        file("pending").save(EncryptedLocalFile.Contents(1, rows.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, author))
        check(pending().map { Triple(it.backend, it.payment.transaction.encoded(), it.lastInclusion) } ==
            records.map { Triple(it.backend, it.payment.transaction.encoded(), it.lastInclusion) })
    }

    private fun readInclusion(row: JsonObject): SaitoPaymentChain.Inclusion {
        require(row.keySet() == setOf("height", "hash", "tipHeight", "tipHash", "tipTimestamp"))
        require(row.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString })
        fun unsigned(name: String): BigInteger {
            val text = row.get(name).asString
            require(text.matches(Regex("[1-9][0-9]{0,19}")))
            return BigInteger(text)
        }
        val timestamp = unsigned("tipTimestamp")
        require(timestamp.bitLength() <= 63) // Checked conversion also supported on Android 10.
        return SaitoPaymentChain.Inclusion(unsigned("height"), row.get("hash").asString,
            unsigned("tipHeight"), row.get("tipHash").asString, timestamp.toLong())
            .also(::validateInclusion)
    }

    private fun validateInclusion(value: SaitoPaymentChain.Inclusion) {
        require(value.height.signum() > 0 && value.height.bitLength() <= 64)
        require(value.tipHeight >= value.height && value.tipHeight.bitLength() <= 64 && value.tipTimestamp > 0)
        for (hash in listOf(value.hash, value.tipHash))
            require(hash.matches(Regex("[0-9a-f]{64}")) && hash.any { it != '0' })
    }
}

