# Wallet backup, import and payment flow evidence for issue 67

Issue [#67](https://github.com/robelene112/redsquare-android/issues/67) replaces nested wallet dialogs with focused steps for backup, import, receive, send and payment review. The active identity stays visible during replacement review. Backup and import warnings explain key custody, local drafts and posting records, and the history that will not be restored from a wallet backup.

## Reviewed Android captures

These Android 10 captures use synthetic test identities and payment values. They contain no private keys.

- [Import replacement review](android10/import-review.png)
- [Payment confirmation review](android10/payment-review.png)
- [Wallet recovery guide](android10/recovery-guide.png)

The recovery guide states that the saved JSON is not encrypted, authentication protects the export step rather than the saved file, and cached chat history is not included in the backup. It explains that history availability depends on the selected node and compatible peers.

## Focused emulator verification

All listed runs use the final implementation sources. Android 10 is API 29 and Android 17 is API 37.

- Payment, send review, receive and wallet viewport: [Android 10 run 40759555267](https://github.com/robelene112/redsquare-android/actions/runs/40759555267), 10 transfer tests plus 1 viewport test; [Android 17 run 40759555718](https://github.com/robelene112/redsquare-android/actions/runs/40759555718), 10 transfer tests plus 1 viewport test.
- Export and backup recovery: [Android 10 run 40760874463](https://github.com/robelene112/redsquare-android/actions/runs/40760874463), 5 export tests plus 1 recovery-guide test; [Android 17 run 40760874336](https://github.com/robelene112/redsquare-android/actions/runs/40760874336), 5 export tests plus 1 recovery-guide test.
- Import, risk and storage recovery at the final commit: [Android 10 run 40762168558](https://github.com/robelene112/redsquare-android/actions/runs/40762168558), 7 import tests, 1 risk test and 1 storage recovery test; [Android 17 run 40762168595](https://github.com/robelene112/redsquare-android/actions/runs/40762168595), the same 9 tests.
- Wallet TalkBack payment review: [Android 10 run 40767894689](https://github.com/robelene112/redsquare-android/actions/runs/40767894689) and [Android 17 run 40767894882](https://github.com/robelene112/redsquare-android/actions/runs/40767894882), 1 test per version. The real TalkBack service completed speech for the recipient, amount, fee and selected node. The test verified wallet identity was preserved and sent zero transactions. It does not claim a person physically heard the synthesized audio.

Every focused run completed successfully. The Android 10 and Android 17 import runs include the final provider drawer selection harness. Native Spec and Standards reviews reported zero findings for the final change set.

The combined TalkBack runs were not used as wallet evidence. Android 10 [run 40763940576](https://github.com/robelene112/redsquare-android/actions/runs/40763940576) stopped at a separate profile speech test before reaching the wallet test. Android 17 [run 40763940636](https://github.com/robelene112/redsquare-android/actions/runs/40763940636) reached the wallet test but used the earlier Compose test click path. The final isolated wallet runs activate Send through Android's accessibility click and focus the visible value labels directly before confirming completed TalkBack speech.

## Full-suite limitation

The complete Android 10 suite [run 40752023116](https://github.com/robelene112/redsquare-android/actions/runs/40752023116) failed in separate chat and social tests. Its wallet partition also exposed two import-test flows that tried to open another wallet action while the import review was active; the tests were corrected to leave the review before continuing, and the final focused import suite passes on both Android versions. The complete Android 17 suite [run 40752022535](https://github.com/robelene112/redsquare-android/actions/runs/40752022535) failed in separate publisher, chat and social tests. Those complete suites were not rerun, so this evidence does not claim an all-app suite pass.

These are hosted emulator results. They do not establish a physical Pixel 7 walkthrough or Play Store acceptance.

## Signed beta71

Beta71 is published as an in-place update at <https://davidengelen.nl/redsquare/>. It is package `nl.davidengelen.redsquare`, version `0.1.71`, with APK SHA256 `51fcc0e2beca12c052976cf74190d9079ff85bf80268a7de478556d9954fe2d9`. The signing certificate SHA256 is `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`. The APK is nondebuggable, supports Android 10 and later (min SDK 29), and targets SDK 36. Public HTTPS verification and implementation-source equivalence are recorded in `signed71-public-verification.json` and `verification-source-equivalence.json`.
