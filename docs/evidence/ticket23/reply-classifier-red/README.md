# Signed request classification RED

Two-axis reply-slice review: Standards0hard/0actionableheuristics; Spec1wrong
classifier. A parent reference alone mislabels a signed commentary retweet as a
reply, contrary to the create-tweet reply contract. This independent pure fixture
has a valid unsafe2 signed retweet request, valid parent/thread/root target and
actual owner recipient. It is an adversarial classifier case, not observed live
module output. Existing independent incomingReply is the positive companion.

One public core readReplies test reproduced the finding before the correction:
1test/1failure/0skips/0errors,0.413s; total build57s. Existing parent-only logic
returns both Reply notification reference and Quote carrying a parent reference;
expected only the reply. The initial reader extraction preserves the existing
parent-only criterion for RED. Fixed reader request-check/quiet valid ineligibility
and native adoption are pending GREEN. No emulator/production/chain proof here.
