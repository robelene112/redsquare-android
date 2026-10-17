# Signed subscription: first local acceptance

Source41cf0c76, bounded minimum basef5c148f7 and whole-ticket base5a048524.
[Actual compiled source metadata](source-files.json) matches the committed source.
Corrected local `:core:test :push-gateway:test` succeeds36s: one public HTTP
journey PASS, core task up-to-date with114/0/0. Earlier positive minima also
succeed35s/39s/34s, separately retained. The unchanged first challenge RED remains
[failure](../subscription-red/README.md); its later tails were unexecuted there.

The normal flow obtains a challenge, binds the synthetic installation using the
real core wallet signature over an independent literal intent, closes that
runtime, starts another on the same state directory and observes retained owner/
backend/expiry through HTTP. Retirement carries only the separate installation
authority, without a wallet key/signature. Status becomes inactive and the old
bind returns410 instead of resurrecting it. Both listeners/executors/file locks
close through scoped ownership. This is server-instance restart, not a JVM
process crash, power-loss or Android runtime proof.

Source reviews atc3abb7a6 report Spec0/Standards1 (invalid integer classified503).
Correction41cf0c76 now reports Standards0/Spec0: request integers400, stored-field
failuresIO/503. No extra targeted campaign or live operation was used. Durable
acknowledgement and limits are in the[current contract](../../../PUSH_GATEWAY.md).

Final committed-evidence review and exact-source CI are pending. CI now invokes
the gateway test and keys its dependency cache on the new module build file.
App/core source remains unchanged; no additional unchanged Android rerun is
claimed. Full-harness wiring, configured SDK and actual Google delivery have their
own remaining acceptance. No standalone command, renewal, Android binding,
resolver/observer/sender, automatic physical cleanup, request-body timeouts or
production deployment readiness is claimed. #42 and #32 remain OPEN; beta24
unchanged. Existing owner Firebase setup question stays pending, not repeated.
