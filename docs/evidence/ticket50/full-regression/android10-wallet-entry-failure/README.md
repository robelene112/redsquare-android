# Failed complete Android 10 run: initial Wallet entry

Run 39106583903 is FAILURE at `10b007c48e9b6e48037741544a4f181e7337ab9a`.
There are 402 PASS, the existing single Profile assumption and one failed case;
88 normal completions, 32 deliberate interruptions and 28 PASS continuity
records are retained. This is incomplete acceptance. The fixed 430-identity
map has 403 passing/assumed identities, with 27 missing successful outcomes:
one executed failure and 26 unexecuted identities. No expected map is generated
from this failed run. Later full-suite gates remain unexecuted.

`WalletImportPreviewTest.failedSavingThroughConfirmedImportRestoresTheVisibleAndSavedWallet`
fails at its initial `onNodeWithText("Wallet").performClick()` on line 51.
The merged lookup finds zero nodes, while the error reports one matching
unmerged node. The actual import selection, filesystem fault, restored-wallet
assertions and recreation tail in this case have not executed. Six other cases
in that seven-case command pass; the class takes 183.416 seconds within its
unchanged 300-second host bound. Instrumentation/JUnit reports the failure even
though no nonzero diagnostic exit file exists.

There is no failure-time window capture. The post-class system PNG has zero
bytes and cannot be viewed; it cannot establish what the user saw. The other
888 PNG and 773 XML observations validate structurally. The optional Chat image-
history archive also has its established missing-directory text. The original
346-file inventory, complete parsed outcomes/cases and fixed-map comparison are
retained. No rendering, lifecycle or application defect is proved by these data.
Transient action readiness is a hypothesis, not an established cause.

The bounded fixture correction waits for the actual merged Wallet action to be
enabled and displayed before the failing scenario's two entry clicks, and takes
an in-scope PNG/XML if that entry fails before scenario teardown. No unmerged
fallback, removed assertion, changed identity, production edit or widened
existing request/class deadline is proposed. Focused import proof and a fresh
complete Android 10 run are required. Complete Android 17 at the same production
source remains SUCCESS with its reviewed packet. Main35 is unpublished, main31
and separate performance36 remain public. This failed run never becomes SUCCESS.
