package nl.davidengelen.redsquare.talk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import nl.davidengelen.redsquare.saito.RtcPlatform
import nl.davidengelen.redsquare.saito.SaitoLocalState
import org.webrtc.*
import java.io.Closeable
import java.util.UUID

/** Capture is created on the call IO actor after native consent and permission.
 * Tracks belong to this call, while the shared factory/connections outlive individual screens.
 */
internal class TalkCapture(private val context: Context, microphone: Boolean, camera: Boolean,
    private val authorize: () -> Unit, private val microphoneAllowed: () -> Boolean = { true },
    private val cameraAllowed: () -> Boolean = { true }, val token: String) : Closeable {
    private val factory = RtcPlatform.factory(context)
    private val lock = Any()
    private val audioSource: AudioSource
    val audio: AudioTrack
    var video: VideoTrack? = null
        private set
    private var videoSource: VideoSource? = null
    private var helper: SurfaceTextureHelper? = null
    private var capturer: CameraVideoCapturer? = null
    private var showing = TalkRuntime.visible
    private var cameraWanted = camera
    private var recording = false
    private var closed = false
    @Volatile var cameraFailed = false
        private set
    @Volatile var canSwitch = false
        private set
    @Volatile var switchFailed = false
        private set
    @Volatile var front = true
        private set

    init {
        check(permitted(context, camera))
        audioSource = factory.createAudioSource(MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        })
        audio = factory.createAudioTrack("talk-audio-${UUID.randomUUID()}", audioSource)
        try {
            authorized { audio.setEnabled(microphone && microphoneAllowed()) }
            if (camera) camera(showing)
        }
        catch (failure: Exception) { close(); throw failure }
    }

    /** Only quick track enable/disable calls are made by a lifecycle/identity interruption. */
    fun silence() = synchronized(lock) {
        if (!closed) { audio.setEnabled(false); video?.setEnabled(false) }
    }
    fun silenceAudio() = synchronized(lock) { if (!closed) audio.setEnabled(false) }
    fun silenceVideo() = synchronized(lock) { if (!closed) video?.setEnabled(false) }

    fun visible(value: Boolean) {
        showing = value
        if (!value) silenceVideo()
        camera(value && cameraWanted)
    }
    fun controls(microphone: Boolean, camera: Boolean) {
        check(permitted(context, camera))
        authorized { audio.setEnabled(microphone && microphoneAllowed()); cameraWanted = camera }
        camera(camera && showing && cameraAllowed())
    }
    private fun camera(requested: Boolean) {
        val enabled = requested && cameraAllowed() && TalkRuntime.visible
        if (closed) return
        if (!enabled) {
            silenceVideo()
            if (recording) { capturer?.stopCapture(); recording = false }
            return
        }
        if (capturer == null) {
            check(permitted(context, camera = true))
            val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) Camera2Enumerator(context) else Camera1Enumerator(false)
            canSwitch = enumerator.deviceNames.size > 1
            val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull() ?: error("No camera is available")
            front = enumerator.isFrontFacing(device)
            val source = factory.createVideoSource(false).also { videoSource = it }
            val texture = SurfaceTextureHelper.create("SaitoTalkCamera", RtcPlatform.graphics(context)).also { helper = it }
            capturer = requireNotNull(enumerator.createCapturer(device, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(description: String) { cameraFailed = true }
                override fun onCameraDisconnected() { cameraFailed = true }
                override fun onCameraFreezed(description: String) { cameraFailed = true }
                override fun onCameraOpening(name: String) {}
                override fun onFirstFrameAvailable() {}
                override fun onCameraClosed() {}
            })).apply { initialize(texture, context, source.capturerObserver) }
            video = factory.createVideoTrack("talk-video-${UUID.randomUUID()}", source)
        }
        check(!cameraFailed)
        if (!recording) { requireNotNull(capturer).startCapture(640, 480, 24); recording = true }
        authorized { video?.setEnabled(cameraAllowed() && TalkRuntime.visible) }
    }
    private inline fun authorized(enable: () -> Unit) = synchronized(SaitoLocalState.lock) {
        authorize()
        synchronized(lock) { check(!closed); enable() }
    }
    fun switchCamera() {
        switchFailed = false
        if (!closed && recording && canSwitch) capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) { front = isFrontCamera }
            override fun onCameraSwitchError(description: String) { switchFailed = true; TalkRuntime.record("camera-switch", IllegalStateException("Camera switch unavailable")) }
        })
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            audio.setEnabled(false); video?.setEnabled(false); closed = true
        }
        if (recording) runCatching { capturer?.stopCapture() }
        recording = false
        runCatching { capturer?.dispose() }; capturer = null
        synchronized(lock) {
            video?.dispose(); video = null; audio.dispose(); audioSource.dispose()
            videoSource?.dispose(); videoSource = null
        }
        helper?.dispose(); helper = null
    }
    companion object {
        fun permitted(context: Context, camera: Boolean): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!camera || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
}
