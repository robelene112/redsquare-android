# Corrected full follow-up, Android10

Run38765859044 atede956c51d1234fe5d924f1a46d5b40e45db1646 remains FAILED.
ProfileEditPreviewTest reports7cases6PASS/1FAIL. New helper35's exact
AnnotatedString comparison fails for the fresh-bio case despite the formatted
expected/actual EditableText showing the same text. Original PNG/XML show retained
multiline bio and restored header. This does not prove text loss, an annotation
mismatch or a timing cause. The other profile classes and their existing API29
assumption remain separately retained. Later import assertions in this failed
case, profile process recovery and downstream full gates are unexecuted.
Correction796e4ad3 uses a bounded10s exact EditableText.text String matcher and
requires displayed/enabled editor, preserving all characters and later safeguards.

Root inspected the original failure PNG; XML parsed. Other completed logs are
indexed without counting failed classes or upgrading this run to full acceptance.
The four keyboard cases complete successfully, but are not the complete gate.
Both runs include source/core114/release/lint checks before device testing.
No complete full runs remain active after these failures. Public21 stays current;
signed22 remains unpublished. Production source is still720e2007, no node, live
wallet, service, chain, credential, store or physical Pixel change/acceptance.
