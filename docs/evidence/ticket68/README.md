# Settings UI evidence for issue 68

Issue [#68](https://github.com/robelene112/redsquare-android/issues/68) reorganizes Settings into clear destinations for Privacy and safety, Notifications and privacy, Node, Appearance, and About. Node selection is a full-row radio choice. The app keeps its wallet and draft when navigating, exposes only the two supported nodes, restores the saved choice after a write failure, and keeps policy/contact and notification status truthful.

## Before and after

The original [Android 10 Settings capture](../ticket38/full10/observed/about-light.png) shows the previous single long dialog, with node radios, appearance, and About content mixed together. The screenshots here come from the revised Settings flow on hosted Android emulators. The test sets the emulator font scale to 130%, asserts the Settings categories are visible after returning from Appearance, and only then captures the overview.

- Android 10: [light overview](android10/settings-overview-light.png), [dark overview](android10/settings-overview-dark.png)
- Android 17: [light overview](android17/settings-overview-light.png), [dark overview](android17/settings-overview-dark.png)

Each platform folder also contains the node detail and controlled save-error state, notification and privacy details, About, policy overview and saved-draft recovery screenshots. The 130% font scale is recorded in `settings-130-font-scale.json`.

These are emulator observations. A physical Pixel 7 walkthrough has not been performed.

## Accessibility and behavior

Each platform's `talkback/` folder retains the actual keyboard-focus capture and XML, TalkBack's focused owner-node and official-node trees and speech logs, plus the machine-readable result. Both named node rows are matched to separate focusable radio rows. The owner row is checked; the official row is unchecked. TalkBack announces the selected state and identifies `saito.io` as a radio button. The controlled test does not submit a transaction or change the wallet.

The two nodes, wallet preservation, draft restoration, appearance recreation, node-save error recovery, policy drafts, notification preferences, and username/IME behavior are exercised by the linked CI runs. The emulator speech result is not a claim that physical audio was heard by a person.

## Verification

The relevant focused runs at the completed implementation are:

- Android 10 organized Settings and 130% rendering: [run 40717921085](https://github.com/robelene112/redsquare-android/actions/runs/40717921085)
- Android 17 organized Settings and 130% rendering: [run 40718662692](https://github.com/robelene112/redsquare-android/actions/runs/40718662692)
- Android 10 policy and node TalkBack: [run 40718669494](https://github.com/robelene112/redsquare-android/actions/runs/40718669494)
- Android 17 policy and node TalkBack: [run 40718675688](https://github.com/robelene112/redsquare-android/actions/runs/40718675688)
- Notification settings, Android 10 and 17: [run 40720053227](https://github.com/robelene112/redsquare-android/actions/runs/40720053227) and [run 40720058835](https://github.com/robelene112/redsquare-android/actions/runs/40720058835), 32 tests per version
- Username and IME, Android 10 and 17: [run 40720064079](https://github.com/robelene112/redsquare-android/actions/runs/40720064079) and [run 40720071978](https://github.com/robelene112/redsquare-android/actions/runs/40720071978), 16 tests per version
- Publisher Settings routes, Android 10 and 17: [run 40705575918](https://github.com/robelene112/redsquare-android/actions/runs/40705575918)
- Social notification Settings path, Android 10 and 17: [run 40711689617](https://github.com/robelene112/redsquare-android/actions/runs/40711689617)

The whole Android 10 suite at [run 40712878733](https://github.com/robelene112/redsquare-android/actions/runs/40712878733) stopped in `GroupInvitationRecoveryPreviewTest` while waiting for the separate group invitation flow's “Paste invitation” screen at 200% text. It is not a Settings test. The targeted Settings, policy, publisher, notification, and username checks are reported independently above; this issue does not claim that the unrelated full-suite failure passed.

The six final CI runs above for policy, notification, and username behavior completed successfully on their specified Android versions. The appearance run also passed on both versions. Paired final reviews reported zero remaining Spec findings and zero Standards findings.

## Signed preview

Beta70 is published as an in-place update at <https://davidengelen.nl/redsquare/>. Its package is `nl.davidengelen.redsquare`, version `0.1.70`, APK SHA256 `ce36ee0bd66f4067f4c7d509987794edc4ac931da93beec7d9ac8e95b0cd66ac`, and signing certificate SHA256 `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`. The public APK was independently downloaded over HTTPS, checksum-compared with the local signed artifact, and checked with `apksigner` and `aapt`. See `signed70-public-verification.json` and `verification-source-equivalence.json`.
