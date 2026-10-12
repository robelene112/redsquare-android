# In-app notification compatibility reference

Issue#24, accepted seams1–4. Generated2026-10-12 from the pinned SaitoWASM0.3.2
andElliptic6.6.1 using pure signed-reference.cjs. WASMhashf59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e,
sourcepin9a4ca7e9442eb5e872168ced361ce741d7d139d7. Published unsafe scalars1/2
only; never fund. No node/wallet/options/database initialization or network writes.
The reference helper checks WASMpreimagehash/signature, independentEllipticverify
andfulltransaction roundtrip before exposing detached Archive envelopes.

First tracer combines the independently signed existing root post from
NATIVE_THREAD_REFERENCE.json (unsafe1author) and a newly independently signed
unsafe2 `like tweet` referring to that root, with actual unsafe1 outputrecipient.
Archive field3 names unsafe1 and field4 names the original, matching both inspected
module indexing contracts. Metadata alone never supplies signer/eligibility.
The next tracer adds an unsafe2 signed reply with the root as parent/thread and an
actual unsafe1 output recipient. Both signatures and decoded thread identifiers
come from the pinned independent generator. The original like-only snapshot was
2980 bytes, SHA2562b8a817154dae7ab0918baaed1f792a74e062b904d33a3b35d05d075502149c9.
The reply-only snapshot was4597bytes/SHA25626d25c2d23cf46a020e9466f6cb09520a6cbe8f265919c058c6fe5f3eedbc6e3.
A subsequent independently signed `retweet` quote deliberately carries a valid
parent reference. It is an adversarial request-classification fixture, not an
observed deployed module output: a parent field does not change its signed kind.
The parented-quote snapshot was6267bytes/SHA476cfd8c86bd3cf1198c1c57dc495d68d5a17ee5b30c50df97f1483de8fa113d.
The next tracer adds an exact text mention with punctuation and an equally signed
addressed ordinary post without a mention. Both have actual owner recipients;
addressing alone does not distinguish eligibility.
The text-mention snapshot was8259bytes/SHA c52b0b8439aad566731d4d4b6c27c655b513be7d50855d5763beb132097c915f.
The next tracer adds an independent signed create-tweet `mentions:[owner]` with no
public-key token in its text. Both pinned constructors preserve signed mention
arrays; the native parser bounds recognized arrays to100items and requires exact
string identity membership. Ordinary addressing remains insufficient.
The mention-list snapshot was9290bytes/SHA c97515e7b98702131749721af26d6d4b561fab99cc72f731587be6f0100e79ab.
The next tracer adds a signed exact @NativeCase7@saito text mention. Its owner
mapping comes from the pre-existing independent NATIVE_REGISTRY_REFERENCE.json:
unsafe2 synthetic registrar signature proves NativeCase7@saito belongs to unsafe1.
This synthetic authority is not the published production registrar or a live
registration/chain-state proof. Reader tests verify ownership proof and reject
wrong authority, owner or corrupt proof instead of trusting a label.
The username snapshot was10287bytes/SHA23c9c5a3597694eeabdfadff677a6f20a3f2062a6c94ffe338b242697978b828.
Next tracer adds an independent ordinary `retweet` of the root with actual owner
recipient, alongside the already signed commentary quote for kind distinction.
The ordinary-repost snapshot was11518bytes/SHA787f2013f17086a177ff6c561aad6571ad4a215ae9051729cbf2d67de178a5ba.
Next tracer adds current `retweet` and legacy `create tweet` dual-field commentary
quotes with actual owner recipients, original signed transaction bytes and matching
legacy embedded presentation. Both quote and original are independently verified;
this is synthetic compatibility evidence, not live delivery or permanent history.
The current/legacy quote snapshot was16109bytes/SHA17edeff358ba5fd060b1252d0e7d186b2ef951d7ae91c603e166ad0e365cd087.
The next native tracer adds a separate independently signed own original and an
unsafe2 like of it. The controlled peer withholds that original on first lookup,
then returns its independently signed bytes on explicit Retry. The ordinary feed
root has a different signature. Dedicated public original-cache bytes are preserved,
cleared atomically for setup, and independently restored; no wallet reset occurs.
The first missing-like snapshot was18124bytes/SHA8ae7667601b311d1154fb81d8988f3ad68aa7836194947eda270ec7aff7a3c32.
Next tracer adds an independently signed ordinary repost of the unavailable
original and a verified non-own post with an owner-addressed like of that post.
The latter must remain ineligible because its original author is already known;
owner output addressing cannot override signed non-ownership. Both missing-like
and missing-repost journeys supply that adversarial activity and observe captions/
actor row counts while retaining full original-cache restoration.
The missing-repost/known-other snapshot was21373bytes/SHAf67062351b533f5aa466d0b1b159ab860dbf145bab1436dd4ff121ee5cc6400b.
Next tracer adds an independently signed unsafe1 current commentary quote plus
unsafe2 incoming like of its own signed quote ID. The quote's original remains
separately authenticated. Its queryable namespace is RedSquareRetweet; ordinary
RedSquare target reads return empty, and Retweet reads withhold it once before
returning it on explicit Retry. Actual namespace-specific read counts are retained
without transaction contents. This is a synthetic compatibility/recovery proof,
not a live archive observation or chain inclusion.
The current-quote-target snapshot was24557bytes/SHA df18fa267c99d457bdab01b4a41f88e83fb92096e740efb492012c9ddffaf06f.
Next public-reader tracer adds an independently signed ordinary retweet with
text:null and images:null. Both optional values mean absent commentary in the
ordinary reader. The quote reader used for cached validation must quietly exclude
it as a valid ordinary repost, rather than reject its signed bytes.
Current whole JSON SHA256:0b2ccd40b8b0adb597480e81987e3e04522353805de09000d4f20fc132e6fc95; 25828bytes.
Documentation/native/core copies are identical.

Reproduction uses the existing build image, networknone/read-only container and
rootfilesystem mount, root inside the container for chroot, empty environment,
/opt/saito-runtime/node-v24.13.0/bin/node and the exact tracked generator path.
The first attempt inherited the image's unprivileged user and failed chroot before
Nodeexecuted. Corrected `--user0` with unchanged read-only/network isolation passed.
This is reference-runner setup failure, not cryptographic/application/node failure.
No hostsecurity policy/capability/production configuration changes were made.

The first native journey uses the existing realbinary authenticated controlled
peer andisolated .dev emulator. It observes the feed before opening Notifications,
skips contextualpermission, expects incominglike/verifiedactor, opens original
Conversation andactorProfile, verifies actualowner-index reads/no emittedpayment/
report, then restores full opaquepriorwallet andpriorbackend independently.
The first like journey passed hosted Android17 run38274447518 after genuine RED
38271177276; see NATIVE_NOTIFICATION_VERIFICATION.md. Reply, text/username mention and repost runtime slices now pass; see the verification chronology.
No whole-ticket/closed-app/Pixel acceptance yet. Each native case restores the
prior contextual-permission offered key independently, preserving unrelated preferences.
Primary source contracts remain NATIVE_NOTIFICATIONS_CONTRACT.md. Existing source
A/B differences, missing originals, mentioncoverage and archive-retention limits
must be preserved rather than claiming complete network history.
