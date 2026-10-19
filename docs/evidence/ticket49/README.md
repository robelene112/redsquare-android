# Signed Play bundle and offline release package

For [issue49](https://github.com/robelene112/redsquare-android/issues/49),
child36/spec1. Review base `2eabfa0b26af4b3814b9c1821d2975546e8b0a5b`.
Concrete listing/assets/reviewer/data/declaration drafts are in the maintained
[release package](../../release/PLAY_RELEASE_PACKAGE.md). No policy, account,
Console, key enrollment, cloud/node deployment or store release was published.

## Actual CLI and artifact proof

The actual initial `scripts/review-build --bundle` at4f48 exits2 because the
canonical CLI has no bundle mode; version remains31. `cli-red` retains that RED.
Minimum implementation is `ef3321247e28747b3d0322546635a1b3fb0b2367`, reviewed
Standards0/Spec0 before the builds. All three canonical builds own the existing
lock and use the established signing identity/counter. Source HEAD and tracked
inputs stayed fixed throughout them; all owned builds are now drained.

| Command | Result and independently checked artifact |
| --- | --- |
| `scripts/review-build` | Exit0, BUILD SUCCESSFUL3m24s. Ordinary main32/0.1.32, APK SHA256 `5f754ed4953bec3192950d4f3bccb31f3414fe9307b947f51bc3041e1c03d4f7`. |
| `scripts/review-build --bundle` | Exit0, BUILD SUCCESSFUL3m39s. Signed AAB33/0.1.33 SHA256 `2f586cac3959d6f91614c9deab4e5b4bd1ab6fb579b46e555f3993717aa8c629`. Actual local bundletool-derived universal APK SHA256 `6939a75937683a23b39837ea8a57bf4e613cf5c993f08c8e177f9d5bc7a977de`. |
| `scripts/review-build --performance` | Exit0, BUILD SUCCESSFUL2m47s. Isolated performance34/0.1.34, APK SHA256 `4b7edbd68ba73a6bfc1f16c12ab3fc5c85122cb5609caaa26c31be93aad9c649`. |

All three use certificate
`6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`,
min29/target36 and nondebuggable manifests. Main32/bundle33 are nonprofileable;
performance34 is profileable with independent package/label/storage. Actual
APK DEX proves REVIEW_BUILD true only for ordinary32, false for bundle33 and
performance34. Actual DEX Push/Posts-Giphy/Chat-Giphy configured flags are false
for all three; no key value is retained. The bundle metadata also records the
independently generated source-configuration booleans.

The signed AAB passes actual strict JDK JAR verification with the existing
keystore and actual bundletool1.18.3 validation. Its actual manifest and signer
are checked against the reserved version/stable main package and derived APK.
The official tool SHA256 is pinned to
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`.
The actual derived APK passes apksigner verification and SDK35 `zipalign -c -P16`.
Four actual64-bit native libraries have LOAD alignment at least16384. Separate
raw RELRO-end remainders are8192/8192/12288/8192; these are retained as layout
observations, not a demonstrated runtime crash or whole16KiB compatibility.
Actual16KiB-runtime testing remains a35 gate, as does physical Pixel acceptance.

`artifact-verification.json` binds source, literal reserved versions, actual
hashes/manifests/SDK/profileability and actual DEX booleans. Per-mode directories
retain canonical commands/results/logs, actual Android tool outputs, permission
lists and configuration observations. Artifact binaries stay in ignored
`.scratch/{review-builds/32,play-builds/33,performance-builds/34}`. Large raw records
remain under `/mnt/storage/redsquare-android-artifacts/play49-*`; tool cache is
outside Git. No signing credential/password/owner-wallet document is retained.

## Preserved native and public evidence

`source/equivalence.json` verifies all239 production/build objects equal the
accepted48 full Android10/17 source4e7992d8d253caff15ac0dcbc48001835de09b4f,
and the earlier prepared31/30 input map. Only packaging scripts change here;
version/review flags/artifact mode deliberately differ. This is source-input
comparison, not byte-identical binaries. No new native behavior changed, so
[accepted48 full runtime evidence](../ticket48/NATIVE_ACCEPTANCE.md) is retained
without rerunning430/431 cases. It does not prove Play installation or physical
performance. Source CI39103857593 at ef332 is SUCCESS, with its actual completed
metadata/log retained under `source`. No new device-case count is inferred.

The actual invalid `--performance --bundle` combination exits2 before reserving
another version. The real APK publisher rejects the actual Play33 directory
because it contains AAB/universal APK rather than a publishable `redsquare.apk`,
before any static publication. Its rejection and release-directory inventory are
retained. Ten actual static-file hashes (main31, immutable29 and performance30
included) and five actual HTTPS metadata observations remain unchanged before/
after preparation. Main31 and performance30 remain live;32/33/34 are unpublished.
This comparison does not repeat accepted48's full HTTPS APK download checks.

## Release assets and remaining scope

`source/asset-inventory.json` verifies six delivered assets, complete PNG CRC/
inflation and required dimensions/types. Both320x640RGB native captures equal
the accepted48 originals byte-for-byte, with their original paths/hashes. Root
opened both complete original native screenshots, the512RGBA icon and1024x500RGB
feature graphic. Feed fixture text and private Chat screenshot provenance are
explicit in the release package; these are drafts for representative final
screenshots, not installed-Play captures. No screenshot editing was performed.
`source/listing-lengths.json` verifies title10/short69/full1705 within30/80/4000.

Provisional document reviews corrected private-versus-group transport wording,
the missing Settings availability explanation, and media preview-versus-send
boundaries. Group originals are unencrypted; private transport and local group
storage encryption are separate facts. Final committed whole49 review at278c756d8484dcce9d0cf73ccd5d7e7a294863cf
reports Standards0/Spec0; both reviewers independently verified all54 hashes,
artifact/configuration/source bindings and exact screenshot copies. Authored
source/prose diff checks pass; raw tool outputs preserve upstream whitespace.
Issue49 is CLOSED at https://github.com/robelene112/redsquare-android/issues/49#issuecomment-6246622115.

A valid local signed AAB is not an accepted Google upload or enrolled Play App
Signing identity. Parent36 keeps verified publisher/account/contacts, final
privacy/deletion/Terms and substantive native policy/moderation flows, declarations,
track installation and authorized submission open. Original configured-provider/
cloud gates and physical35 remain open. No policy exemption or publisher fact is
invented, and local tools never establish physical or Google delivery acceptance.

## Fresh current-code offline preparation44, 2026-10-19

[Candidate44](prepared44/README.md) supersedes historical33 for offline preparation.
Canonical bundle mode exits0, strict JAR/bundletool/local APK checks pass; actual
universal APK DEX, signature/manifest/SDK and16KiB ZIP/64-bit LOAD alignment pass.
All258 production/build objects match prepared42/43 and executing About38 full116e.
Current full acceptance and beta publication remain pending. No final configured
policy/provider/physical/Play result is claimed; all real gates remain open36.
