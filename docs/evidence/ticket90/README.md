# Ticket #90: Wallet and Settings shared design

Review base: `835021c03c80c6c1351631aa57b647f1d9b759d1`.
Last production change: `3f7da3e346d27253c4dc6557b9a2a7ca6dff2e13`.
Both source review axes and final native payment reviews have zero actionable findings.

Wallet, Settings, receive, payment review and recovery use the shared account
window, hierarchy, cards, real48dp controls and category navigation. Existing
controllers retain balance provenance, device authentication, signing, import
safeguards, cancellation, identity and local recovery. Both approved nodes remain.
Transfer step guidance scrolls with the form so the whole focused payment field
fits with large text and the real keyboard. No public payment was submitted.

## Completed checks

| Platform | Complete affected account run | Checked-out source | UI methods |
| --- | --- | --- | --- |
| Android10 | [42095757828](https://github.com/robelene112/redsquare-android/actions/runs/42095757828) | `58dfcfa1fb6a25e2540996c0d377a68cbc04f680` |32 |
| Android17 | [42094963847](https://github.com/robelene112/redsquare-android/actions/runs/42094963847) | `32eb9f73f63537c9918919cf5d7b05c4bfe3f851` |32 |

Both runs retain original appearance, import/risk/storage, export, transfer/PIN,
backup and actual transfer/export/import process-death checks. Continuity receipts
retain the real interrupted/recovered process observations and exact signed
controlled-peer frame. They establish neither public inclusion nor real payment
delivery. Original class/request/process deadlines remain unchanged.

The two sources have identical production code. The later10 fixture observes a
visible account popup before activating its scoped action. The17 fixture closes
actual Gboard font education through its package-scoped OK control. Neither hides
the IME, reduces font scale, removes acceptance or changes application behavior.

Each platform has12account viewport cells:320/412dp, light/dark and100/130/200%
system text. Four additional320dp payment/import/receive cells retain actual IME,
full address/QR discovery, replacement safeguards and cancellation. The complete
focused Amount field measures72/72px at130% and88/88px at200%, in both themes.
Acknowledgment of the real Gboard font banner is recorded only in17's dark cells.
Both original amount labels and focus outlines are visibly complete.

Eight original movies per OS, decoded frames and actual system/window scale0/1
receipts preserve account task opening/return and Settings category motion.
Independent review inspected144earlier viewport PNGs and16complete sequences,
then final payment/receive/review images, representative final account views and
all final17sequences. No additional actionable layout/motion finding remains.
This does not establish rapid interruption during every possible animation.

A precise zero-motion observation remains qualified: earlier17return has one
dimmed feed frame at257ms and normal feed at275ms; final17has one at278ms and
normal feed at295ms. The original pair and final sequences are retained. Cause is
unestablished; completely flash-free window return is not claimed.

[Source/debug lint42093749985](https://github.com/robelene112/redsquare-android/actions/runs/42093749985)
passes at the last production source.117core/16gateway results are validated
Gradle cache reuse, not fresh test execution. Debug lint has0errors,55existing
warnings and1hint. Raw source/build metadata and report are retained.

## Failure provenance

- Real task-header RED10:42083120494; first GREEN10:42084565308. Child receive/
  review RED10:42086633967. Earlier42085614149 used an invalid localhost fixture
  URL; the accepted explicit127.0.0.1host corrected setup without relaxing validation.
- Run42088113599 passes both functional methods on17, then fails a new matrix's
  missing category-to-index Back action.83ece corrects that real route.
- Focused e042 runs42089706359/42089712188 fail the immediate native Settings
  capture while still showing the account popup. Explicit Compose destination
  readiness produces complete focused c148 PASS42090783136/42090788634.
- Initial broad83ece runs42089005702/42089010358 pass32UI methods and all process
  checks. Visual inspection nevertheless finds a clipped Amount label with IME.
- New geometry regression42092041308 first fails compilation only, then fe9's
  real RED42092585777 measures88px whole versus76px visible at320dp/200%.
  Existing130% cells measure72/72.3f7 scrolls guidance with the form, yielding
  four full-field GREEN10 cells in42093550633. Exact RED originals are retained.
- Run42093556247 passes17's first three cells, but200%dark measures49/88 with
  the actual Gboard education banner.32eb reuses the existing real acknowledgment
  helper, preserving font/IME and the assertion. Final17proves88/88.
- Broad10run42094638664 fails a Pixel200 menu setup: Settings semantics exist but
  are not displayed; a later native capture has no popup. Cause remains unproved.
 58df observes actual popup visibility/removal within5s, scopes the unchanged
  displayed/enabled assertions and click, and passes all32methods/process gates.
- Superseded interim runs42087921107,42088225310,42088509831and42088513891 were
  cancelled, not accepted. Earlier first-compile syntax failure and deliberately
  stopped local compile are likewise not passes.

## Scope limits

These are owned emulator/controlled signed-peer checks. They do not establish
physical Pixel performance, actual provider delivery, Play/KVK acceptance or
owner-wide visual approval.94 retains consistent-final-source whole-application
checks, signed in-place update and beta publication.1/36/55/84 remain independent.
