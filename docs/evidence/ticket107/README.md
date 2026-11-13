# Vault upload and recovery, ticket107

**In progress.** This packet records focused observations, not completion of107
or combined beta acceptance. Beta127 remains delivered. Specification105 and
issue107 retain Share, background/screen-off, interruption, error and recovery
requirements; both complete SDK checks and independent reviews remain pending.
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

## Remaining acceptance

Complete Share/provider failures, delayed/rejected delivery, background/screen-off
and actual process-loss recovery, viewport/motion and affected wallet/Chat/Talk/
Fileshare checks. Review the complete107diff on Standards and Spec independently
before closing it. Final ticket115 owns the combined signed beta and full-run
catalogue. No live provider activation, real funds or public Saito write is
performed by these controlled proofs.
