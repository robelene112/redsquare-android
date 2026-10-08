# Feed launch timing protocol

Ticket #4 prepares repeatable launch measurements. The accepted targets belong
on the owner's Pixel 7 running Android 17, not an emulator. Physical runs remain
in final device acceptance (#35), consistent with reviewing the completed app.

Timing starts when Android starts the cold activity after process termination.
Timing ends when a rendered feed with visible posts can accept scrolling and
header actions. Instrument this using Activity.reportFullyDrawn after the first
rendered frame containing available posts. Keep wallet creation, archive loading
and cache decoding off the main thread. Android's initial display time alone is
not an interactive-feed measurement. An empty-feed/error screen is not a passed
post-loading target.

Record the commit, APK version and package, OS/build, device model, system theme,
network validation state, selected server, cache state and visible post count.
Do not record private wallet material or network payloads in timing reports.

For cached cold launches, populate the feed normally, force-stop the app and
launch five times without clearing data. Keep the same backend, stable Wi-Fi,
normal animations and no debugger. All five interactive-feed times must be at
most 2,000 ms; report individual values and the median, not only the best run.
An offline cached run separately verifies that reading does not wait for a peer.

For first-install launches, use five fresh installations of an isolated
performance package on the same physical device, never erase an owner's wallet.
A release-equivalent performance variant is prepared in #35. There must be no
feed cache or wallet before each run. Keep stable Wi-Fi and record backend
availability. All five interactive-feed times must be at most 5,000 ms. Failed
loads remain failures with evidence; do not discard them as timing outliers.
Record cache-read and first live-read modes separately.

Emulator observations are diagnostics of instrumentation and launch behavior.
They do not establish either physical target. Ordinary app updates must preserve
wallets and cached data regardless of whether a timing target has been met.
