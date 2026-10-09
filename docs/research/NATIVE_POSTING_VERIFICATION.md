# Native posting and drafts

Ticket#10, delivery slice9 of35, in progress. Review base78163d6. Parent#1
approved application, failure/persistence and independent crypto boundaries.

First vertical case observes actual MainActivity with a controlled signed feed,
opens New post, saves synthetic draft text, recreates the activity and requires
the same editable text. The external peer records public transaction frames
and must observe no publication on saving/reopening. This tests app recovery
and explicit Send separation, not physical process death or draft portability.
Controlled archive requests remain normal verified reads. No production writes.

Actual red37964083012 ateb45228 reaches the signed feed and fails at missing
New post action, line32,3.29seconds. Public log/image inspected and retained
ticket09/draft-red. Minimal7360289 adds root composer plus encrypted local
wallet/backend-scoped drafts. AES256GCM AndroidKeystore key, package/filename
AAD, noBackup storage, AtomicFile/file and directory sync, decrypted readback
before closing. Missing key with existing drafts fails closed. Loads/saves run
on IO; failed saving retains editable text and does not confirm persistence.
Draft safety bound is1MiB of UTF8, not a Saito protocol limit. No Send yet.
Local core/debug/test assembly passes1m33s. Green37964861535 at7360289 passes the draft case on Android10 in4.772seconds.
Public log/recovered editable-text image inspected and retained draft-green.

Primary read-only researchf31ed06 is in NATIVE_POSTING_CONTRACT.md. It records
compatible recipients/hops/transport and practical web validation limits. This
document does not authorize production writes or prove live delivery.



Next case at296d729 observes actual Send, the public peer broadcast and pending
server-availability copy. It requires type4 plus full signed Normal transaction,
zero-valued input/output, one routing hop, matching displayed wallet author,
original text and root-thread derivation. Exact-signature archive returns empty;
no remote acknowledgement or chain proof is invented. Application verification
uses the previously independently checked decoder; new transaction constructor
and hop vectors still require explicit independent reference proof later.
Two-case37965701990 at296d729 passes draft recovery but fails before Send at
text entry (line78): async draft load has not yet enabled the editor. This is
a harness timing failure, not an actual functional Send red. Public log retained
send-editor-race. Correctede1f628a waits for an enabled editor via visible
semantics; actual two-case run37966333736 is running.37965626375 superseded during
setup after completing the controlled peer's pending archive response. No Send
production implementation yet. This ticket is not complete.


Corrected actual red37966333736 ate1f628a passes draft recovery and fails at
missing Send (line82),7.388seconds. Public log/composer image retained send-red.
Minimal41113e1 adds immutable zero-payment Normal creation, detached envelope,
validated restore and peer routing signature; selected authenticated session
submits type4 with no fabricated RPC acknowledgement. AES encrypted v2 draft
record contains text and exact original signed candidate before any transmission;
v1 text-only draft records remain readable. 1MiB text safety limit,5MiB encoded
record bound. Restoring candidate validates signature, author and text. Local
core/debug/test assembly passes1m47s. Two-case green37967156376 is running.

Source caveat to resolve before acceptance: current immediate peer.use close
cancels the socket after enqueue and can race actual outbound delivery. Inspect
the external packet test and keep a proper session lifetime. Archive observation,
replies, pending recovery/retry behavior, funded text handling, independent new
constructor/hop proof and final review/full checks still require work. No ticket
completion or live publication is claimed.
