# Recreated-writer navigation follow-up

Initial121 acceptance492c61b64 did not exercise shared module navigation after
image preparation. A later source inspection found that Post/Profile
DisposableEffect(state) captured a non-null selected URI from recreation. That
value could permanently reject guarded module switching after preparation finished.

At exact sourceadcc1e9ca804372cd7b6a11c276168d106fcc328, the guards use existing
live preparation, picker, busy and readiness state. They no longer test the stale
captured selection. The scope is exactly two product files plus tests/harness;
review base0b549754 already contains the initial122feed polling fix. That work is
retained and is not qualified as completed122behavior by this follow-up.

Original red43770278729 at56e11217c fails all three post/reply/quote module
transitions at their unchanged five-second bound. Its profile phase is not reached.
Complete corrected selected-image runs43772033795(Android10) and43772035861
(Android17) each finish all five post/reply/quote/avatar/banner methods. Every case
retains one selected read through actual Activity recreation, switches Red Square
to Stack and back through the real header, then verifies exact encrypted text/
media and no transaction. Each module switch remains bounded at five seconds, each class
at180seconds. Stack cache/reading-window state is captured and restored too.

Both independent Standards and Spec reviews reproduce fresh GitHub metadata,
full logs, server ZIP digests, every original/index and every assessor assertion.
There are10normal completions, no assumptions or intentional starts,157native raw
originals and40separate PNG/XML members. Source/debug43770760846 verifies55originals,
core224/44 with zero failures/ignored and explicit core/gateway FROM-CACHE, plus
lint0errors/94warnings/3hints. The failed red remains retained and excluded.

Original ZIPs/logs/member extracts and assessment tools remain outside Git at
`/mnt/storage/redsquare-android-artifacts/selected-images121-navigation`.
The initial eight-scope evidence remains valid only at its recorded sources.
This targeted follow-up does not replace whole-app/beta qualification under131.
Both axes verify all final packet copies with zero remaining findings. The
five-second bound applies to each individual module switch.
