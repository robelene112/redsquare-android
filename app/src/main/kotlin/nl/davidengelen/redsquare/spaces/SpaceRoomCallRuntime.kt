package nl.davidengelen.redsquare.spaces

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.update
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.RtcCallbacks
import nl.davidengelen.redsquare.saito.RtcLease
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.SharedRtcPeers
import nl.davidengelen.redsquare.saito.TalkLink
import nl.davidengelen.redsquare.saito.TalkRoom
import nl.davidengelen.redsquare.talk.CallParticipant
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkCall
import nl.davidengelen.redsquare.talk.TalkCapture
import nl.davidengelen.redsquare.talk.TalkRuntime
import nl.davidengelen.redsquare.talk.TalkService
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One room can own the same foreground audio session as existing Talk calls. */
internal object SpaceRoomCallRuntime {
    private val active = AtomicReference<RoomCall?>()

    suspend fun join(context: Context, authority: SpaceRoomAuthority, destination: SpaceRoomDestination): SpaceVoiceRoom.Status? {
        val call = try { withContext(Dispatchers.IO) { RoomCall(context.applicationContext, authority, destination) } }
        catch (failure: Exception) { authority.close(); throw failure }
        if (!TalkRuntime.visible || !TalkService.available(context) || !active.compareAndSet(null, call)) {
            call.discard()
            return null
        }
        if (!call.reserve()) {
            active.compareAndSet(call, null)
            call.discard()
            return null
        }
        return call.start()
    }

    fun end(token: String) { active.get()?.takeIf { it.token == token }?.end(CallPhase.ENDED) }
    fun interrupt(token: String?) { active.get()?.takeIf { token == null || it.token == token }?.end(CallPhase.INTERRUPTED) }
    fun visibility(visible: Boolean) { active.get()?.visibility(visible) }
    fun controls(token: String, microphone: Boolean?, camera: Boolean?) {
        active.get()?.takeIf { it.token == token }?.controls(microphone, camera)
    }
    fun switchCamera(token: String) { active.get()?.takeIf { it.token == token }?.switchCamera() }

