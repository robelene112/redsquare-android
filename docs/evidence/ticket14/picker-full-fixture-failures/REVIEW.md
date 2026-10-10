# Full verification rejected: landscape viewport and peer restart

Both runs use source `1d12a94688891f3f0b95f45aba5a6f70e5f0b88d` and enable
separately approved public reads. Both source/core/release/lint stages pass;
neither complete device run passes, so neither establishes final acceptance.

Android 10 [38073794569](https://github.com/robelene112/redsquare-android/actions/runs/38073794569)
reaches 83 successful individual cases, 16 passing completion records and nine
actual process-crash stages, with one failed image-picker case. Preparation,
newest-text picker death/recovery, candidate reconciliation and cold queued-image
delivery pass. The actual picker selection returns and the prepared gallery
exists, but its displayed assertion fails in landscape. Individually inspected
system-picker-failure.png shows a 640 by 320 window occupied by development
header and backup controls; the gallery is below the visible composer area.
The accompanying XML records rotation1. Neither capture establishes visible
image pixels or text in this landscape viewport. This layout limitation belongs
to #35 polish; the recovery fixture must still observe a new Activity and
prepared result in landscape before rotating back for exact portrait pixels,
text, Send and zero-transaction assertions.

Android 17 [38073796590](https://github.com/robelene112/redsquare-android/actions/runs/38073796590)
reaches 52 successful individual cases, seven passing completion records and
three actual process-crash stages. Its sole failure is the existing pending-like
backend switch fixture: rebinding the original endpoint throws EADDRINUSE. It
stops before image tests and provides no media-rotation result. Exact
MockWebServer5.4.0 source disables reuse for port0 and enables it for explicit
ports. The correction adopts the existing PostQueuePreviewTest reservation
idiom so both owner listeners bind the same explicit OS-selected port with
reuse enabled. Retained TCP state is a supported hypothesis, not proved from
these artifacts. No retry or replacement endpoint is permitted; all backend
isolation and exactly-one-action assertions remain unchanged.

Counts come from status0 individual-case records, explicit OK completion lines
and Process crashed lines in retained logs. Partial failed groups are included
only in individual successes, never in passing completion records. No APK,
physical-device acceptance, production write or transaction publication.
