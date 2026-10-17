# Firebase client source preparation

Bounded[#32](https://github.com/robelene112/redsquare-android/issues/32) source work,
review base4f7000c29463a044a2060887bfdcd4d28a21cf25; corrected source reviews at
2310e0df report Standards0/Spec0. This is source preparation and unconfigured
regression, not a configured SDK or cloud-delivery acceptance checkpoint.

| Check | Exact2310e0df56d7715d25fe7b3123bd5031dcd02d10 result |
| --- | --- |
| Android10 /38989625467 | SUCCESS,11 social87.193s plus3 worker46.541s |
| Android17 /38989627211 | SUCCESS,11 social129.468s plus3 worker47.423s |
| Source /38989625277 | SUCCESS, core/debug/test/release/lint |
| Local corrected build | SUCCESS, core/debug/test/release/lint,10m15s |

Both native runs report core114/0/0. Exact metadata, passing logs and one actual
opaque-reply alert PNG/XML per SDK are retained here. Root viewed both originals
and parsed their XMLs. All other existing assertions, including retry, duplicate
postTime, preservation and scoped restoration, pass in the retained logs. Their
remaining captures stay in the raw run; no new visual-review claim is made for
them. Captures are sequential. This repeats existing app flows with the new SDK
present and PUSH_CONFIGURED=false; it does not exercise configured initialization,
SDK register/unregister callbacks or actual Google receipt.

[Build observations](build-observations.json) record the actual merged Application/
service/FID metadata, absent FirebaseInitProvider, disabled automatic registration/
collection/delegation, default unconfigured fields and resolved Fragment1.8.9 for
both variants. The resolved Messaging26.0.0 AAR matches the previously independently
retained public binary. See[configuration and lifecycle](../../../PUSH_CLIENT_CONFIGURATION.md).

Initial uncommitted Gradle setup failed23s at java.net name shadowing; an explicit
URI import corrected it before258ec4f7. That source then compiled debug/test code,
but release lint failed on Play services' old transitive Fragment1.1 at the existing
wallet ActivityResult calls. The remaining owned build was stopped, exit143;
it remains a failed/stopped attempt, not PASS. Logs are retained separately.
The1.8.9 constraint fixes the dependency without suppressing lint or broadly
upgrading Activity/Lifecycle. Initial review Standards1/Spec2 also found stale
retry ordering and unfinished SDK tasks;2310e0df corrects both. No TDD RED claim.

Worker source now coalesces current demand, preserves durable pending phases and
retains/drains in-process unfinished Google Tasks before another SDK operation.
Matching callbacks avoid self-cancellation; unexpected callbacks request fresh
reconciliation. These are reviewed source properties, not configured callback,
timeout, rotation, process-loss or remote-ordering runtime proof.

Final committed-evidence review pending. No unchanged rerun needed. Required
separate[gateway#42](https://github.com/robelene112/redsquare-android/issues/42)
is published as a #32 sub-issue and remains unassigned until this checkpoint is
finished. Signed gateway binding/revocation/observer, actual Firebase configuration,
Google delivery, both-backend transport and full/physical acceptance remain open.
No project/account/IAM/node/deployment mutation or new beta. Existing owner setup
question stays pending, without repeating it. Public signed beta24 is unchanged.
