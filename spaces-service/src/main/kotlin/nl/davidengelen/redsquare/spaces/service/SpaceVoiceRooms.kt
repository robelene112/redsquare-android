package nl.davidengelen.redsquare.spaces.service

import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelRecord
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom.Command
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom.Status
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom.SignalDelivery
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import java.security.MessageDigest

/** Called inside the service's serialized exchange. Active calls have no persistent presence. */
internal class SpaceVoiceRooms {
    data class CommandData(val session: String?, val contact: SpaceVoiceSignalKey.Contact? = null,
        val packet: SpaceVoiceSignalKey.Packet? = null, val receivedThrough: Long = 0)

    private data class AccessTerm(val generation: String, val epoch: Long, val reopenedAt: Long,
        val channelAccess: Long?, val previousDeparture: Long?)
    private data class Session(val contact: SpaceVoiceSignalKey.Contact, val device: String?, val term: AccessTerm,
        val expiresAt: Long, val mutedByStaff: Boolean = false) {
        val wallet get() = contact.wallet
        val id get() = contact.session
    }

    private class Inbox {
        private val seen = linkedSetOf<List<Byte>>()
        private val pending = mutableListOf<SignalDelivery>()
        private var next = 1L
        private var offeredThrough = 0L

        fun receive(receivedThrough: Long): List<SignalDelivery> {
            require(receivedThrough in 0..offeredThrough)
            pending.removeIf { it.sequence <= receivedThrough }
            return pending.take(SpaceVoiceRoom.SIGNALS_PER_PAGE).also { page ->
                page.lastOrNull()?.let { offeredThrough = maxOf(offeredThrough, it.sequence) }
            }
        }

        fun add(packet: SpaceVoiceSignalKey.Packet) {
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(packet.json().toString().toByteArray(Charsets.UTF_8)).toList()
            if (fingerprint in seen) return
            require(pending.size < MAX_QUEUED_SIGNALS) { "Room peer signal queue is full" }
            require(pending.sumOf { it.packet.ciphertext.length } + packet.ciphertext.length <= MAX_QUEUED_BYTES) {
                "Room peer signal bytes are at capacity"
            }
            pending += SignalDelivery(next, packet)
            next = Math.addExact(next, 1)
            seen += fingerprint
            if (seen.size > MAX_RECENT_SIGNALS) seen.remove(seen.first())
        }

        fun removeFrom(session: String) { pending.removeIf { it.packet.from == session } }
    }

    private class Room {
        val sessions = linkedMapOf<String, Session>()
        val retired = mutableMapOf<String, Long>()
        val inboxes = mutableMapOf<String, Inbox>()
    }

    private val rooms = mutableMapOf<SpaceVoiceSignalKey.Address, Room>()

