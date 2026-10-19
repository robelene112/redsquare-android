# Ticket52: offline draft privacy and safety center

Parent36/spec1 remain open. Review base:
`45a90620211c2c272ae2c76bd3e00f46a27ba070`.

The owner requested `OWNER_INPUTS.md` and explicitly authorized implementation
with recognizable placeholders on2026-10-19. This child supplies a read-only
Settings reader for six bundled drafts. It does not implement agreement to final
terms, reporting/deletion operations, working public contact channels or store
submission. None of those parent criteria is completed by a document reader.

## Observed red and corrections

Run39211393123 at8f08a1627 is FAILURE, with the new single native case failing
because Settings has no `Privacy and safety` action. Retained original XML shows
the actual Settings window, and the PNG was captured while its Activity remained
open. No production source changed before that committed test.

Native minimum74e54b5ce adds the reader. The first Standards review identified
missing SDK-specific exclusions for the two new focused workflow scopes;
e53cb56b9 fixes those two conditions. Focused10 run39212130707 and17
run39212133586 pass at e53, but root's visual inspection finds low-contrast
Android10 dark-dialog system icons. `contrast-before/` retains that original
screen and separate XML observation. The initial full attempts39212758904/39212762063
were intentionally CANCELLED to correct production first, not accepted.

795aef39e sets light/dark system-icon appearance on the Dialog's own Window,
keeps a Draft label in the non-scrolling header and strengthens the existing
case to reach each document's final paragraph. No deadline or existing
acceptance assertion is weakened. Earlier74e focused/source attempts were
superseded/CANCELLED while correcting workflow selection, not accepted.

## Corrected focused runtime

| Android | Run and source | Native result | Retained originals |
| --- | --- | --- | --- |
|10|39212876918,795aef39e|SUCCESS,1PASS,17.244s|16PNG and16XML, native output/exit and environment/scope records|
|17|39212879562,795aef39e|SUCCESS,1PASS,61.083s|16PNG and16XML, native output/exit and environment/scope records|

The same real Settings journey reads all six documents in Light/Dark, rejects
new owned-peer connections and disconnects its authenticated sockets for the
Dark pass, recreates the Activity while the deletion document is selected,
returns to the saved draft and verifies the unchanged complete wallet/backend
and zero controlled-peer transactions/reports. Android Back closes the center.
No test sends to a public backend, contacts a policy service or modifies a phone.

Root opened seven corrected original PNGs: Android10 Dark overview, Dark deletion
and Light privacy; Android17 Light/Dark overviews, restored Dark deletion and
Light contact. System icons are readable, the Draft header remains visible
after scrolling/recreation, and incomplete contacts/procedures remain explicit.
Screenshots and XML are separate observations, not atomic pairs. Paragraphs
longer than the viewport remain scrollable; these images do not imply every
line fits on screen simultaneously.

`sha256.json` binds the retained native originals and prepared APK verification files. All33 new/red PNGs passed signature,
chunk-CRC, IHDR, decompression/scanline-length and filter-byte validation; all33
matching XMLs parsed. Two historical contrast originals are also retained.

## Review and remaining acceptance

Spec/Standards source review at795aef39e reports0/0, including the dispatch and
system-bar fixes against the original base. One test indentation-only correction
follows the focused source, with no statement, identity or deadline change.

Source run39212876588 at795aef39e is SUCCESS (core/gateway tests, Debug/Release/
Performance assembly, Debug/Performance lint and instrumentation assembly).
Full Android10/17 regression and signed ordinary preview publication are pending. The original application identities and existing full
gates remain in the harness, with this one additional class in the wallet/all
partition. The parent physical, configured-provider, approved-policy/operations
and actual Play requirements stay open in `OWNER_INPUTS.md` and issue36.

## Prepared ordinary37, not yet published