    private class RoomCall(private val context: Context, private val authority: SpaceRoomAuthority,
        private val destination: SpaceRoomDestination) {
        private val keys = authority.newSession()
        val token = keys.contact.session
        private val owner = authority.owner
        private val backend = authority.backend
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val stopped = AtomicBoolean()
        private val wantMicrophone = AtomicBoolean()
        private val wantCamera = AtomicBoolean()
        private val events = Channel<suspend () -> Unit>(64)
        private val outgoing = Channel<SpaceVoiceSignalKey.Packet>(48)
        private data class Presence(val room: SpaceVoiceRoom, val requestedAt: Long) {
            fun allowedUntil(participant: SpaceVoiceRoom.Participant) = requestedAt + participant.expiresAt - room.observedAt
        }
        @Volatile private var presence: Presence? = null
        @Volatile private var capture: TalkCapture? = null
        @Volatile private var receivedThrough = 0L
        private var observer: Closeable? = null
        private val peers = ConcurrentHashMap<String, RtcLease>()
        private val remoteCapture = ConcurrentHashMap<String, SpaceVoiceSignalKey.CaptureState>()
        private val pendingCapture = ConcurrentHashMap<String, SpaceVoiceSignalKey.Packet>()
        private var localCapture = SpaceVoiceSignalKey.CaptureState(1, microphone = false, camera = false)

        fun reserve(): Boolean {
            val link = TalkLink(backend, TalkRoom(keys.contact.key, owner))
            val draft = TalkCall(token, owner, backend, link, null, false, CallPhase.JOINING,
                microphone = false, camera = false, spaceRoom = destination, microphoneAllowed = false)
            while (!stopped.get()) {
                val previous = TalkRuntime.call.value
                if (previous?.phase?.live == true) return false
                if (TalkRuntime.call.compareAndSet(previous, draft)) return true
            }
            return false
        }

        fun discard() { scope.cancel(); authority.close() }

        suspend fun start(): SpaceVoiceRoom.Status? {
            val result = CompletableDeferred<SpaceVoiceRoom.Status?>()
            scope.launch {
                try {
                    requireAccount()
                    val requestedAt = SystemClock.elapsedRealtime()
                    val joined = withTimeout(5_000) { authority.join(keys) }
                    if (!joined.admitted) {
                        result.complete(joined.status)
                        end(CallPhase.ENDED)
                        return@launch
                    }
                    presence = Presence(joined, requestedAt)
                    observer = SaitoLocalState.observeAuthority {
                        capture?.silence()
                        post { requireAccount(); applyCapture() }
                    }
                    if (stopped.get()) { observer?.close(); return@launch }
                    TalkService.prepare(token)
                    withContext(Dispatchers.Main.immediate) {
                        check(!stopped.get() && TalkRuntime.visible)
                        context.startForegroundService(Intent(context, TalkService::class.java).putExtra("token", token))
                    }
                    TalkService.awaitReady(token)
                    applyPresence(joined, requestedAt)
                    result.complete(SpaceVoiceRoom.Status.AVAILABLE)
                    launch { deliverSignals() }
                    launch { poll() }
                    launch { watchCaptureAndDeadline() }
                    for (event in events) {
                        if (stopped.get()) break
                        event()
                    }
                } catch (_: CancellationException) {
                    end(CallPhase.INTERRUPTED)
                } catch (failure: Exception) {
                    TalkRuntime.record("space-room", failure)
                    end(CallPhase.UNAVAILABLE)
                } finally { result.complete(null) }
            }
            return result.await()
        }

        private fun requireAccount() {
            if (stopped.get() || TalkRuntime.call.value?.token != token) throw CancellationException("Room was superseded")
            val current = synchronized(SaitoLocalState.lock) {
                AndroidWalletStore(context).loadExisting().publicKey == owner && AndroidBackendStore(context).load() == backend
            }
            if (!current) throw CancellationException("Room account or node changed")
        }

        private fun currentParticipant(session: String): SpaceVoiceRoom.Participant? {
            if (stopped.get()) return null
            val observation = presence ?: return null
            return observation.room.participants.firstOrNull {
                it.session == session && SystemClock.elapsedRealtime() < observation.allowedUntil(it)
            }
        }

        private fun guardPeer(session: String) {
            check(!stopped.get() && TalkRuntime.call.value?.token == token && currentParticipant(token) != null && currentParticipant(session) != null)
        }

        private suspend fun poll() {
            while (currentCoroutineContext().isActive && !stopped.get()) {
                delay(1_100)
                val at = SystemClock.elapsedRealtime()
                try {
                    val next = withTimeout(3_500) { authority.renew(token, receivedThrough) }
                    if (!next.admitted) { end(CallPhase.INTERRUPTED); return }
                    presence = Presence(next, at)
                    if (currentParticipant(token)?.canSpeak != true && wantMicrophone.getAndSet(false)) capture?.silenceAudio()
                    post { applyPresence(next, at) }
                } catch (denied: SpaceClient.ReadDenied) {
                    end(CallPhase.INTERRUPTED)
                    return
                } catch (timeout: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The last signed lease still has its original deadline. */ }
            }
        }

        private suspend fun deliverSignals() {
            for (packet in outgoing) {
                if (stopped.get()) return
                for (attempt in 0..1) {
                    try {
                        guardPeer(packet.to)
                        withTimeout(2_500) { authority.signal(token, packet) }
                        pendingCapture.remove(packet.to, packet)
                        break
                    } catch (_: SpaceClient.ReadDenied) { break }
                    catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (currentParticipant(packet.to) == null) break }
                    if (attempt == 0) delay(150)
                }
            }
        }

        private suspend fun watchCaptureAndDeadline() {
            while (currentCoroutineContext().isActive && !stopped.get()) {
                delay(300)
                if (currentParticipant(token) == null) { end(CallPhase.INTERRUPTED); return }
                peers.entries.filter { currentParticipant(it.key) == null }.forEach { row ->
                    if (peers.remove(row.key, row.value)) row.value.close()
                }
                if (wantMicrophone.get() && !TalkCapture.permitted(context, camera = false) ||
                    wantCamera.get() && !TalkCapture.permitted(context, camera = true, microphone = false)) {
                    capture?.silence()
                    post { applyCapture() }
                }
            }
        }

