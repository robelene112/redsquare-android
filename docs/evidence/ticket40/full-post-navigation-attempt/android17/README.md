# Full Android17 attempt, post navigation fixture

Run38763901887 at47a73f87 remains FAILED. The stronger Chat keyboard class
passes4/4 in45.381s, including physical Back/dismissal, retained text/photo,
restored controls, physical reopening and exactly one explicit Send. Other
completed logs are indexed separately. Posting reports18cases17PASS/1FAIL95.842s.
Sole failure is unfinishedCompositionSurvivesBackendNavigationAndActivityRecreationWithoutPublishing
at175: missing Settings immediately after typing. This source predates the
platform IME-dismissal/visible-Settings correction7789600b and later profile
navigation/matcher corrections. No failure-specific post-window capture exists.
Subsequent backend/recreation assertions and remaining full gates are unexecuted;
no complete acceptance from this failed run. Corrected focused posting/profile10
runs pass separately and do not turn this Android17 attempt into SUCCESS.

All12 keyboard XML files parsed;12 original PNGs retained. Root inspected four
keyboard-open originals. Privacy, exact multiline draft, image when selected and
native Send remain above actual IME. Snapshots are separate, not every transition
frame. New full17run38768073681 at3366d4b3 includes the corrections, public reads
enabled; its actual outcome remains pending. Full10run38765859044 atede is also
active. Production is identical720e2007/prepared22; public21 remains current.
No production node, wallet, service, chain, credential, store or physical Pixel
operation/acceptance is claimed. Scoped original installation restoration remains
on device; only public synthetic UI evidence is retained.
