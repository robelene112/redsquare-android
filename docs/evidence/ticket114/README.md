# Ticket114: integrated Store rentals and Vault access

Status: complete functional rental slice. All six current-source scopes and both
[independent final reviews](reviews.md) pass. No beta was built or promoted;
#115 owns the single combined delivery.

The creator retains the Vault master and deliberately mints separate rental stock,
reviews its deposit and signs the Store listing with actual duration/rights/price.
The renter receives a verified right bound to the reviewed file and actual buyer,
opens that file from the order in native Vault and returns to the original order.
Browser-acquired holdings recover through public signed Store history without an
Android buyer journal. Safe community Vault links retain their signed origin.

Expiry is fixed when listing, not restarted at payment. The first undelegated
renter remains the contract's renter. Assigned remainders and elapsed-end offers
cannot start new payments; authentication and initial enqueue recheck availability.
Uncertain previously sent originals remain recoverable. For overlapping grants,
Vault retains verified alternatives and tries existing loan reads before any
activation. A newer expiry never proves the provider has activated that loan.
Creator reads do not activate a loan merely to open a file. Explicit creator
restoration checks the actual current loan and provider NOW. Offline/exported
copies remain usable after expiry and cannot be remotely recalled.

## Source and verification boundary

Review base: `e5669b50f55aad4620a4527a5f6304de268843c4`.
Product source: `54a188eac885ebdb11962e0a31f76bf72eb27b97`.
Current execution source: `ced166d8cfe1961d60a98ac63a6e85cbad1310e9`.
The [complete tree comparison](source-bridge.json) proves that only three native
test/fixture files differ between product and execution sources. All other 224775
tracked entries are exact. No acceptance is
assembled from the earlier source-qualified failures or partial class passes.

Pinned official source: `0db0062d873369dc013046ed77c97410b5683b64`,
actual saito-wasm 0.3.15 and hash-checked acorn 8.19.0. Actual isolated Store mint,
listing, fulfillment and transfer hooks produce independently signed vectors.
Both SAITO-/USDT-denominated provider receipts and all seven advertised payment
quotes are verified. Applicable payment methods remain dependent on denomination.
The nine focused core rental cases pass, including exact later activation,
original file binding, public browser holdings and actual partial fulfillment.
Complete current core passes 224 cases in 44 suites with no failures or skips.

| Scope | Ordinary classes/cases per SDK | Separate process proofs | Android 10 | Android 17 |
| --- | ---: | ---: | --- | --- |
| Seller | 12 / 21 | two three-phase proofs | [43612128463](https://github.com/robelene112/redsquare-android/actions/runs/43612128463) | [43612131229](https://github.com/robelene112/redsquare-android/actions/runs/43612131229) |
| Purchase | 12 / 50 | two three-phase proofs | [43612123720](https://github.com/robelene112/redsquare-android/actions/runs/43612123720) | [43612126072](https://github.com/robelene112/redsquare-android/actions/runs/43612126072) |
| Vault reading | 4 / 10 | none | [43612292455](https://github.com/robelene112/redsquare-android/actions/runs/43612292455) | [43612295245](https://github.com/robelene112/redsquare-android/actions/runs/43612295245) |

All six are successful whole originals from the same execution source. Seller
retains 24 viewport cells; Purchase retains 36; Vault reading retains 12. Each SDK
also retains four actual distinct-PID interruption/cold-authentication/restoration
proofs. Exact PIDs, original signatures, ordinary class counts and extracted-member
verification are retained in the run ledger. Recreation is not counted as a new
process. The new rental classes add 19 ordinary cases: 3 creator, 8 access and 8 payment.

The owned native seam preserves real UI, PIN, signed peer framing, encrypted
journal/Keystore, document provider and wallet/node restoration. Added journeys
cover creator mint/listing/restoration, received-order navigation, offline export
through recreation/expiry/outage, browser import, mixed old-local/new-public
rights, existing older active loans, expiry during PIN/funding IO, all seven
methods and a USDT-denominated rental. Original financial/read/navigation/modal
and class bounds remain. Historical fixture time is guarded DEBUG/dev/emulator
and affects only local purchase availability; provider role decisions remain
independent actual-WASM observations.

The [run ledger](focused-runs.json) retains whole primary metadata/logs, original
ZIP/digest, source and byte-exact extracted members. Originals are external under
`/mnt/storage/redsquare-android-artifacts/vault-store-planning`. Earlier failed or
cancelled executions stay unaccepted, including wrong observers and provider
adapter failures. The reproduced first listing has only a source-hash-object key
order difference; semantic values and every signed transaction/envelope are exact.

## Undeployed provider prerequisite

[Vault rental recovery patch](../../../integrations/saito/vault-rental-recovery.patch)
SHA256 `b6f4f9108d83290e9bba683dc51c67124bd38ab5b4e87b244166b6115f786857`
is source-only and not activated. It adds server-only restoration and advertisement
`vault-rental-restore`, plus optional atomic metadata owner comparison in Archive.
Actual script/actor/NOW authorization and original signed file bytes are preserved.
Fresh apply/rehearsal, actual isolated SQLite/WASM transitions, browser/server
branches, idempotency and competing updates pass. This is not a fully initialized
community runtime, deployed schema rehearsal or live-provider acceptance.

[Operations](../../OPERATIONS.md) and [owner inputs](../../../OWNER_INPUTS.md) keep
separate provider activation/schema/runtime/OEM/physical qualifications under #36.
No node/config/database/provider or public financial write occurred. Existing
Store payment compatibility/activation and manual payout qualifications remain.
Rental and legacy payment tests reuse some screenshot basenames in these focused
artifacts. Their complete ordinary UI assertions pass; a single overwritten image
is not claimed as proof of both scenes. #115 must namespace those captures before
freezing final full-app/UI coverage. #115's full-app/motion/accessibility,
signed 127-to-next continuity and actual public beta checks remain pending.
Keep issues #1/#36/#55/#84/#86 open.
