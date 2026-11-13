# Vault upload and recovery, ticket107

**Development slice complete.** Native picker/Share review, standard key creation,
exact-file upload, independent delivery facts, background/screen-off progress,
safe cancellation and recoverable original operations are implemented and verified.
Beta127 remains delivered; the one combined Vault/Store beta belongs to115.

Both complete focused upload runs use
`f9225502dab0f2a204534160e8c7282400557ff3`:

| SDK | Complete run | Result |
| --- | --- | --- |
| Android10 | [43466897529](https://github.com/robelene112/redsquare-android/actions/runs/43466897529) | All17native cases in12classes, all12viewport cells, three-phase actual-process protocol pass. |
| Android17 | [43466903331](https://github.com/robelene112/redsquare-android/actions/runs/43466903331) | The same complete scope passes. |

The exact artifact digests, class counts and distinct process IDs are retained in
[focused-runs.json](focused-runs.json). All101/103ZIP members and186capture members
per SDK are byte-exact, with the recorded proof source verified. No successful
subset is assembled into either acceptance run. All160core tests pass.
Independent Standards and Spec reviews have0remaining findings, including their
final fixture-delta checks. Product source is unchanged from reviewed3687f079.

Review base: `8570655b69ab4d1b5aeb98f7ae68bdaa71d1e3f4`.

## Independent reference and owned native upload

`test-fixtures/reference/vault-mint-reference.cjs` initializes the actual isolated
`saito-wasm@0.3.15` wallet factory with a disposable synthetic UTXO. It reproduces
small/4MiB file signatures, standard access commitments, Bound mint output and
upload/broadcast bytes against pinned source
`0db0062d873369dc013046ed77c97410b5683b64`. This is wallet-factory compatibility,
not a funded consensus mint. Generic transaction restoration remains strict.
The native file limit is4MiB, below the reference browser's100MiB.

| Android17 run | Exact source | Observation |
| --- | --- | --- |
| [43418367936](https://github.com/robelene112/redsquare-android/actions/runs/43418367936) | `4c2644368b988d56a62258692487fff0cc33fb80` | Original missing Upload file action. |
| [43422033796](https://github.com/robelene112/redsquare-android/actions/runs/43422033796) | `62113505a6b8d82e7d0815d50d77868fd2a74ddd` | Picker/review/discard passes before network writes. |
| [43425078209](https://github.com/robelene112/redsquare-android/actions/runs/43425078209) | `15ba65954ac0e3ce1c73aa147bd9f30edfbba375` | Picker and encrypted wallet-wide reservation/reopen pass. |
| [43427512801](https://github.com/robelene112/redsquare-android/actions/runs/43427512801) | `4e37061043376cd09e67f851641833f11b4e0fe5` | Original full-mint tracer fails on missing Create key and upload. |
| [43431499158](https://github.com/robelene112/redsquare-android/actions/runs/43431499158) | `104b8bca08da4e8832f8657c576a4663b664a138` | Submitted flow remains paused instead of completing within25s. |
| [43432982964](https://github.com/robelene112/redsquare-android/actions/runs/43432982964) | `822570043687d9f4fb18592a132a87c98b93891a` | Real Android PIN, one mint, one upload, exact39byte file, observed key and returned Files entry pass. |
| [43434339461](https://github.com/robelene112/redsquare-android/actions/runs/43434339461) | `33028bcd1d89d1660c436a64ec27e5eeaec66651` | Android Share does not reach review within15s. |

The sender initially reused the mint lease after `finishBroadcast` gracefully
closed its sole physical connection. Acquiring the next feature lease for the
file RPC fixes the original native journey under unchanged25s/180s goals.
Run43432982964 independently records one received mint, one received upload,
recognized original candidate and accepted requests. The test also checks exact
file bytes and selected wallet/node restoration. Source submission, file
acceptance and selected-node ownership remain distinct facts.

Run43432982964 artifact14040547342 ZIP SHA256:
`5742d4f6b36bb8ee3cb5b7815d9a380ce6393feef33362bb69acd3519dd439f4`.
All58ZIP entries and8capture members were byte-compared after extraction;
`.scratch/proof-source.txt` matches the full source above. All160core tests pass.
Raw originals, source receipts, native PNG/XML and verification JSON remain
outside Git under `/mnt/storage/redsquare-android-artifacts/vault-store-planning`.
Original failed runs remain retained; successful subsets do not replace final
whole-source verification.

A blank header in some image-tool previews is not a demonstrated application
failure: original RGB PNG pixels in the actual `[18,34][115,82]` drawer bounds
have brightness range222 and no transparency. The native review probe uses
actual device pixels with the unchanged contrast threshold90. Do not infer a
header regression or a proven rendering fix from the preview alone.

Run43435800206 on `f98ee7ce4420bb16a7c4bb2fa454fec060da53bd` reaches
native shared-file review, verifies no publication and preserves launch settings,
but its final refresh action fails: the test selects a content description while
the disabled native action exposes a text label. Original screenshot/XML retain
the disabled action and active refresh. The follow-up waits for an enabled native
label/description under a new5s readiness bound and positively requires a new
owned-peer request within5s; the original15s Share goal and180s class remain.
It also checks Android's public Share-target resolution. This partial failure is
not used as completed Share acceptance.

Run43437450439 on `d33c0dc9f76c55f461311f8a3610cf89f0e9b7cc` passes
Share resolution, review, no-write, discard and enabled Refresh, then fails the
new5s archive-counter assertion. Its screenshot shows the resolved original key.
Vault refresh always reads the selected node's balance, while Archive metadata
can already be cached. The follow-up observes the real owned HTTP balance
request and the settled original file entry under5s bounds. This corrects the
observation; all original Share, identity, no-write and class goals remain.

Run43438340933 on `9641ab5c1bd57a49348e5eaa9cd5e7231f0eeee4` passes
the actual grant revocation and recoverable read-error case. Its delayed-provider
case reads bytes, navigates between modules and cancels without publishing, but
reports a read error instead of the required stopped message. Android pipe closure
can surface IOException before coroutine cancellation reaches the caller. The
fix classifies an exception in an already-cancelled preparation as cancellation,
retains the original rollback and preserves real read/storage errors. New work
also waits for the previous job to finish, including cancellation cleanup.
The complete provider class must pass together before acceptance.

Run43439448284 on `d577a425f11ece0a77f30e38352a843015e5c48f` reaches
the actual notification Stop, retains one mint/one upload, and its later failure
capture shows Waiting after the availability check. Its instrumentation result
is masked by test cleanup: revoking POST_NOTIFICATIONS from inside the target
process kills that process. It is not accepted as a product crash or completed
cancellation proof. The follow-up restores permission from the host after the
unchanged native class returns and retains the original Throwable before fixture
cleanup. It must reproduce the exact failure before any lost-reply product fix.

Run43439824727 on `ce6db2fc1dca7990784cc766fa969181c9381a3b` remains
negative: the delayed-provider case now exceeds the original10s return-to-Files
goal. Exception classification alone is insufficient. A bounded100ms readiness
poll outside the InputStream read/close monitor is the next tested change, with
cancellation checked before reading. The stopped-provider goal, joined worker
cleanup, real revoked-grant case and180s class bound remain unchanged.
Diagnostic prefix `[DEBUG-vault107]` is temporary and must be removed after the
exact boundary is verified. Do not count a hypothesis as the established cause.

Run43441762466 on `bf73c6243a1dbab02206f7ab3d2d691a08502874` cleanly
reproduces the lost-reply failure: notification Stop works, one mint and one
upload remain, but the subsequent availability check does not reach Complete
within15s. The product fix now uses the observed original key to retrieve and
verify the exact signed file, including the expected MIME/size, when an upload
reply was lost. This read does not mint or upload. The unchanged class must pass.
Run43441762577 on the same source separately passes the full background case:
the real display remains noninteractive while balance requests advance6to7,
with one mint/one upload; native Complete and Files return pass after waking.
Both original masked permission-cleanup runs remain retained.

Run43442291181 on `ee7e4b6aeecc46af172f26f86bd05c54fd2e80ea` remains
negative under the same10s goal. Its original XML shows Review with a false
unreadable-draft message after Stop, rather than a demonstrated still-running
read. The diagnostic records an active preparation at the actual Stop. Cleanup
had protected only its IO block: returning to the cancelled caller could throw
before updating the UI and be misclassified as a corrupt draft. The next fix
protects the complete rollback/status update with NonCancellable. Polling alone
is not accepted as the established cause or fix. Diagnostic run43442181449 was
superseded/cancelled by the workflow concurrency group and supplies no acceptance.

Run43443537731 on `13638b7c8ee1414ec958695676f8fda8ec710751` now passes
the native read-only lost-reply recovery, one-mint/one-upload, authenticated file
read, exact bytes and Files return assertions. The class still fails its final
owned-peer health assertion: the fixture treated the deliberately closed original
upload reply as an unexpected transport error. The corrected fixture requires
one explicitly identified authenticated request's response to fail enqueue and
records that exact event; all other response checks and request validation remain
strict. The test positively waits for the actual lost reply within5s. This failed
class is retained and is not counted as complete cancellation acceptance.
Run43443537968 on the same source passes actual native process death and distinct
cold PID, no automatic send, fresh real PIN, same retained candidate/file without
another mint, retained Chat/Fileshare bytes and exact original installation
restoration. Its process image archive is named `images.tar` and must be collected
in addition to the standard focused capture archives.

Run43444702387 on `93ffc24a4172a5c283355304220841a7f87c711a` passes the
complete delayed-provider and real revoked-grant class together under the
unchanged bounds. The complete NonCancellable rollback/status update removes
the false corrupt-draft result. All temporary `[DEBUG-vault107]` product probes
and their dedicated log collection are removed after this verification. Original
failed artifacts and the source-only original-Throwable capture remain retained.

Run43445182955 on `1e3d029a1ae26ca2f3eabbabf835b3d127aaa597` fails at
the second viewport cell: the review correctly retains its scroll position after
module/theme changes, so its heading is above the visible viewport. The original
XML shows the reachable deposit, fee and actions. The corrected test waits for
the actual review, explicitly scrolls to its heading and verifies it is displayed
within one original15s deadline. All12cells, fonts/themes, costs, action48dp and
no-publication assertions and the180s class bound remain required.

Run43447023227 on `28da880654a780d6393ff132d43aba138ca66569` passes the
complete notification-cancellation/read-only lost-reply class, including the
exact deliberately failed reply enqueue, one original mint/upload, authenticated
remote-file bytes and Files return. No transport validation is waived.
Run43447023974 on the same source retains the existing3recovery passes but fails
the new fourth case within its15s goal: unavailable device authentication is
mislabelled as user cancellation. The fix distinguishes actual user cancellation
from unavailable authentication and leaves the visible review controller retryable
when prompt setup throws. The complete4case class, including recovery after real
screen-lock setup, must pass under its unchanged180s class bound.

Complete focused Android17 run43449106813 on
`a792fee256ff6453cd05903c9a5802a6ee12e016` passes the full then-declared107
scope: actual process loss/restore, Share, all12upload viewport cells, background,
cancellation/lost reply, provider, picker, all4recovery cases,4MiB upload,
reservation and mint. All97ZIP entries and166capture members are byte-exact.
The same-source Android10 run43449107346 fails after real screen-off progress:
the test attempts module navigation before any Compose hierarchy is registered
on returning from Home. Its original15s completed-return goal is retained as one
combined readiness/navigation/completion deadline. That failed SDK run and its
successful subsets are not assembled into complete acceptance.
Independent source reviews at that snapshot report1Standards finding (fixture
consumer quiescence before captured-state restore) and3Spec findings (funding
race uncertainty, interrupted completed-source cleanup and separate visible
facts/provider rejection). These remain open until fixed and re-reviewed.

Review finding1 now has a native red tracer: run43454335901 on
`812fa1c5580028a2929c2dc1beba4e04da282e7f` reserves the same input for another
payment during real Vault PIN authentication and fails the required15s unsent
recovery. The fix validates/reserves the exact mint before recording uncertainty,
preserves monotonic uncertainty/file-acceptance facts, and retains genuinely
unsent review/discard. Only a journal-verified never-submitted mint reservation
can be released; other payment/mint records remain. Empty committed mint ledgers
are valid while partial file/guard loss remains a recovery barrier. Cancellation
loads the durable record and updates status inside NonCancellable cleanup so it
cannot overwrite a sent record with the stale pre-authentication copy.

Additional review tracers retain the original failures:
- Run43455007997 (`2c77e0db049e5eaac59ed208623a9da8706833cf`)
  fails the aborted-provider assertion because the process-owned consumer remains
  active when fixture restoration begins. The fix explicitly cancels and joins
  the exact owned runtime and independently closes controlled workers before
  restoring captured files. Original assertions and180s class bound remain.
- Run43455975730 (`635c8e8a796160d2a9e47316998821ecf68bb0b1`)
  fails its15s completed-recovery goal with the exact old encrypted descriptor
  restored after successful source-chunk cleanup. Journal-first recovery and
  cleanup-before-journal-removal address this independently of distinct-PID proof.
- Run43455975798 on that same source passes the complete Android10 background
  class after the test waits for return readiness within the existing combined
  15s goal. It does not replace a complete same-source SDK run.
- Recovery tracer43460569923 on
  `fa79ece6841fe4b9e1bd8a58d8d565590b92b9ce` adds explicit submitted/file/ownership
  facts, retained rejection after failed read-only access, and return to the
  independently usable library. It fails two cases because the submission and
  rejection facts are absent, with the other two cases passing. The subsequent test-only correction uses the
  existing FrameIconButton text semantics for its library button. Dispatch
  43460548670 used an incorrect source ref and was superseded before proof;
  it supplies no runtime acceptance.

The three focused corrected classes pass on
`dd4e31db0db9364a2a87fabd9a8871637bd0d374`: provider3cases43460440877,
funding race43460450350 and completed-source cleanup43460445974. The original
ZIP digests and every extracted capture member were independently compared.
The remaining facts/library correction preserves accepted/submitted history,
records explicit provider rejection until actual file acceptance, persists
observed ownership before a potentially failing file read, and allows library
navigation without releasing or resending retained work. These focused passes
are not a completed SDK acceptance run.

Independent re-review reports0Spec findings at
`3687f07907756975a83cc66c83d44100573c72e4`. Standards review found one additional
process-fixture cleanup sequencing issue: a failed original-file restore could
skip preference restoration. Fix`ec62c468e5da49e2c36c84943ff108308417ab5b`
attempts each captured preference independently alongside file restoration,
keeps all original identity assertions and removes the recovery checkpoint only
after successful restoration. Independent Standards re-review confirms0remaining
findings. The forthcoming complete SDK process gates retain that correction.

Recovery run43462291537 on`3687f07907756975a83cc66c83d44100573c72e4`
passes3cases, including durable explicit rejection after failed file retrieval.
The delayed-ownership case verifies all three independent facts and returns to
the retained-operation library, but waits for the wrong heading, "Files". Its
original screenshot/XML show the actual "Your files" heading, Offline filter and
Open upload action. The test correction uses that existing label; every original
15/25s goal, exact operation assertion and180s class bound remains. The complete
4case class still must pass. Same-source viewport run43462298233 passes all12
width/font/theme cells with costs and48dp actions retained.

All160core tests pass after the review fixes, with0failures/errors/skips.
Affected Android17 wallet-import risk regression43462939166 and the actual
three-party Talk/Chat/Fileshare regression43462944723 pass on
`ec62c468e5da49e2c36c84943ff108308417ab5b`. Original ZIPs and all extracted
members are byte-exact. These are focused regression observations; the final
combined beta still requires complete shared-source application/update proofs.

Complete Android10 upload run43464152245 on
`ddc58d929500cf768500ec22b789cb84010fda5a` passes the distinct-PID process
protocol, Share and all12viewport cells, then fails the original15s background
return goal. Original screen-off facts still show1mint/1upload, exact bytes,
noninteractive display and fresh ownership IO. The original failure PNG/XML show
Android's actual PIN bouncer covering the app, after the test removed the
credential using shell while asleep. Clearing the credential is not reliable
lock-screen dismissal on Android10. The test correction uses the observed real
PIN digit/Enter controls and positively checks the keyguard is gone. Unlock,
app readiness, navigation and completion now share the same original15s return
deadline. No product authority, upload assertion or class goal is relaxed, and
that failed complete run is retained without assembling its passing subsets.

Complete Android17 run43464157912 on that same`ddc58d9295`source retains
passing process/Share/viewport/background/cancel/provider/picker checks. The
4case recovery class passes3cases; delayed ownership now verifies independent
facts, library navigation, retained exact operation and read-only completion,
then fails its15s filename goal because the test itself retained the Offline
filter. The original PNG/XML show the selected Offline filter and truthful
"No offline copies yet". The corrected observation positively verifies that
filter is retained, chooses All files and requires the completed remote file
within the same total15s return goal. No offline copy is invented and the
failed complete run remains negative.

Corrected Android10 background run43465646030 passes on
`4f1c884019db047f45846fb044ecdc259cdb7728`, including real device unlock inside
the original total return goal. Corrected Android17 recovery run43465879771
passes all4cases on`f9225502dab0f2a204534160e8c7282400557ff3`, including all
independent facts, retained rejection/ownership, library Offline navigation and
original-key/exact-file recovery. Both independent final delta reviews report
0remaining findings and confirm unchanged production source and original goals.
Representative actual320dp/200percent cost and action PNGs were visually
inspected in both themes, as were the waiting and rejected-file views. New
complete Android10/17 upload runs43466897529/43466903331 use that same final
source. Their complete primary conclusions and byte-exact originals are now
verified above; those complete results supersede no original negative record.

## Remaining trajectory and real-service qualification

Continue108–115 sequentially under the existing owner authorization. Ticket115
owns the complete application/frozen-catalogue expansion, final motion and signed
in-place update/public delivery proofs. Append all new106/107native classes and
process phases to its full catalogue while retaining every inherited gate.
These controlled results establish compatible wallet-factory/protocol behavior,
not a funded consensus mint, actual production-provider acceptance or ARM/OEM
qualification. VAULT_LIVE remains unchecked under36 and OWNER_INPUTS.md. No live
provider activation, real funds or public Saito write was performed. Existing
parent/release gates remain open.
