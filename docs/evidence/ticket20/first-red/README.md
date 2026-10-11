# First personal-controls native RED

Android17 run38149454797 at056d9a3e99b6968e9870e2ae4298704446bd0088
builds/core-tests successfully, then reports1FAIL29.531s. The new native flow
reaches the wait for Mute account at test line70 and times out after15seconds;
that control is absent from the pre-implementation Profile source. Prior wallet,
feed scrolling and author-link actions execute before that wait.

Root individually viewed the original PNG and separately parsed XML. PNG is a
blank closed-app surface and XML contains no app nodes. The outer failure capture
ran after ActivityScenario.use closed the failing Activity. These observations
cannot establish the visible Profile or any later mute/persistence behavior.
Wallet/client/zero-transaction end assertions were not reached. No GREEN claimed.

Correct the fixture by capturing inside each live Activity and positively waiting
for Profile before the missing Mute control. Capture errors still preserve the
primary assertion. Retain this raw first failure and limited observations unchanged;
repeat the focused native RED on the corrected fixture before product code.
