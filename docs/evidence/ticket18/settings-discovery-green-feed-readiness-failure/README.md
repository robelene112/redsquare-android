# Settings discovery and feed readiness checkpoint

Source585074d. Android10 run38123194656: ordinary10 PASS/48.452s;
actual cold recovery1 PASS/5.216s. Android17 run38123195572: ordinary9 of10
PASS/88.834s, shared-name case fails at initial scroll (UsernamePreviewTest.kt:59).
Settings-first own-key discovery and all other ordinary cases pass on both APIs.
Android17 process stage is not reached; no whole-suite Android17 GREEN claim.

Root individually viewed Settings PNGs on both APIs and inspected their separate
XMLs: exact verified name and own-wallet status are visible. Root viewed Android17
failure PNG/XML: Loading posts, disabled Refresh, no post/author. New post readiness
and observing an Archive request precede parsing and publication of the response.
The next fixture waits for enabled Refresh before its unchanged post/name checks.
That correction is not present in these runs and is not yet runtime-proved.

Android10 actual death uses PID4569, recovery4678, unchanged wallet/backend/frame
except checkpoint phase and recovered PID. Cold opening performs a real read-only
namecheck with zero automatic broadcasts; explicit Retry emits original bytes.
Independent pinned WASM/Elliptic verifies the actual default-published-registrar
request and routing signatures. No production write, namespace ownership, live
chain inclusion, browser DOM, power-cut or physical-device claim.

Root viewed both process PNGs and inspected separate XMLs. Before-death PNG shows
locked name, Retry and pending copy; cold-recovery PNG renders only a status strip.
Cold XML/runtime establish recovery, without a consistent bitmap or proven capture
cause. Original logs, public checkpoints, PNG/XML are retained verbatim. The
independent-frame report is derived separately. #19 remains open.
