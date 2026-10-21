# Rejected complete Android10: TalkBack startup focus

Run39681561379 executes796eb1fb60ce3e63bd0e603530d6620d82278d64 and concludes
FAILURE. Social and Wallet succeed; Chat fails the original initial reader-focus
observation in TalkBackStatusPreviewTest, before the outage is triggered.
No full acceptance follows and no passing partition is reused for Android10.

The native method reports one failed test in10.297seconds. The exact original
5second post-focus observation fails at line31. The in-scope screenshot shows
Wallet focused while Reference post20 remains visible. The real TalkBack transcript
records the deliberate post-focus event2390243, then Wallet focus2390261,18ms later.
TalkBack selects Wallet as FIRST_FOCUSABLE_NODE for the earlier window-state event
2386874. This is evidence of startup focus arbitration; it does not demonstrate
an outage/recovery focus defect. The later launcher image follows Activity teardown.

A narrow fixture correction separately waits up to5seconds for actual initial
Wallet accessibility focus before the single deliberate reader-focus action.
The existing5second post-focus deadline, quiet baseline, outage/recovery speech,
real-engine completion, same-process, exact focus, real retry and cleanup goals
remain unchanged. No Wallet speech completion is required. The startup transcript
is captured before the original log clear. Product code is unchanged. Independent
source reviews report Spec0/documented Standards0/heuristic0; native correction
verification remains pending. Android17 original complete run39681571919 continues.

raw-inventory.json hashes all698 original downloaded files across three artifacts.
archive-members.json binds all8 exact members of the failed status TalkBack archive.
All native text/JSON records and the exact terminal Chat job log are retained.
These originals and successful preceding classes are context, never full acceptance.

Complete downloaded originals remain at:
`/mnt/storage/redsquare-android-artifacts/conversation62-full10-796-39681561379`.
