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
debug/test assembly pass1m48s; focused first-green38034593176 pending. Deletion,
global cache ordering, recovery and final acceptance remain incomplete.

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
