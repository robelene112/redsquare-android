# Android 17 complete native regression

[Run39029992811](https://github.com/robelene112/redsquare-android/actions/runs/39029992811)
completed SUCCESS at exact source `e85076aaae54605fcf73ad9f424565a3dcffb46a`,
scope `full-android17`, with approved public feed reads enabled.

Raw instrumentation records contain406 PASS,96 completion records and29
intentional interruption outputs, with no assumptions or explicit failure file.
The exact completed classes/counts and interrupted paths match the accepted #16
baseline plus the six Chat GIF cases. Core114 tests, zero failures/ignored;
source assembly, release and lint pass.

Chat GIF6/0/58.695s, existing Posts GIF preview3/0/19.863s and posting6/0/30.118s.
Private process cold recovery1/0/6.117s and restoration1/0/0.15s pass after the
fixture-only viewport correction. The first failed full attempts remain failed.
The complete suite also includes the protected process recoveries, both public
feed reads, public Chat readiness, immutable5-to8 updates and final diagnostic.
These establish controlled/emulator behavior, not Google or live Chat delivery.

`SHA256SUMS` covers236 retained originals/outputs. The original artifact inventory
covers401 device files,712 parsed XML entries and849 PNG-signature entries,
including captures repeated in archives. Five missing optional diagnostics are
explicitly listed, not counted as valid captures. The profile aggregate is
excluded only after exact concatenation proof; status-only diagnostic packets
without class/test names are not counted as passing tests.

Root viewed three original Chat captures: `private-owner-reply-selected`,
`group-official-queued_cancel-online-unsent`, and
`group-official-queued_cancel-sent-credits`. The private selection shows its reply,
caption and owned GIF frame; group captures show retained credits/caption before
explicit Send and after forwarding. Ordinary scrolling clips the group preview
above its credits, so these PNGs do not independently show a complete GIF image
or animation. Other retained PNGs have no new visual claim. Static captures are
not timing, motion or atomic screenshot/hierarchy evidence.

No physical Pixel, live Giphy key/query, production writes, provider account,
node change or store acceptance. Both complete SDK runs are successful. Final evidence review and publication
were still pending when this packet was retained.
