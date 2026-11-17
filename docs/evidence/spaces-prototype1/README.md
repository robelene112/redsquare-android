# Spaces native prototype 1

Delivered for owner UI review, not functional Spaces acceptance. The owner requested the quality plan first, then specification134, then this downloadable native prototype. No implementation tickets or functioning Spaces backend were delivered by this phase. UI approval is still pending.

- [Download page](https://davidengelen.nl/redsquare/spaces-prototype/)
- [Specification134](https://github.com/robelene112/redsquare-android/issues/134)
- [Quality plan](../../quality/SPACES_QUALITY_PLAN.md)
- Primary source: branch `codex/spaces-native-prototype`, signed/tested source `b968b51da7d214a634c717e7b44575d2cf645e3e`.
- [Prototype explanation](../../../prototypes/spaces-native/README.md)

## Artifact and public delivery

Separate package `nl.davidengelen.redsquare.spaces.prototype`, launcher **Spaces Prototype**, version1. It installs alongside the ordinary app. The signed nondebuggable APK is 26,879,335 bytes, SHA256 `12bb20cbd27f95261a918f870bf77733257d402ae654fec0c952479e2d20f5df`, using the established certificate `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.

The actual public page and complete APK were downloaded with curl. Body bytes/hash, package/version and signing identity match. The prototype requests no network/camera/microphone permissions; AndroidX's app-owned signature-only receiver guard is retained. No wallet, real Saito publication, payment, media capture or storage-provider activation occurs. All community content and operations are synthetic/in-memory. The editor/reader use the existing native Stack library and shared app theme/resources.

Ordinary beta139 metadata and page are byte-identical before/after publication. Public-directory traversal initially failed under a private umask; the publisher now explicitly sets only the two prototype asset directories to0755. Origin and public curl requests then returned200. Python urllib is separately rejected by the site's Cloudflare1010 rule; this is not recorded as an APK failure or a reason to modify site protection.

## Native verification

[Complete run44193043194](https://github.com/robelene112/redsquare-android/actions/runs/44193043194) passes all three jobs at the exact signed source. Each job ran the complete12-case catalogue, not a passing prefix assembled from earlier runs.

| Profile | Actual pixels / density | Font | Terminal passes | Screenshots |
| --- | --- | --- | --- | --- |
| Android10, API29 | 1080x1920 /420dpi,411dp | 100percent | 12 | 29 |
| Android17,37.0 image | 1080x1920 /420dpi,411dp | 100percent | 12 | 29 |
| Compact Android17 | 960x1800 /480dpi,320dp | 200percent | 12 | 29 |

The independent receipt checks exact source, all12 expected terminal method identities, actual display settings and87 screenshot hashes. Per-test viewport assertions verify actual width, density and font scale. Native flows include chat/thread/editor/review/files/call navigation; offline queue/cancel without text loss; visitor/denied states; loading retaining content; storage full/expiry; Space/channel Back restoration; owner-only controls; archive; listening during timeout; membership-preserving discovery; call ownership across Spaces; announcement-thread restoration.

Visual inspection covered normal and large-text overview/chat/room/call-bar images. Large-font avatar clipping and tab-label misalignment were corrected before delivery. Owner design feedback is still needed; these images are evidence, not automatically approved design goldens. Release lint and actual signed-APK identity checks pass. This is prototype UI verification, not a physical/OEM performance, live backend, E2E, real media or provider claim.

## Standards

Independent read-only Standards review found no remaining actionable findings in product source323958df. The final b968 correction changes only the native observer: scroll the specific queued cancellation control into view before clicking. It retains every input and cancellation assertion. Public-directory readability correction is limited to the dedicated prototype subtree. Native and public acceptance are recorded separately from source review.

## Spec

Independent read-only Spec review found no remaining substantive phase/scope/spec mismatch in product source323958df. Product/UI source is identical at tested b968; only the observer changed. Earlier queue loss, cross-Space mutation/navigation, permission, room-context and article draft/publication findings were resolved. Destination specification134 remains open and is not satisfied by simulated prototype actions.

Summary: Standards0 remaining product findings; Spec0 remaining product findings. All36 final native terminal outcomes pass. Owner UI approval and later functional implementation remain separate.

## Retained observations and limits

Unsuccessful CI runs and available local failure logs remain retained. They include initial build/source-selection problems, old Android17 Espresso injection, AVD lookup/path errors, offscreen-drawer selectors and offscreen action observation. The expected12 methods and native outcomes were preserved; observer/setup corrections received fresh complete affected runs. Earlier successful portions are not substituted into the final run. The private external cache retains the earlier candidates and screenshots, including the observed large-font defects.

Evidence cache: `/home/david/.cache/redsquare-android-artifacts/spaces-planning-20261117`, final profiles `final-android10`, `final-android17`, `final-compact`. APKs/screenshots stay outside Git. The small receipts here preserve the accepted identities and hashes.

Bugs132/133 remain open and must be fixed and verified sequentially before functional Spaces integration. Real encryption/protocol/legacy Chat compatibility, provider operating terms/schema, measured room capacity and actual physical/OEM/Play qualifications remain gates in specification134. No node/provider deployment or public financial/community write is authorized by this prototype delivery.
