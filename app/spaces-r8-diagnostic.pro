# Keep app names and entry points so the controlled comparison can use the existing native observers.
-dontobfuscate
-keep,allowoptimization class nl.davidengelen.redsquare.** { *; }

# libjingle calls into WebRTC Java classes through JNI.
-keep class org.webrtc.** { *; }
