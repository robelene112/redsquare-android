# Android 10 protected photo history follow-up

Run 38970020926 at `1487ed1b26ac9ee960367c114cd30ee7b9c405fb` completed SUCCESS: both protected photo-history cases pass in 82.302 s, retaining the same 12/16-photo growth, extra-photo, prior-history/draft and installation-restoration assertions. API 29 intentionally does not exercise the API 30+ .new atomic-write fault. Production is unchanged from 84eb9704. The earlier failed complete photo run remains FAILURE; this storage-only follow-up does not include UI/picker/process scopes.

Effective device properties show heapsize 512m and an empty heapgrowthlimit property, with the retained Android 10 fingerprint/AVD configuration. The added println memory observations do not appear in the instrumentation output, so process Runtime.maxMemory and memoryClass were not retained. This successful run does not prove the earlier 16 MiB OOM’s cause or an app fix. The next logging correction uses instrumentation stream status so those observations are actually retained. No heap, count or deadline changed.

This headless storage fixture takes no native screenshots. The raw best-effort capture-retrieval output is preserved as received and is not a usable capture archive. No screenshot claim is made for these cases. Core result and exact run metadata are retained.