    fun exchange(address: SpaceVoiceSignalKey.Address, caller: String, device: String?, command: Command, args: CommandData,
        journal: SpaceJournal, now: Long, recordModeration: (String, () -> Unit) -> Unit): SpaceVoiceRoom {
        val channel = address.channel
        val sessionId = args.session
        val snapshot = journal.snapshot
        val record = requireNotNull(snapshot.channels[channel])
        require(!snapshot.dissolved && snapshot.protectedChat == null && caller !in snapshot.bannedMembers)
        require((record.kind ?: record.profile?.kind) == SpaceChannelKind.VOICE && journal.canAccessChannel(caller, channel))

        pruneExpired(now)
        val existing = rooms[address]
        if (record.archived) {
            if (existing != null) existing.sessions.values.toList().forEach { retire(existing, it) }
            return SpaceVoiceRoom(Status.ARCHIVED, false, SpaceVoiceRoom.CAPACITY, emptyList(), now)
        }
        val room = existing ?: Room()
        room.sessions.values.toList().forEach { entry ->
            if (!canJoin(journal, record, channel, entry.wallet, entry.device) || entry.term != term(journal, record, entry.wallet))
                retire(room, entry)
        }
        val previous = room.sessions[caller]
        val own = previous?.takeIf { it.id == sessionId && it.device == device }
        var admitted = false
        var status = Status.AVAILABLE
        var signals = emptyList<SignalDelivery>()
        var moderated: Session? = null
        when (command) {
            Command.OBSERVE -> {
                status = when {
                    !canJoin(journal, record, channel, caller, device) -> Status.MEMBERS_ONLY
                    previous != null -> Status.ALREADY_JOINED
                    room.sessions.size == SpaceVoiceRoom.CAPACITY -> Status.FULL
                    else -> Status.AVAILABLE
                }
            }
            Command.JOIN -> {
                val contact = requireNotNull(args.contact)
                require(contact.session == sessionId && contact.wallet == caller && contact.verify(address))
                status = when {
                    !canJoin(journal, record, channel, caller, device) -> Status.MEMBERS_ONLY
                    previous != null && (own == null || previous.contact != contact) -> Status.ALREADY_JOINED
                    contact.session in room.retired || room.sessions.values.any { it.id == contact.session && it.wallet != caller } -> Status.SESSION_ENDED
                    previous == null && room.sessions.size == SpaceVoiceRoom.CAPACITY -> Status.FULL
                    else -> {
                        require(existing != null || rooms.size < MAX_ACTIVE_ROOMS) { "Room service is at capacity" }
                        require(room.retired.size < MAX_RECENT_SESSIONS) { "Room has too many recent sessions" }
                        room.sessions[caller] = Session(contact, device, term(journal, record, caller),
                            Math.addExact(now, SpaceVoiceRoom.LEASE_MILLIS),
                            mutedByStaff = own?.mutedByStaff == true)
                        rooms[address] = room
                        admitted = true
                        Status.AVAILABLE
                    }
                }
            }
            Command.RENEW -> {
                if (own != null) {
                    signals = room.inboxes.getOrPut(own.id, ::Inbox).receive(args.receivedThrough)
                    room.sessions[caller] = own.copy(expiresAt = Math.addExact(now, SpaceVoiceRoom.LEASE_MILLIS))
                    admitted = true
                } else status = Status.SESSION_ENDED
            }
            Command.LEAVE -> {
                if (own != null) retire(room, own)
            }
            Command.MUTE, Command.REMOVE -> {
                val target = requireNotNull(room.sessions.values.singleOrNull { it.id == sessionId })
                require(journal.canRestrictWriting(caller, target.wallet, channel))
                if (command == Command.REMOVE || !target.mutedByStaff) {
                    recordModeration(target.wallet) {
                        if (command == Command.REMOVE) retire(room, target)
                        else room.sessions[target.wallet] = target.copy(mutedByStaff = true)
                    }
                    moderated = target
                }
            }
            Command.SIGNAL -> {
                val sender = requireNotNull(own)
                val packet = requireNotNull(args.packet)
                require(sender.id == packet.from && room.sessions.values.any { it.id == packet.to })
                room.inboxes.getOrPut(packet.to, ::Inbox).add(packet)
                admitted = true
            }
        }
        val participants = room.sessions.values.mapNotNull {
            if (it == moderated && command == Command.REMOVE) return@mapNotNull null
            SpaceVoiceRoom.Participant(it.contact,
                it != moderated && !it.mutedByStaff && journal.writeRestrictedUntil(it.wallet, channel, now) == null, it.expiresAt)
        }
        return SpaceVoiceRoom(status, admitted, SpaceVoiceRoom.CAPACITY, participants, now, signals)
    }

    private fun canJoin(journal: SpaceJournal, record: SpaceChannelRecord, channel: String, wallet: String, device: String?): Boolean =
        wallet !in journal.snapshot.bannedMembers && journal.canContributeChannel(wallet, channel) &&
            (record.visibility == SpaceVisibility.PUBLIC && device == null || device != null && device in record.devices && device.split(':')[1] == wallet)

    private fun term(journal: SpaceJournal, record: SpaceChannelRecord, wallet: String) =
        AccessTerm(record.generation, record.epoch, record.contributionStartedAt, record.accessStartedAt[wallet],
            journal.snapshot.membershipEndedAt[wallet])

    private fun retire(room: Room, session: Session) {
        room.sessions.remove(session.wallet)
        room.retired[session.id] = session.expiresAt
        room.inboxes.remove(session.id)
        room.inboxes.values.forEach { it.removeFrom(session.id) }
    }

    private fun pruneExpired(now: Long) {
        rooms.values.forEach { room ->
            room.sessions.values.filter { it.expiresAt <= now }.forEach { retire(room, it) }
            room.retired.entries.removeIf { it.value <= now }
        }
        rooms.entries.removeIf { it.value.sessions.isEmpty() && it.value.retired.isEmpty() }
    }

    companion object {
        private const val MAX_ACTIVE_ROOMS = 512
        private const val MAX_RECENT_SESSIONS = 24
        private const val MAX_QUEUED_SIGNALS = 48
        private const val MAX_QUEUED_BYTES = 256 * 1024
        private const val MAX_RECENT_SIGNALS = 128
    }
}
