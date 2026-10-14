Run38513845554 at455d69ba on Android17: one test, one failure/24.909s.
First missing Groups wait at fixture48; original PNG/XML show opened private Chat,
no Groups entry. Root inspected both. Later create/membership/invitation/reopen
assertions did not execute and do not count as acceptance. Source gate38513831069
SUCCESS at the same head, separately from UI failure.

Before GREEN, review corrections compare exact normalized broadcast/Relay originals
and all four invitation claims. Recreate retains Chat via rememberSaveable, so the
fixture goes directly to Groups rather than clicking the nonclickable Chat title.
Delivery copy says Forwarded, with delivery explicitly unconfirmed. No assertions
or deadlines are relaxed.
