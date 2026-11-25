package nl.davidengelen.redsquare.spaces.service

import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelRecord
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom.Command
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom.Status

/** Called inside the service's serialized exchange. Active calls have no persistent presence. */
internal class SpaceVoiceRooms {
    private data class Key(val space: String, val channel: String)
    private data class AccessTerm(val generation: String, val epoch: Long, val reopenedAt: Long,
        val channelAccess: Long?, val previousDeparture: Long?)
    private data class Session(val wallet: String, val device: String?, val id: String, val term: AccessTerm, val expiresAt: Long,
        val mutedByStaff: Boolean = false)
    private class Room {
        val sessions = linkedMapOf<String, Session>()
        val retired = mutableMapOf<String, Long>()
    }

    private val rooms = mutableMapOf<Key, Room>()

    fun exchange(space: String, channel: String, caller: String, device: String?, command: Command, sessionId: String?,
        journal: SpaceJournal, now: Long): SpaceVoiceRoom {
        val snapshot = journal.snapshot
        val record = requireNotNull(snapshot.channels[channel])
        require(!snapshot.dissolved && snapshot.protectedChat == null && caller !in snapshot.bannedMembers)
        require((record.kind ?: record.profile?.kind) == SpaceChannelKind.VOICE && journal.canAccessChannel(caller, channel))

        val key = Key(space, channel)
        pruneExpired(now)
        val existing = rooms[key]
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
        var admitted = false
        var status = Status.AVAILABLE
        when (command) {
            Command.OBSERVE -> {
                status = when {
                    !canJoin(journal, record, channel, caller, device) -> Status.MEMBERS_ONLY
                    room.sessions.size == SpaceVoiceRoom.CAPACITY && previous == null -> Status.FULL
                    else -> Status.AVAILABLE
                }
            }
            Command.JOIN -> {
                val id = requireNotNull(sessionId)
                status = when {
                    !canJoin(journal, record, channel, caller, device) -> Status.MEMBERS_ONLY
                    previous != null && (previous.id != id || previous.device != device) -> Status.ALREADY_JOINED
                    id in room.retired || room.sessions.values.any { it.id == id && it.wallet != caller } -> Status.SESSION_ENDED
                    previous == null && room.sessions.size == SpaceVoiceRoom.CAPACITY -> Status.FULL
                    else -> {
                        require(existing != null || rooms.size < MAX_ACTIVE_ROOMS) { "Room service is at capacity" }
                        require(room.retired.size < MAX_RECENT_SESSIONS) { "Room has too many recent sessions" }
                        room.sessions[caller] = Session(caller, device, id, term(journal, record, caller),
                            Math.addExact(now, SpaceVoiceRoom.LEASE_MILLIS),
                            mutedByStaff = previous?.takeIf { it.id == id }?.mutedByStaff == true)
                        rooms[key] = room
                        admitted = true
                        Status.AVAILABLE
                    }
                }
            }
            Command.RENEW -> {
                if (previous != null && previous.id == sessionId && previous.device == device) {
                    room.sessions[caller] = previous.copy(expiresAt = Math.addExact(now, SpaceVoiceRoom.LEASE_MILLIS))
                    admitted = true
                } else status = Status.SESSION_ENDED
            }
            Command.LEAVE -> {
                if (previous != null && previous.id == sessionId && previous.device == device) retire(room, previous)
            }
            Command.MUTE, Command.REMOVE -> {
                val target = requireNotNull(room.sessions.values.singleOrNull { it.id == sessionId })
                require(journal.canRestrictWriting(caller, target.wallet, channel))
                if (command == Command.REMOVE) retire(room, target)
                else room.sessions[target.wallet] = target.copy(mutedByStaff = true)
            }
        }
        val participants = room.sessions.values.map {
            SpaceVoiceRoom.Participant(it.wallet, it.id, !it.mutedByStaff && journal.writeRestrictedUntil(it.wallet, channel, now) == null,
                it.expiresAt)
        }
        return SpaceVoiceRoom(status, admitted, SpaceVoiceRoom.CAPACITY, participants, now)
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
    }

    private fun pruneExpired(now: Long) {
        rooms.values.forEach { room ->
            room.sessions.entries.removeIf { it.value.expiresAt <= now }
            room.retired.entries.removeIf { it.value <= now }
        }
        rooms.entries.removeIf { it.value.sessions.isEmpty() && it.value.retired.isEmpty() }
    }

    companion object {
        private const val MAX_ACTIVE_ROOMS = 512
        private const val MAX_RECENT_SESSIONS = 24
    }
}
