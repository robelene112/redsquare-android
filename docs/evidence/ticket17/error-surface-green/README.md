# Provider refusal recovery checkpoint

Sourcec9b114a868cace2c75527c523eac939c53f30bc5, whole reviewbasec0f2da7e.
Source reviews Standards0/Spec0 after actual-view Retry correction. This is a
bounded #17 failure-recovery improvement, not completed public playback.

| Evidence | Actual result | Scope |
| --- | --- | --- |
| [Preflight](preflight/README.md) | Local core/debug/test3m31s | Earlier retired143 is not PASS; coreUP-TO-DATE |
| [Android10](android10/README.md), run39004102308 | SUCCESS,4/0,22.074s | Full controlled video class; bitmap limits explicit |
| [Android17](android17/README.md), run39004103787 | SUCCESS,4/0,56.491s | Same class; targeted refusal text visibly retained |
| [Source CI](source-ci/README.md), run39004102110 | SUCCESS,5m43s | Core/gatewayFROM-CACHE, debug/test/release/lint; no device |

The [original controlled RED](../error-surface-red/android17/README.md) remains
4cases/1failure/68.276s ated0afa19, exactly missing delayed explanation. Stronger
visible-bounds and fourth-response text assertions were added after that RED.
Keeping the browser on101/150 now preserves its ordinary explanation until
user retry/close. Other failure disposal and minimum viewport behavior remain.
The UI no longer claims that a video can only be watched externally when the
numeric error does not establish that precise cause. Retrying remounts the
actual browser view rather than accepting READY from an unattached replacement.

Forty-nine raw files are hashed across preflight(3), both SDKs(22each) and
sourceCI(2). Root viewed14 originals and parsed14 XMLs. Several bitmaps omit
fields, explicitly excluded in SDK notes; semantic bounds are not proof of
pixels or physical-device performance. No decoded real media or provider cause
is inferred from controlled text. Actual public run39004585910 is pending at
this exact source, using the same second signed reference after this changed
ordinary error handling. Actual Playing/no-autoplay/close/feed/no-write gates
remain unchanged. No header/identity/login/proxy/node/account change. Beta24
unchanged; #17 remains open. Giphy source research is preparation for a later
sequential ticket, not parallel implementation.
