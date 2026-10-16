# Scheduled-receipt attempt interrupted by group editor assertion

Run[38979224052](https://github.com/robelene112/redsquare-android/actions/runs/38979224052)
FAILURE at67293afb6637657a56d8234227426ef608ac7535. Existing social class has
11cases,10PASS1FAIL82.607s. The new scheduled-receipt case DID NOT RUN: the harness
stopped after the earlier group-chat final editor assertion at389, called at266
before group publication/tap. Its later group tails remain unexecuted.

The15s same-AnnotatedString wait returned, but the subsequent same-value assertion
failed. Expected and printed text look identical. Root viewed the original group
failure PNG and parsed all44 XMLs; image shows latest text/photo and active IME.
The exact value/metadata at predicate time was not captured, so neither an IME
cause nor data loss is established. The prior wait did not resolve this repeated
observation failure. No actual worker RED or processing acceptance is claimed.

[AnnotatedString source](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/AnnotatedString.kt)
compares text and annotations, but prints only text. This explains why the diagnostic
cannot distinguish those values; it does not prove this failure's cause or pin
that source tree to the installed binary. A correction will assert the exact full
plain-text value required by the saved-work contract, explicitly ignoring formatting
annotations. This changes the predicate, while retaining the same text, wait and
final assertion plus existing persisted draft/photo/no-candidate checks.

Core114/0/0 and native source assembly passed. Separate source push38979223612
SUCCESS includes release/lint. Android17 was deliberately skipped. adb exit0 is
not a JUnit pass; the raw failure and absent scope/work log remain truthful.
SHA256SUMS covers retained originals/logs/metadata.
