# Native author actions verification

Active #14, fixed base `6c657d4`; boundaries #1 seams1–4. Primary source contract
NATIVE_EDIT_DELETE_CONTRACT.md. No production writes or node mutation authorized.

Independent public vectors use existing pinned WASM0.3.2/Elliptic6.6.1 reference
crypto, unsafe scalar1/2, original signed feed fixture. Named read-only runtime,
dependency, generator and public fixture mounts; network-none/read-only/cap-drop/
no-new-privileges/env-i/512MiB container, no Saito app initialization. Original
identity remains intact in edited metadata. Actor, target and corrupted-signature
negative samples included. Never fund these identities.

First read-edit tracer actually red25s, then green43s after independently
verifying optional update_tx as an authorized edit. Unsigned edit_ts supplies
neither authority nor ordering. Additional actor/target/signature and untrusted
ordering regressions pass in core28/debug/test assembly1m48s. Observable native
own-post edit tracer prepared. Run38033270587 failed at a fixture navigation
race: it scrolled before the saved composer closed. This does not establish the
missing edit control. A bounded New post readiness wait was added in7ce6f6b;
run38033592630 also failed before reaching Edit because New post labels both
composer and feed. Retained failure screenshot confirms composer still visible.
527184d waited for list semantics; run38033860604 then waited for a new post
above the retained list position.1f54664 uses the previously verified quote
fixture pattern: wait for Refresh enabled, then scroll to the post. Run38034150449
actually reaches the own conversation and fails at missing Edit (7.402s).
Retained log and inspected screenshot: ticket13/edit-control-red. This establishes
the intended UI red. Three preceding navigation failures establish no product red.

a2f50e6 adds author-only editing in the conversation, encrypted candidate storage,
active identity/backend checks before transmission, exact nested-edit observation
and original-candidate retries. Newer conflicting edits retain authored text and
show explicit conflict status; resolution controls remain planned. Local core28,
debug/test assembly pass1m48s; focused first-green38034593176 passes6.178s on
Android10. The thread/feed/profile text and original identity checks pass;
inspected profile screenshot shows edited text. Retained ticket13/edit-first-green.
Navigation-save reviewfinding fixed in8a680d9/0650743: saving gates input and
actions, callback itself rejects queued input, immutable snapshot precedes IO.
Both source axes confirm resolved. This firstgreen predates those fixes.
Deletion,
global cache ordering, recovery and final acceptance remain incomplete.

Stale-cache tracer38034937825 actually fails after an older response replaces
newer verified text, while the own-edit tracer still passes (suite8.439s).
322177c applies newest signed-edit merging in feed/thread/profile/cache; local
core29/debug/test assembly pass2m6s. Native edit/delete signatures match the
independent vectors; focused core regression26s. Invalid encrypted author records
and queue/backend isolation regression added, Android execution pending.

First cache-green38035438325 and source run38035437877 were refused before any
runner step: GitHub reports recent payments failed or spending limit needs
increasing. This establishes neither a test failure nor a green. Owner notified
and billing question pending. No billing/spending/repository-visibility change.

Bounded Android10 software-emulator attempt started locally at about07:47UTC:
network-none,1.5CPU/3GiB limits, isolated AVD/dev packages, no concurrent build.
Current /dev/kvm absent, host had4.7GiB available and low load before start.
Unlike the earlier unmeasured speed proposal, this is a temporary verification
fallback after hosted execution became unavailable. Stop at the existing bounded
boot/test limits; no persistent emulator or speed/performance claim. Result
ended with exit1 at instrumentation timeout, about13 minutes after start. Both
APKs installed, but instrumentation produced0 bytes and no test result. Container
removed; no persistent emulator. Logs retained ticket13/local-software-attempt.
This provides no cache/flow green. Do not retry unsupported software emulation
as the ordinary development loop. Hosted Android verification remains blocked.

Standards found one cache read/merge/write race in322177c. Working-tree fix adds
PublicStringStore.update under the shared lock, decoding and merging the latest
committed values before save. Sourcefixreview confirms0 outstandingfindings;
build passed after local emulator ended: core30/debug/test1m52s. Dedicated core
history regression preserves the independently signed newer edit against stale
and damaged/unrelated nested edits. This is core ordering proof, not Android
cache recovery acceptance.
Independent actual-native-frame verifier and pinned-receiver native mode prepared,
unexecuted until public controlled frames are retained; no new frame proof claim.

Deletion reader tracer actually RED27s: valid archived delete commands counted
as unreadable posts (expected0,actual2). Minimal reader separates signed deletion
commands without deleting target posts; GREEN41s. A follow-on original-author/
target pairing API tracer initially fails compilation22s because deletion()
does not exist, not an executed authorization failure. New deletion verifier
independently verifies original and command, requires matching author/target
and delete kind; unrelated/wrong-target/edit-command negatives rejected. Full
core32/debug/test assembly GREEN1m58s. No Android deletion screen, removal,
tombstone or server-reconciliation acceptance yet.

Pinned A/B unmodified edit/delete receiver methods executed offline using the
independently verified public reference vectors and an in-memory Archive boundary
adapter. Both preserve original identity, apply only strictly newer edits, reject
unrelated edits/wrong targets, retain the original on unrelated deletion while
archiving that command, and remove the original on authorized deletion. Source
hashes and results: docs/evidence/ticket13/web-reference/receiver.json. This is
receiver-method compatibility evidence, not deployed-node, DOM or Android-write
proof. Named read-only runtime/script/public fixture/cached source mounts,
empty environment, network-none/read-only/cap-drop/no-new-privileges/512MiB.

Standards preliminary review found one author-store issue: signed binding was
checked on reopen after committing. It is now checked before any write, including
ordinary composer records. This preserves prior work on candidate validation
failure. Final review and Android recovery regression remain pending.
