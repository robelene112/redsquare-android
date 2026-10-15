# Android 10 full failure at recreated private queued UI

[Run38813599061](https://github.com/robelene112/redsquare-android/actions/runs/38813599061)
at b218c1f762eb67ce1f86fc33e20ffb2659ae5efb completed FAILURE.
Source/core/debug/test/release assembly and lint pass; core114tests,0failures,
0ignored. The native scope stops at ChatQueuePreviewTest:13cases12PASS1FAIL,
257.095seconds. Across executed classes and recovered/restored stages:
50reported cases49PASS1FAIL,12completion records and3actual process deaths
(two photo-queue recoveries and one mute-policy recovery).
Later queue process stages, administrator/group and all subsequent complete-suite
gates remain unexecuted. This run is not full acceptance.

The failing case is anOfflinePrivateMessageRemainsCancellableAfterReopening.
At the exact pinned source, line485 recreates and opens the conversation,
line486 invokes physicalChatAction and line487 would wait for Queued.
open(queued=true) establishes the saved QUEUED original/exact content, not the
recreated composer's visible state. The physical lookup returns no Cancel target
within its unchanged five-second bound and fails at334/call486. The later enabled
check, coordinate tap and exact restoration/no-wire tail are not reached in this
case. The raw instrumentation shell exit is0 despite its reported JUnit failure;
the harness correctly rejects the missing OK summary and the overall job fails.

Root viewed all four retained original PNGs and parsed all four XML files.
Before recreation, long-message and preview-end captures show Queued/Cancel and
the complete stored text can be scrolled to its final lines. Reopened and failure
captures instead show Loading messages... and the disabled empty composer,
with no Queued/Cancel control. Screenshot and hierarchy are separate observations,
not an atomic pair. This supports a missing fixture UI-readiness prerequisite;
it does not explain why restoration took longer or exclude a performance issue.

Fixture-only38a61205 moves the existing twenty-second waitFor(Queued) immediately
before the physical target check. It changes neither deadline nor48dp/system-bar,
enabled/tap/payload/identity/backend/no-repeat assertions. Source reviews report
Spec0/Standards0; corrected native runtime outcome remains pending.

The earlier keyboard class passes all10cases. Public font diagnostics again
observe actual1.3 profiles and restoration to rendered1.0. The first prior-null
setting reads back canonical1.0; the other three explicit1.0 settings restore
exactly. This exercises the narrow API29 implicit-default branch and does not
claim permanent persisted absence. Recovery raw logs/checkpoints/continuity and
partial-counts.json remain independently inspectable. No encrypted installation
snapshots, private production data, live transaction or phone observation retained.
