# Ticket #9 interim review

Read-only independent review at c14246a against6dd511d. Five-case Android10/17
proof and final full remain pending. This is not ticket acceptance.

## Standards

One documented breach: initial follow-load failure leaves ready=false, has no
in-session retry, disables Follow, but says Try again and continues Loading.
This conflicts with the standard requiring useful recovery guidance.

One heuristic Mysterious Name: generic PublicStringStore still names its loaded
public strings envelopes, including the new account-identity consumer. Rename
the property to values while preserving the on-disk cache format.

Storage generalization has a third concrete consumer; no secret exposure or
live-network testing breach found.

## Specification

1. P2: Following results share the general history and are globally truncated
   to its newest100 entries before filtering by followed identity. A full newer
   unrelated general cache can discard all valid older followed-author results
   even after successful archive reads. Retain the active Following history
   independently or preserve its fetched results before retention.
2. P2: follow choices have no in-session reload after initial temporary storage
   failure. Add an accessible retry and replace indefinite Loading with an
   honest failure state, without overwriting previous choices.

No scope creep. Pending runtime evidence is separate from source defects.

Standards: 1 documented breach and 1 heuristic finding.
Spec: 2 actionable findings, both P2.

The Standards reviewer also separately noted that sequential followed-author
requests publish only when all finish; a later timeout discards already verified
earlier responses. Assess this availability/recovery edge during fixes. No
physical performance result or hard standards classification was claimed.
