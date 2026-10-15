# Source review for conversation mute

Fixed base:40f28872cb9360d36c834fa632fd463dad3be72c.
Reviewed source:3a9649443a98397248874b7f0ab77dfbc70c6d5f.
Originating requirements: GitHub issues29 and1. Both independent read-only
review agents inspected the nonempty committed diff and commit list. Device
process proof, final full runs and publication remain separate acceptance gates.

## Standards

Hard Standards breaches: **0**. Remaining actionable Fowler judgments: **0**.

The combined changes preserve the reviewed boundaries: wallet-scoped encrypted
policy with legacy defaults; authenticated retention before optional alerts;
signed-original deduplication; fresh selection, policy and group-membership
checks at publication; generic notification payloads; and distinct private/group
draft memory with retained mutexes and protected-work authority.

Native fixtures exercise observable UI, peer ACKs, OS notifications and protected
recovery seams. Scoped cleanup preserves primary failures. Process snapshots
remain device-only; exported evidence contains public metadata and digests.
Focused scopes and full verification include the new fixtures without removing
earlier gates. Earlier duplication, cancellation-publication, membership-lock
and fixture-readiness findings are resolved in this source.

## Spec

**Spec0**. No missing source requirement, incorrect implementation or scope creep
found. Private/group controls persist wallet-local choices across both backends,
explain account mute/block distinctions and preserve readable messages. Live
alerts follow authenticated retention, deduplicate originals and freshly check
selection, policy, permission and group membership. Generic notification content
preserves private-message privacy.

Legacy policies default the new field safely; account-policy updates preserve
it. Draft recovery remains identity/backend/conversation scoped, with shared
initialization/write locking and noncancellable queue-transition publication.
Failure and process fixtures use approved seams with protected snapshots and
qualified evidence. Closed-app delivery, previews and physical-device acceptance
are not inferred.

Standards0, Spec0. Earlier process-proof finding is fixed by mandatory nonempty
pending-guard size metadata before cold recovery; both correction reviews0.

Evidence checkpoint56b3c17e...010454a1 also clears both axes. Reviewers verified
171 hashes and73 XML across the five new retained sets. Raw process proof
records259-byte guard metadata and PID6413 to6890; prepared19 production
equivalence and qualified compile failure are verified. Final full results and
public publication remain pending.

Both final SDK evidence reviews also report Spec0/Standards0. Android10 proof
893 hashes/329 XML and Android17 proof898 hashes/331 XML confirm all expected
class records,25 deaths per SDK and separate six cold/update observers. Both
complete workflows succeeded at56b3c17e. Publication/direct checks are retained
in signed-preview19-published; actual physical/closed-app/store gates remain
with their existing tickets.
