package nl.davidengelen.redsquare.talk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack
import java.io.Closeable

/** Capture starts only after an explicit native permission/acceptance action. */
internal class TalkCapture(context: Context, microphone: Boolean, camera: Boolean) : Closeable {
    val audio: AudioTrack? = null
    val video: VideoTrack? = null
    init { check(permitted(context, camera)) }
    fun visible(value: Boolean) {}
    fun controls(microphone: Boolean, camera: Boolean) {}
    fun switchCamera() {}
    fun silence() {}
    fun silenceVideo() {}
    override fun close() {}
    companion object {
        fun permitted(context: Context, camera: Boolean): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!camera || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
}
