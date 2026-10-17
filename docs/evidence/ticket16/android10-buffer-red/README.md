# Android10 animated-buffer crash diagnostic

Exact3bd3ce88fdfbac73239b995e95941f10f39e1903 run39012426099 FAILED.
Recovery passes, selected animation starts and instrumentation reports a process
crash. Search and posting remain unexecuted. Five original files are hashed.

The newly retained crash buffer identifies SIGABRT on AnimatedImageTh inside
nl.davidengelen.redsquare.dev. Its abort reports failure to obtain JNIEnv, with
ByteArrayStream::read followed by GIF frame decoding in the native stack.
This establishes the failing read path, not a guarantee about all GIF failures.

Representative Android10 AOSP source supports a targeted correction:
[Java decoder](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/graphics/java/android/graphics/ImageDecoder.java)
chooses the byte-array path for heap buffers with an array.
[Native decoder](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/core/jni/android/graphics/ImageDecoder.cpp)
delegates creation to its stream adapter.
[Stream adapter](https://android.googlesource.com/platform/frameworks/base/+/android-10.0.0_r1/core/jni/android/graphics/ByteBufferStreamAdaptor.cpp)
reads heap arrays through JNI, while direct buffers use a memory stream and a
retained global reference. The three fetched public source hashes/URLs are in
aosp-inputs.json. This source tag is not claimed as the exact emulator build.

Correctioneb840006 supplies a direct read-only buffer, using base Buffer.rewind
for Android10 compatibility. Existing byte/dimension/output/concurrency bounds,
clipping, lifecycle and native assertions remain unchanged. Both bounded source
reviews have no findings. The proposed mechanism is supported; corrected runtime
success remains pending. No new provider/key, production transaction or node change.
