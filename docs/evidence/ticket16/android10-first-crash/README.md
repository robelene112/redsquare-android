# First Android10 GIF run, interrupted by a process crash

Run39011893710 at a2753fe8cb00fbb848b2225c498a34a3fbffd016 FAILED.
The recovery/retry/close/save/recreation method passes. The selected-animation
method starts, reaches the visible static owned thumbnail, then instrumentation
reports `shortMsg=Process crashed.` No final JUnit count/time is available.
The search method and posting class did not execute. No animation, selection,
restoration or posting success is inferred for Android10.

Twelve original files are hashed. Root viewed all four PNGs and parsed all four
XMLs. They show recovery text, restored authored work, and the bounded static
preview with provider/creator/source controls. These are observations before the
crash, not a crash explanation. Emulator host output does not establish its cause.

The original harness omitted the device crash buffer. Commit1b7ecb93 adds that
buffer on a failed JUnit summary, preserving failures and deadlines. A changed
diagnostic run is required before choosing an application fix. No cause, fix or
successful rerun is claimed here. No production/key/account/node change.