Canonical `scripts/review-build` succeeds in3m27s at25159f3a6, reserving ordinary
37/0.1.37. APK SHA256:
`08f84ad0a3e5304b2acd35d9cc6b150212a4b481cab2f1e397c77c53f71cdbd4`.
Established certificate:
`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
Package is `nl.davidengelen.redsquare`, min29/target36, nondebuggable,
nonprofileable and with automatic backup disabled. Root independently checks
actual APK bytes/manifest and targeted BuildConfig DEX output: REVIEW_BUILD=true,
Push/Posts-Giphy/Chat-Giphy all unconfigured. Only booleans, never option values,
are retained in `prepared37/apk-check.json`. Final native full acceptance and
public delivery verification are still pending; no APK37 publication occurred.

## First full Android10 failure and fixture correction

Run39213969388 at25159f3a6 is FAILURE, not full acceptance:53 terminal
instrumentation statuses report52PASS/1FAIL before later suites and final gates
are unexecuted. Existing ChatGiphy case
`anOfficialGroupGifCanBeQueuedReopenedCancelledAndExplicitlySent` fails at
line259, via the credits helper at175: artist text exists in Compose but is not
displayed. All six cases in that class run; this is not a policy-reader failure.

`full10-first-failure/` retains original output, environment and two failure
observations. Root opened both PNGs. The in-scope `fresh-failure` shows the actual
group and a small message viewport with clipped content. Its separate XML has
Powered by GIPHY visible, with artist/source absent from the visible hierarchy.
The outer `failure` captures Launcher after the Activity closes and is not
causal evidence. Neither snapshot proves why the earlier assertion failed.

Fixture-only d74e25ea0 keeps all six identities, the three credit labels and final
visibility assertions, the180-second class and existing10-second label deadline.
Inside that deadline it now waits for positive visibility and repeats scrolling
if necessary while history/credits relayout. No production/build input changed.
Focused39216609812 atd74 is SUCCESS:6PASS/60.905s, with unchanged terminal
visibility assertions. `chatgifs10-correction/` retains its native output and
selected original sent-credit captures. Fresh full Android10 run39217149317
at1a1a4cbd3 must still verify the entire SDK;
Android17 full39213972826 remains independent and was already running at25159.

Root opened the corrected Android10 sent-credit original: GIPHY credit, owned
artist and Source are visible in the actual group view. Its native output
separately establishes all three terminal visibility assertions. The targeted
fix establishes a passing observation, not the cause of the earlier failure.

`full-identity-reference.json` records the already committed accepted48 identity
references plus the literal new policy case. It was recorded during these runs,
not before execution. Expected counts are comparison values only. Complete
production/build input maps across25159 and1a1a4cbd3 are identical (248 objects),
including performance/build-support paths; d74 changes only the GIF observer.
Spec/Standards residual packet review through1a1a4cbd3 reports0/0 with107 hashes
independently reproduced. The new focus/reference records await residual review.

## Complete Android17 result

Run39213972826 at25159 is SUCCESS in all three jobs. The independent identity
comparison is exact:432PASS, no missing/extra cases or assumptions, with
105 normal completions,33 deliberate process interruptions and32 PASS continuity
records. Chat183, Social208 and Wallet41 all pass. Exact profile concatenation
is checked before excluding its duplicate aggregate. All numeric exit records
are zero; partition-complete markers are chat/social/wallet respectively.

`full17/` retains native text/checkpoints, image properties, core/lint reports,
432 path/class/test identities, selected original observations, raw archive and
member hashes, and a summary. Structural checks validate946PNG/811XML across
archive and direct originals. Five optional diagnostics are explicitly recorded:
unavailable established Chat image-history archive and four missing-file-text
wallet-first-failure PNG/XML files. Required application gates are not excluded.
Full original tar files remain in the recorded external artifact directory.

Private recovery keeps worker4f1eaf4f-75bd-4990-8264-02cfb2e4f867 across
PIDs6665/6711/6776; group keepsb411334e-d2a6-4e77-b2f4-083762214413 across
7162/7209/7285. Both record zero expired requests, independent protected
restoration and PASS continuity. Both approved fresh public feeds, authenticated
read-only public Chat readiness, signed legacy5-to8 continuity and final wallet
identity complete. These are read/recovery observations, not Google delivery.

The actual independently signed immutable ordinary35/performance36 checks pass,
with distinct UIDs10236/10237 and public wallets, unchanged ordinary address and
restoration of both originally absent packages with no cleanup failures. The
three separate one-case signed-wallet observer logs remain outside the432
application identity comparison. This proof does not install candidate37.

Root opened15 full-run originals: Light overview and restored Dark deletion;
GIF sent credits; default generic private card and exact group; restored post
draft and generic group card; published post and recovered unsent draft; both
public feeds; legacy-update wallet; main35 wallet before/after and separate36
wallet. Text/controls and intended privacy labels are visible. XML observations
remain separate snapshots. Android17 uses the default x86_64/4096-byte image;
this is not a new16KB, physical Pixel, configured-provider or Play proof.

Full Android10 recheck39217149317 remains running. No publication or child52
acceptance follows from the Android17 result alone.
