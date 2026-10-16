# Ordinary reply tap lifecycle, Android10

Run [38931710840](https://github.com/robelene112/redsquare-android/actions/runs/38931710840)
completed SUCCESS at source `182d38433d36131184837fbd3681cddfa2378b1c`.
Focused `social-push` scope reports4passing ordinary cases in27.376seconds,
exit0. Core114tests and debug/application-test compilation pass. Release assembly,
lint and full suites are not part of this focused scope. Android17 is skipped
in this run and is recorded separately.

The four cases are actual reply publication, physical dismissal with bounded
replay silence, warm tap preserving latest unsent text/photo and the newly added
no-Activity tap/recreation case. These are the ordinary cases in the current
source, not the historical four-case set from the earlier tap evidence.

The new case starts with no MainActivity, publishes a verified reply alert,
physically taps the system card and displays the exact signed reply in
Conversation. Recreating that Activity retains the target. Back returns to the
feed; recreating again retains the feed and no Conversation is observed for2s.
Reopening New post restores the original saved text and red image. The image
pixel assertion, exact encrypted draft content, unsigned/unqueued state, full
fixture-wallet/backend invariants, no outgoing transaction/report/Relay and
controlled-peer health assertions all execute and pass. Fixture activities stop
before restoration of the original installation.

Root viewed all five original cold-case PNGs and parsed all11original XMLs.
Cold alert shows the actual system card over the launcher, destination and
recreated captures show the exact reply, consumed capture shows the feed, and
restored-draft capture shows Earlier saved text and the red image. Both cold
Conversation captures still say Loading replies; this does not establish a fully
loaded thread at capture time. PNG/XML capture is not atomic.

Instrumentation and the controlled Archive peer remain alive throughout. No
MainActivity means no open application screen, not a dead process or actual FCM
wake-up. Real transport, actual public backend delivery, physical Pixel timing,
remaining forms/settings/mentions and whole#32 acceptance remain outstanding.
No production node, service or wallet was changed. Public beta24 remains current.

`SHA256SUMS` preserves original copied logs, metadata and captures. READMEs are
interpretation and are deliberately outside that original-file manifest.