        private suspend fun applyPresence(room: SpaceVoiceRoom, requestedAt: Long) {
            requireAccount()
            if (requestedAt != presence?.requestedAt) return
            val own = currentParticipant(token) ?: throw CancellationException("Room lease expired")
            if (!own.canSpeak && wantMicrophone.getAndSet(false)) capture?.silenceAudio()
            val remote = room.participants.filter { it.session != token }
            peers.entries.filter { row -> remote.none { it.session == row.key } }.forEach { row ->
                if (peers.remove(row.key, row.value)) row.value.close()
            }
            remoteCapture.keys.removeIf { session -> remote.none { it.session == session } }
            pendingCapture.keys.removeIf { session -> remote.none { it.session == session } }
            publish { call ->
                call.copy(phase = CallPhase.ACTIVE, since = call.since.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    microphoneAllowed = own.canSpeak, participants = remote.map { participant ->
                        val previous = call.participants.firstOrNull { it.roomSession == participant.session }?.takeIf { peers.containsKey(participant.session) }
                        val media = remoteCapture[participant.session]
                        previous?.copy(microphone = media?.microphone == true && previous.audio != null && participant.canSpeak,
                            camera = media?.camera == true && previous.video != null, roomMaySpeak = participant.canSpeak)
                            ?: CallParticipant(participant.wallet, microphone = false, camera = false,
                                roomSession = participant.session, roomMaySpeak = participant.canSpeak)
                    })
            }
            remote.forEach { participant ->
                if (peers[participant.session] == null) {
                    try { attach(participant) }
                    catch (failure: Exception) {
                        peers.remove(participant.session)?.close()
                        TalkRuntime.record("space-room-peer", failure)
                    }
                }
                else {
                    peers[participant.session]?.refreshPlayback()
                    pendingCapture[participant.session]?.let { outgoing.trySend(it) }
                }
            }
            room.signals.filter { it.sequence > receivedThrough }.forEach { delivery ->
                val sender = remote.firstOrNull { it.session == delivery.packet.from }
                val lease = sender?.let { peers[it.session] }
                if (sender != null && lease != null) {
                    val signed = runCatching { keys.open(sender.contact, delivery.packet) }.getOrNull()
                    if (signed != null) {
                        val media = SpaceVoiceSignalKey.captureState(signed)
                        if (media != null) receiveCapture(sender.session, lease, media)
                        else if (!lease.enqueueSignal(signed.body.get("request").asString, signed.body, signed.id)) {
                            if (peers.remove(sender.session, lease)) lease.close()
                        }
                    }
                }
                receivedThrough = delivery.sequence
            }
            applyCapture()
        }

        private fun attach(participant: SpaceVoiceRoom.Participant) {
            val id = participant.session
            guardPeer(id)
            val contact = participant.contact
            val reference = AtomicReference<RtcLease>()
            fun current() = reference.get()?.let { peers[id] === it } == true && currentParticipant(id) != null
            val lease = SharedRtcPeers.acquire(context, keys.rtcWallet, token, contact.key, backend.peerUrl,
                authority.traversalAvailable, RtcCallbacks(
                    validate = { guardPeer(id) },
                    signal = { original -> check(outgoing.trySend(keys.seal(contact, original.envelope())).isSuccess) },
                    opened = { post { if (current()) updateParticipant(id) { it.copy(connected = true) } } },
                    failed = { post {
                        reference.get()?.let { previous ->
                            if (peers.remove(id, previous)) {
                                previous.close()
                                updateParticipant(id) { it.copy(connected = false, video = null, audio = null, camera = false, microphone = false) }
                            }
                        }
                    } },
                    track = { track -> post {
                        if (!current()) return@post
                        val media = remoteCapture[id]
                        updateParticipant(id) { item -> when (track) {
                            is VideoTrack -> item.copy(video = track, camera = media?.camera == true)
                            is AudioTrack -> item.copy(audio = track, microphone = item.roomMaySpeak && media?.microphone == true)
                            else -> item
                        } }
                    } },
                    trackRemoved = { track -> post {
                        if (current()) updateParticipant(id) { item -> when {
                            item.video === track -> item.copy(video = null, camera = false)
                            item.audio === track -> item.copy(audio = null, microphone = false)
                            else -> item
                        } }
                    } },
                    media = true, scopedSignals = true,
                    remoteAudioAllowed = { currentParticipant(id)?.canSpeak == true && remoteCapture[id]?.microphone == true },
                    remoteVideoAllowed = { currentParticipant(id) != null && remoteCapture[id]?.camera == true },
                ), roomSession = token)
            reference.set(lease)
            peers[id] = lease
            if (stopped.get()) {
                if (peers.remove(id, lease)) lease.close()
                return
            }
            if (!lease.enqueueOffer() && peers.remove(id, lease)) lease.close()
            sendCapture(contact)
        }

        private fun receiveCapture(session: String, lease: RtcLease, media: SpaceVoiceSignalKey.CaptureState) {
            if (currentParticipant(session) == null || peers[session] !== lease || media.sequence <= (remoteCapture[session]?.sequence ?: 0)) return
            remoteCapture[session] = media
            lease.refreshPlayback()
            updateParticipant(session) { it.copy(microphone = it.roomMaySpeak && media.microphone && it.audio != null,
                camera = media.camera && it.video != null) }
        }

