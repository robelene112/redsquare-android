# Private Chat tracer: unsupported fixture scrolling

Android 10 run 38965766119 at `f80b035a4eb5f4a2f166055cf02b6a2086560b03` completed FAILURE: nine cases, eight passed, one failed, 68.261 s. The new case now passes the exact editable-text assertion, but stops at line 197: the inherited post photo helper calls performScrollTo on a Chat attachment with no scrollable ancestor. The photo is already visible. This is a fixture failure before editing, alert publication or tapping, not the intended navigation RED. Those later new-case assertions remain unexecuted.

The correction requires the Chat photo to be displayed directly, retaining post scrolling and the same final image-pixel assertion. The other eight social cases passed. Separate source run 38965756141 succeeded at the same source, including release assembly and lint. Raw logs, original PNG/XML and metadata are retained. The instrumentation command’s exit 0 does not change the failed harness conclusion.
