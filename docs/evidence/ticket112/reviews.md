# Ticket 112 independent reviews

Review base: `a60cd323cf0c46b965fbbc986394d77a73ada2fb`.
Reviewed source: `79d5e50baef27fed255cf09afbb4638210407878`.
Both independent read-only axes reviewed the full diff and subsequent fixes.
These source reviews do not establish native or live financial acceptance.

## Spec

Zero remaining specification findings. All three previous findings are repaired:
history uses external payment states; definitely unsent orders expose guarded
discard; public remainder hydration handles provider-funded SAITO and alternate
priced purchases without a previous buyer's private invoice. The public lineage
reader verifies the funded predecessor and requires the expected Store's signed
fulfillment before admitting remaining inventory. It does not weaken private
invoice admission. Native compilation and complete Android 10/17 runtime
verification remain pending.

## Standards

Zero established documented-standard violations and zero remaining judgment-call
smells. Invoice.sameBinding resolves the earlier duplication. The public-payment
parser shares some wire-validation structure with private issuance admission,
but their different trust boundaries justify separate validation here. Independent
provider/Store reference vectors cover SAITO and USDT partial purchases and a
different buyer's subsequent carrier. Discard checks cancellation and wallet/node
context under the journal lock. Orders and detail views share external status.
Reported core209/42 and main compiler passes do not establish the pending native
compilation or complete SDK acceptance. Provider deployment and live financial
acceptance remain separate under36. Neither reviewer changed files or ran tests.

Both axes also reviewed the final test-only delta at
`61892460fd1a88d4450dc0879dc295b7091a9352`: the owned remainder catalogue now
sets the actual listing_signature field instead of an ignored signature field.
The native exact-original assertion caught this fixture error. Product code,
assertions and deadlines are unchanged. Spec: zero findings. Standards: zero
violations and zero smells. Complete SDK acceptance remains a separate gate.

Both axes reviewed the subsequent keyboard-observation delta through
`1fa70dda7c7785ba36e10ed32e3bbe45fe44d792` with zero findings. It observes
real IME visibility, uses Android Back, and observes dismissal before the existing
viewport scroll/tap. The original methods deadline,48dp checks and financial
assertions remain intact. Production code is unchanged. This synchronization
is legitimate but does not by itself prove the earlier timeout's cause or SDK
acceptance; preserve the complete original failure and fresh runtime results.

The final shared-input delta through
`4ce0926a43c3a2f68ed5e651acecb613f6acd426` also has zero findings on each axis.
The complete1fa Android17 run passes all12external-review cells but fails invoice
preparation through the old shared method opener, with the same visible numeric
IME. Both paths now share one helper observing the focused window, real Back,
hidden IME and zero bottom inset. Existing financial assertions, deadlines and
case catalogue remain unchanged. This is source review, not runtime acceptance.

Both axes reviewed the production storage delta through
`51bd948439727262ecbd1386cbd8f8881f10fbe4` with zero findings. The existing
obsolete-response recovery test exposed an unconditional5MiB allocation for each
small encrypted read under an actual16MiB growth limit. The loader now allocates
from the already-open descriptor's bounded actual length. It preserves the
29byte minimum/MAX_BYTES+29 maximum, exact reads, EOF check, version, GCM, AAD,
UTF-8, atomic recovery and shared synchronization. This is necessary recovery
correctness work within105/112. Final compilation/runtime acceptance and115's
full shared-application checks remain separate requirements.

Both final axes report zero findings through observer source
`7a17cf8bcd233bf673c9e40395f509034c10d02e`. The input helper now uses an actual
click and observes focus/IME readiness before replacing quantity, then verifies
its value before and after dismissal. The new focused scope preserves the
original class and480second bound; complete purchase/Wallet remain additive.
The exact reset cause is unproven. Installed Compose1.11.4 bytecode and the
[official text-action implementation](https://android.googlesource.com/platform/frameworks/support/+/e0ecf6d2233244754e505cb53cedc0359e0c87ba/compose/ui/ui-test/src/commonMain/kotlin/androidx/compose/ui/test/TextActions.kt)
show focus acquisition followed immediately by SetText; neither guarantees that
a newly opening native IME is already ready. This does not prove a product bug.

Retaining complete51bd Android17run43580633625 is legitimate with the exact
test-only source bridge and a separately identified passing7a observer probe.
Do not call it a complete7a run or assemble successful tails. New full Android10
and focused Android17 runs remain pending. Final full-application tests on both
SDKs remain required under115.

Final runtime acceptance is now recorded independently: complete7a Android10 and
complete51bd Android17 each pass all34ordinary cases,36cells and both three-phase
process proofs. The separate7a17observer probe passes. Production-object equality
and every complete source-specific artifact are in the ledger. Both reviews remain
zero findings; the test-only bridge is explicit and no failed prefix is accepted.
