# Editable wallet transfer restoration, #128

Parent [#117](https://github.com/robelene112/redsquare-android/issues/117), SP6.
Review base: `3e77f05ec6635181798897108c681cbfb291aa4a`.
Product last changed: `62f4146cb2eca09d21e566c1f833bd0eb5d10a6a`.
Source/debug checks: `78bc22eda545039dea5be51db5b0c7aa6b574016`.
Native fixture: `c39a60dafd9ecc8f5082533bc9be8e920819f88f`.

Both independent review axes accept this source and the complete controlled native
evidence, including the stated protected-credential observer correction.

The active transfer task and exact recipient, amount, fee and memo now survive
Activity recreation. A saved editable draft carries its original public identity
and selected backend. A mismatched identity/node cannot mount the task; once both
current values resolve, a mismatched draft clears. Explicit task dismissal clears
its editable state. Existing form appearance and decimal input handling remain.

Only editable data is restored. The existing WalletTransfers owner is recreated,
its old operation is canceled on disposal, and review/candidate/authentication
state remains ephemeral. Restoration loads current records and balances without
preparing or sending. A restored task requires another explicit review and real
system authentication. Existing uncertain/submitted records and original signed
retry candidates keep their separate encrypted storage and recovery semantics.

## Controlled native qualification

| Whole transfer scope | Parent | Native outcome |
| --- | --- | --- |
| Android10 atc39 | [43842990373](https://github.com/robelene112/redsquare-android/actions/runs/43842990373) | 21 normal completions; one intentional interruption with recovery |
| Android17 atc39 | [43842990410](https://github.com/robelene112/redsquare-android/actions/runs/43842990410) | 21 normal completions; one intentional interruption with recovery; protected credential capture qualified |

Both whole parents pass their original native goals. Assessment uses the narrow
protected-credential observer correction described below, with the initial observer
failure retained. No failed-parent prefixes are assembled.

The existing complete transfer scope additively executes three new methods:

- All four exact editable values and the active task survive recreation before
  review, without creating a pending transfer or sending a transaction.
- A real PIN challenge is canceled, then Activity recreation returns to the same
  editable details. A second real challenge is interrupted by actual Activity
  destruction/replacement. Both leave zero outgoing transactions and no pending
  record. Fresh review and a third real system challenge precede the sole owned
  submission, whose recipient/amount/fee/memo match the entered values.
- Actual public backend/wallet stores are changed, followed by Activity reload.
  Another node/wallet receives an empty new task; returning to the original scope
  cannot resurrect the discarded task. Full original wallet/options and backend
  are independently restored after the Activity closes.

The context-change method uses actual public stores and Activity reload. Existing
financial status scenarios independently retain real Settings and Android wallet
import-picker journeys. This is not a claim that the new method itself performs
those UI changes. Owned fixture state, PIN and resources restore independently of
UI success, preserving primary failures. Common frame checks retain full wallet/
options, selected node, expected local-peer writes and zero report publication.

Prior wallet task/design, both density/font/theme matrices, interruptible account
motion, transfer/recovery, receiving QR, large-text input, and actual process-loss
checks remain. Per-class bounds stay180/240seconds; normal UI/auth deadlines are
unchanged. The declared process-kill method is an intentional interruption with
an original signed-frame checkpoint. Separate fresh-process recovery and PIN-
cleanup methods complete normally. The original frame, wallet/backend and distinct
process IDs are retained, with explicit review and a fresh grant before exact retry.
It is not classified as a normal successful completion.

## Reviewed fixture bridge and source checks

Source78-to-nativec39 changes exactly four AndroidTest files. The first method
uses existing identical field-entry/readiness helpers. The interrupted-auth case
calls the actual stopped Activity's recreate on Android's main thread and observes
old-instance destruction and a distinct replacement within10seconds. The credential
window keeps focus. All later exact-field/no-review/no-send/fresh-review/PIN checks
remain. This avoids ActivityScenario.recreate's forced RESUMED precondition on
Android10 while a separate credential Activity is in front.

Four narrow calls in the legacy financial journeys close a retained transfer via
the actual enabled Close transfer action, observe Wallet within5seconds, close it,
then perform their existing explicit Wallet reopen. The helper rejects duplicate
close actions and leaves the existing import path alone when no task is mounted.
All original financial/storage/assertion text and180/240-second bounds remain.
Product/core/resources/build/workflow/harness/controlled-peer entries are identical.
The canonical bridge records226417 identical Git entries and its exact serialization.

[Source43840718101](https://github.com/robelene112/redsquare-android/actions/runs/43840718101)
assembles debug/test APKs and passes lintDebug at78. Core reports224 tests/44classes,
zero failures/ignored; core and gateway tasks are explicitly FROM-CACHE. Lint has
zero errors,95 warnings and three hints. Final native runs compile their actual
fixture source. This source-qualified bridge does not relabel the lint run or
establish signed release, physical performance or whole-app acceptance.

## Retained observations

Original metadata, logs, server-digest-verified ZIP/TAR bytes and extraction checks
remain outside the checkout at
`/mnt/storage/redsquare-android-artifacts/wallet-input128`:

- 43838304573@d6fe8a1b0 is the adverse before-review case: all four fields are
  entered and observed, then actual Activity recreation loses the task and returns
to Home. The original10-second editable-task wait fails. This is the original bug.
- 43839259130@62f4146cb passes that complete one-method correction. It is an
intermediate observation, not whole-ticket acceptance.
- 43840717689@78 fails the interrupted-auth method before recreation executes:
ActivityScenario first attempts RESUMED while the actual credential window leaves
the app STOPPED. The first two methods and cancellation/first recreation are
observed, but do not form an accepted prefix. The system credential window also
prevents its failure PNG capture; that secondary failure is suppressed and kept.
- 43840718070@78 passes the three new methods and original account/design groups,
then three financial cases fail on duplicate Send SAITO matches: the restored task
title and separately opened Wallet button. The whole parent stays failed.
- 43841998220@268 is canceled as superseded after the known legacy reopen correction
is prepared. Its three new methods complete, but the canceled parent contributes
no acceptance. All partial originals remain retained.

Eight retained receipts contain712 original ZIP files and1944 regular TAR members.
Only the complete corrected SDK parents and source/debug check count for acceptance.

The original Android17 credential screenshot is a valid703-byte black PNG. The
original helper/note already permits protected/refused system screenshots and
requires the actual PIN control before entry; its XML contains cred_pin_pad.
This image provides no visible credential proof. The initial generic PNG-size
observer incorrectly rejected it. The retained failed observation and initial
assessor SHA remain in protected-credential-observer.json and externally in
assess-initial-png-heuristic.py. The corrected observer permits only this exact
path/size with its original qualification and actual PIN XML. Every other PNG
threshold, native goal and deadline remains. Report corrected observer assessment
with protected-capture qualification, never unchanged-observer PASS.

## Acceptance and limits

Both independent Spec and Standards source reviews report zero remaining findings.
Each complete native parent's actual source, original bytes, status identities,
process continuity and stated qualifications are independently reviewed. Both axes independently verify all1278 indexed copies against external source
bytes/hashes and accept the eight-receipt712ZIP/1944TAR inventory, exact canonical
source bridge and corrected protected-capture observer with zero remaining findings.

These are controlled emulators, local peers and simulated funding. Real physical/
OEM, live funding/provider/service and Play gates remain separate. No production
node/provider operation or public financial write occurred. Public beta remains
0.1.131 until combined final qualification and signed delivery in#131. Continue
#129–#130 sequentially, then#131, without another approval.
