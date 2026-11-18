# Spaces prototype 3: five navigation alternatives

The owner finds prototype 2 better but still not attractive enough, and requested five alternatives for opening communities/channels. The rest of the design remains the shared baseline. No alternative has been selected or approved.

[Download](https://davidengelen.nl/redsquare/spaces-prototype/) or [direct APK](https://davidengelen.nl/redsquare/spaces-prototype/releases/3/spaces-prototype.apk). Tap **Design A / 5** to compare:

- A: Space switcher, a community-name button in the header.
- B: Channel title, the current channel acts as the menu.
- C: Bottom dock, a wide control above the bottom navigation.
- D: Floating button, with its own clear area above the composer.
- E: Space rail, a narrow community sidebar with the channel menu.

All use the existing drawer, scoped content, drafts and scenario controls. Global module/account navigation, post/article/file/call content and state rules remain shared. Choosing a design from the overview opens the sample conversation; choosing within a Space retains the current destination and draft.

## Verification and delivery

Signed/tested source `4dae92cdfacd24232902ab1024174e69b4be5a0a`, maintained on `codex/spaces-native-prototype`. [Complete run 44209158851](https://github.com/robelene112/redsquare-android/actions/runs/44209158851) passes all 13 expected native methods on Android 10, Android 17 and compact 320dp / 200% font: 39 terminal passes and 138 screenshot hashes. Every profile is a fresh complete suite at the signed source, not an assembly of earlier successful portions.

The added journey selects all five designs, preserves the same unsent draft, checks current Space/channel accessibility state and B's heading, verifies fixed menu bounds after scroll and opens the existing drawer. D additionally checks that its control lies outside the message viewport. Normal and large-text design images were inspected. Debug and signed release lint pass. Independent Standards and Spec reviews report zero remaining findings, including separate review of all corrections.

The public page, complete downloaded APK, manifest, hash, package/version and certificate are verified. Separate package `nl.davidengelen.redsquare.spaces.prototype`, versionCode 3, keeps the established signer and can update earlier prototypes. Ordinary beta 139 page/metadata and immutable prototype 1/2 files remain byte-identical. No device/network permissions were added.

## Retained observations

Review corrected missing current-context/heading semantics before acceptance. Actual c68 Android 10 images revealed D covering a message action; 9b reserves space outside the viewport. Original native run 44206290024 remains failed because the compact announcement-parent text was not found by an immediate assertion. Observer 593 adds a scroll to that exact text before the unchanged assertion. Run 44208046689 remains failed because the normal Android 17 queued-status text was not found by an immediate assertion; 4dae scrolls to that exact status before the unchanged assertion. Inputs, permission/draft/cancellation goals, all 13 methods and original deadlines remain. Earlier b76/9b runs were superseded/cancelled, not accepted. Unpublished signed candidates, original logs and available captures remain retained outside Git.

Raw APKs, screenshots, logs and hashes are under `/home/david/.cache/redsquare-android-artifacts/spaces-prototype3`. Small JSON receipts here preserve the accepted source, terminal identities and image hashes.

This is a synthetic, in-memory native design study, not functional Spaces, real Chat E2E, live media/storage or physical/OEM acceptance. Owner choice among A–E remains pending. Bugs #132/#133/#135 remain open prerequisites for functional integration; #136 is separately ready for the next ordinary beta. No ordinary beta, node/provider or public financial/community write is published by this delivery. Specification #134 stays open.
