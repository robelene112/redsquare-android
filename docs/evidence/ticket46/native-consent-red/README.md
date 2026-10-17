# Native background consent RED

Actual Android10 run39057745908 at935c7787d2afc35ac09f78996d3f81c4dc1fe1a5 fails the new consent journey at its first missing Chat alerts when closed Settings choice after20seconds. All five existing Chat alert cases pass; six run, one fails in76.143s. PNG/XML confirm Settings shows social consent and private previews without the missing background choice. No union HTTP assertions or new persistence checks are reached. Android17 is unexecuted.

The earlier local compile failure was a fixture variable typed as MockWebServer instead of ControlledFeedPeer; corrected before this runtime. It is retained as a compile failure, not the behavior RED. Seven hashed records preserve both outcomes, runtime source hash and exact run metadata.

Later861a5b219 corrects failure-diagnostic suppression and requires a positive Activity shutdown barrier before protected restoration. Both bounded reviews of those corrections return zero remaining findings. Behavior assertions and deadlines are unchanged; this immutable RED uses the original935 fixture. Production implementation and corrected-fixture runtime validation follow separately. No Google delivery or whole#46 acceptance claim.