        private fun sendCapture(contact: SpaceVoiceSignalKey.Contact) {
            val packet = keys.sealCapture(contact, localCapture, System.currentTimeMillis())
            pendingCapture[contact.session] = packet
            outgoing.trySend(packet)
        }

        fun controls(microphone: Boolean?, camera: Boolean?) {
            if (stopped.get()) return
            microphone?.let { wantMicrophone.set(it && TalkRuntime.visible && currentParticipant(token)?.canSpeak == true) }
            camera?.let { wantCamera.set(it && TalkRuntime.visible) }
            if (!wantMicrophone.get()) capture?.silenceAudio()
            if (!wantCamera.get()) capture?.silenceVideo()
            post { applyCapture() }
        }

        fun visibility(visible: Boolean) {
            if (!visible) { wantCamera.set(false); capture?.silenceVideo() }
            post { applyCapture() }
        }

        fun switchCamera() { post { requireAccount(); capture?.switchCamera() } }

        private suspend fun applyCapture() {
            requireAccount()
            val canSpeak = currentParticipant(token)?.canSpeak == true
            if (!canSpeak || !TalkCapture.permitted(context, camera = false)) wantMicrophone.set(false)
            if (!TalkRuntime.visible || !TalkCapture.permitted(context, camera = true, microphone = false)) wantCamera.set(false)
            try {
                val needsAudio = wantMicrophone.get() && capture?.audio == null
                val needsVideo = wantCamera.get() && capture?.video == null
                if (needsAudio || needsVideo) TalkService.allowRoomCapture(token, needsAudio, needsVideo)
                if (capture == null && (wantMicrophone.get() || wantCamera.get())) {
                    capture = TalkCapture(context, false, false, authorize = {
                        requireAccount()
                        check(!stopped.get() && currentParticipant(token) != null)
                    }, microphoneAllowed = { wantMicrophone.get() && currentParticipant(token)?.canSpeak == true },
                        cameraAllowed = { wantCamera.get() && TalkRuntime.visible }, token = token, eagerAudio = false)
                }
                capture?.controls(wantMicrophone.get(), wantCamera.get())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                wantMicrophone.set(false)
                wantCamera.set(false)
                capture?.silence()
                TalkRuntime.record("space-room-capture", failure)
            }
            val source = capture
            val microphone = wantMicrophone.get() && source?.audio != null
            val camera = wantCamera.get() && source?.video != null
            peers.values.forEach { lease ->
                lease.media(source?.audio?.takeIf { microphone }, source?.video?.takeIf { camera }, token)
            }
            if (localCapture.microphone != microphone || localCapture.camera != camera) {
                localCapture = SpaceVoiceSignalKey.CaptureState(localCapture.sequence + 1, microphone, camera)
                presence?.room?.participants?.filter { peers.containsKey(it.session) }?.forEach { sendCapture(it.contact) }
            }
            publish { call -> call.copy(microphone = microphone,
                camera = camera, localVideo = source?.video?.takeIf { camera },
                microphoneAllowed = canSpeak, canSwitchCamera = source?.canSwitch == true) }
        }

        private fun updateParticipant(session: String, change: (CallParticipant) -> CallParticipant) = publish { call ->
            call.copy(participants = call.participants.map { if (it.roomSession == session) change(it) else it })
        }

        private fun publish(change: (TalkCall) -> TalkCall) = TalkRuntime.call.update { call ->
            if (!stopped.get() && call?.token == token) change(call) else call
        }

        private fun post(action: suspend () -> Unit) {
            if (!stopped.get() && !events.trySend(action).isSuccess) end(CallPhase.INTERRUPTED)
        }

        fun end(phase: CallPhase) {
            if (!stopped.compareAndSet(false, true)) return
            wantMicrophone.set(false)
            wantCamera.set(false)
            capture?.silence()
            TalkRuntime.call.update { call ->
                if (call?.token == token) call.copy(phase = phase, microphone = false, camera = false, localVideo = null,
                    participants = emptyList(), microphoneAllowed = false) else call
            }
            events.close()
            outgoing.close()
            observer?.close()
            peers.values.forEach(RtcLease::close)
            peers.clear()
            CoroutineScope(Dispatchers.IO).launch {
                scope.coroutineContext[Job]?.cancelAndJoin()
                capture?.close()
                capture = null
                try { withTimeoutOrNull(1_500) { authority.leave(token) } }
                catch (_: Exception) { /* The node retires unrenewed presence at its original expiry. */ }
                finally { authority.close(); active.compareAndSet(this@RoomCall, null) }
            }
        }
    }
}
