# Keep app names and entry points so the controlled comparison can use the existing native observers.
-dontobfuscate
-keep,allowoptimization class nl.davidengelen.redsquare.** { *; }

# AndroidJUnitRunner is in the separate test APK and calls this app-side facade.
-keep,allowoptimization class androidx.tracing.Trace { *; }

# libjingle calls into WebRTC Java classes through JNI.
-keep class org.webrtc.** { *; }
