# First final Android10 run stops at existing import fixture

Full10 run38145371958 at6c489bfa700e1fde73c3f43ba446980c11ad58d4 stops
before search. Browse8PASS21.019s, Following8PASS23.037s and actual posting
process-death/cold reconciliation PASS. Posting reports18cases/17PASS/1FAIL
102.61s: queued-work wallet replacement cannot locate the reference import at
PostPreviewTest.kt:762. Later search/public-read/remaining suite stages did not
execute. Source/core/debug/test/release/lint passed.

Root individually viewed original PNG and separately parsed XML: native wallet
dialog is present, without a DocumentsUI picker or selected import preview.
This establishes the observed state, not an exact scheduling cause. The old
local selector does not synchronize Compose while the app remains foreground,
and broadly matches provider titles/unscoped scrollables. Maintained shared
chooseWalletImport instead synchronizes the native app, scopes provider drawer
selection and requires the incoming validated preview. A source-only fixture
correction will reuse it, retaining backup, identity, queue and no-send gates.
Add file-existence/positive-size setup check without reading its contents.

Raw log and separate observations retained unchanged. Android17 remains active;
no production defect, weakened wallet safeguard or final acceptance inferred.
