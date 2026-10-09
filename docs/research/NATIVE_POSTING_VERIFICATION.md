# Native posting and drafts

Current nested regression: actual five-case37971949876 fails only the selected
reply context at line78,32.55seconds. Clicking an existing reply did not change
the selected parent before Reply; the public image confirms the original root's
author in the composer. Public log/image retained ticket09/nested-reply-red.
Minimal850c098 makes verified conversation post text open that post's context
through the existing navigation stack. Five-case37972847178 is running; source
build passed, runtime result pending. Ticket#10 was reopened after premature
closure without completed acceptance. This remains delivery9 in progress.

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
core/debug/test assembly passes1m47s. Two-case green37967156376 at41113e1 passes Android10 in10.831seconds; the
peer receives the signed zero-payment type4 frame, pending copy inspected and
public log/image retained send-green.

Source caveat to resolve before acceptance: current immediate peer.use close
cancels the socket after enqueue and can race actual outbound delivery. Inspect
the external packet test and keep a proper session lifetime. Archive observation,
replies, pending recovery/retry behavior, funded text handling, independent new
constructor/hop proof and final review/full checks still require work. No ticket
completion or live publication is claimed.


Third case at1094a0a adds actual observed availability and subsequent feed
visibility. Controlled external peer indexes the exact received signed native
transaction, serves it only for a matching signature, and supplies it on general
reads. It verifies through the app, not an internal persistence assertion. A
new onTransaction fixture callback preserves existing trailing archive callbacks;
AtomicReference safely publishes across socket threads. Guarded .dev/emulator
setup clears only synthetic postdraft records between cases, never wallet keys.
Actual three-case red37968017392 is running.37967863806/37967946413 superseded
during setup for fixture corrections; neither is functional proof. No archive
observation production fix yet. Core new constructor/hop vectors still needed.


Actual red37968017392 at1094a0a passes the draft case but fails waiting for
Available (line64), and the previously green explicit-Send case fails waiting
for any received frame (line145),36.005seconds. Public log/composer image
inspected and retained observation-red. Minimalfdcc528 holds the authenticated
peer open through exact-signature Archive queries, retrying6 times at2-second
intervals. Only a matching verified author/text changes status to Available.
Back refreshes selected feed and clears a confirmed draft on verified saving.
Unobserved candidates stay durable. Local core/debug/test assembly passes1m30s.
Three-case green37968817970 atfdcc528 passes Android10 in8.119seconds.
Public Available and actual posted-feed images/log inspected and retained
observation-green. No final acceptance yet.


Independent lower-boundary proof at2e62a19: posting-reference.cjs uses the pure
network-disabled/read-only reference runner, pinned WASM0.3.2 and Elliptic6.6.1,
public unsafe scalar1/2, no application/wallet/DB initialization. Both libraries
verify transaction/hop signatures and WASM serialized roundtrips. New public
SaitoTransactionTest matches network bytes, compact signature, detached envelope,
type4/hop bytes, and restored bytes including Unicode/newlines/HTML punctuation.
Focused core test passes26seconds. This is construction compatibility, not live
post acceptance. No new complete16-case core run yet.

Fourth UI case at9331e52 replies to the selected root, requires original parent
and thread IDs and the independently known scalar1 parent point as recipient,
then signed archive observation and visible reply in Conversation after Back.
Actual red37969745626 at9331e52 passes3 cases and fails missing Reply (line70),
12.732seconds. Public log/Conversation image inspected/retained reply-red.
Minimal68b7e9d adds Reply/shared composer, signed parent/thread and ordered
recipients from verified parent output keys, sequential output indices, separate
reply draft records and zero-only restore guards. Missing FeedPost import caused
local compile failure, corrected indca6cf1; local core16/debug/test assembly
passes1m27s. Four-case green37970835482 atdca6cf1 passes Android10 in10.785seconds; public
log/reply-in-thread image inspected and retained reply-green.37970627296 was
canceled after the missing import failure and is not runtime proof. This ticket
remains incomplete.


At23f163a the pure reference helper supports ordered scalar1/2 recipient outputs,
checks each manual preimage against WASM-generated signature hash, independently
signs/verifies with WASM+Elliptic and serialized roundtrips. Existing root vector
remains valid; new recipient reply with output index1 plus hop matches the native
public constructor/restore/envelope/broadcast. Both focused core tests pass24s.
New complete17-case core run is pending.

Fifth UI case c348f14 selects an independently signed existing reply from a root
conversation, then presses Reply. It requires that reply's author as context,
its signature as parent, the original root as thread and output order of self,
parent author, inherited root author. It verifies actual received bytes and
archive availability. Five-case red37971949876 is running; no nested-navigation
fix yet. Existing Conversation text currently has no post-selection action.
