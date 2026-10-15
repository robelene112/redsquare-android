# Profile exact-text matcher attempt, Android10

Run38765277087 at7395431a remains FAILED. ProfileEditPreviewTest reports7cases,
5PASS/2FAIL19.449s. Both failures are dismissTypingForWallet35, where
assertTextEquals compares merged Text=[Bio] alongside the actual EditableText.
The raw semantics preserve the full expected multiline bio in both cases;
visible Wallet was already observed. These are not lost-bio observations.
Later import-warning/storage-block/repair/retry assertions in these two cases
are unexecuted. The other classes report5/23.037s (4PASS plus the existing API29 modern-fault
assumption),2PASS/16.639s and2PASS/23.565s.
Profile process recovery is unexecuted after the accumulated class failure.
Core114/debug/test assembly passed. No complete profile/full acceptance.

Standards review identified this matcher before this run completed. Correction
b6885691 compares only exact SemanticsProperties.EditableText, as existing
class assertions do. Wallet visibility, exact bio and all original import
safeguards remain. Both correction reviews0. Current finalfull10 atede956c5
includes the correction; its actual outcome remains pending. No production,
budget, node, wallet, credential, service, chain, store or physical Pixel change.
Two original failure PNGs and parsed XMLs retained. Root inspected both PNGs;
these are separate snapshots, not atomic proof of every assertion or window.
