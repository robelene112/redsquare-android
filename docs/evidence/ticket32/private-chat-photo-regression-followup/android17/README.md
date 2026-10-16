# Android 17 complete photo regression follow-up

Run 38970017629 at `1487ed1b26ac9ee960367c114cd30ee7b9c405fb` completed SUCCESS. All ten Chat photo UI cases pass in 103.91 s under the unchanged 120-second class ceiling. Both actual queued-photo process recoveries/restorations pass: private PID 4940 to 5011 and group PID 5120 to 5196. Same original, full wallet/backend/history, protected group metadata, native photo pixels and controlled raw/Relay checks pass; delivery remains unconfirmed. Both protected photo-history cases pass in 73.503 s.

Production is unchanged from 84eb9704. The new photo phase println messages are not retained in the instrumentation output, so this success does not explain the earlier timeout/capture interruption. The next logging correction changes only observation transport to instrumentation stream status. Existing assertions, counts, per-step deadlines and class ceiling remain unchanged. The raw failed run remains separately recorded as FAILURE.

PNG/XML and public signed-fixture envelopes/checkpoints are retained; no encrypted installation originals are copied. The headless storage class takes no screenshots, and its raw best-effort capture output is not a usable archive. This is controlled regression evidence, not FCM, public Chat delivery, physical-phone or full application acceptance.
