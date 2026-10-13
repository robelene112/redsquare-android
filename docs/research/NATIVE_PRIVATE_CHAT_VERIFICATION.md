# Native private Chat verification

Ticket #25 is active. Review base: c102f891f6041f08199905728d302e19b7588abd.
Protocol contract: NATIVE_PRIVATE_CHAT_CONTRACT.md. Independent reference:
NATIVE_PRIVATE_CHAT_REFERENCE.md and JSON. Evidence: docs/evidence/ticket24/.
Separate axis reports: docs/evidence/ticket24/REVIEW.md.

## Protocol increments

First receive tracer: missing-decoder RED31s, GREEN1/56s. Native send tracer:
missing-create RED28s, GREEN2/58s, matching exact pinned ciphertext, signature,
network and Archive envelope bytes. Initial shared regression core76 and both
APKs passed2m59s. Imported exchanged-key preference/fallback genuine RED3/one
comparison failure55s; minimum implementation passes fullcore77/zero failures,
errors or ignored in1m. Source checkpoint7dbaba3.

First review: Spec2 findings (legacy publickey and passphrase case), Standards2
findings (passphrase case and coercing malformed formatter arrays). No native
transport, UI or recovery completion was claimed.

Relay JSON tracer: missing serialization/read API RED26s, GREEN4/56s against
independent existing-client inner JSON. Legacy field genuine RED5/one failure33s;
alias minimum GREEN5/57s preserves the complete exported backup. Uppercase saved
passphrase independent genuine RED6/one failure34s; exact-string minimum
GREEN6/56s. Correctly signed malformed formatter genuine RED7/one failure40s;
string-primitive guards pass fullcore81/zero failures/errors/ignored in1m.
Source checkpoint42dfd6f. Reference hash886ff62259151851e7a1131d264d805bd4c192f1e312b42376099c2711ee1c06:
four valid vectors plus three malformed; two byte-identical isolated executions.
Actual pinned Crypto returns empty plaintext for ct-array and throws for iv/s
arrays. The initial reference harness assumed an exception for ct; this was
corrected to record its actual result before passing output was generated.

Bounded fixes review: Spec0; Standards0 new hard findings plus one actionable
Duplicated Code heuristic. Required-string validation is now shared between
formatter and decrypted body. Shared validation cleanup remains passing in core81; both APKs build3m24s.

## Peer discovery

New actual local-WebSocket test targets authenticated advertised Archive/Relay
services. The first APK compile failed2m18s with both missing services API and a
fixture constructor regression. Adding a non-function final option broke old
trailing-lambda bindings. A known-invalid subsequent build was stopped143.
The option now precedes the existing final Archive callback; existing assertions
unchanged. Clean RED2m33s contains exactly one missing services compiler error.
Minimum implementation awaits authentication on the same selected connection
before exposing bounded, strict-UTF8 service records. Core81/debug/testAPK source build passes3m24s. Focused emulator socket result
still pending.

These are incremental protocol/source checks. Complete native DM UI, Relay
send/receive, available-history recovery, private cache, Block/Mute semantics,
both public backend contracts, process interruption and final fullSDK acceptance
remain unfinished. No physical Pixel, live Chat publication, positive funding,
closed-app push, permanent history or new phone APK claim.
