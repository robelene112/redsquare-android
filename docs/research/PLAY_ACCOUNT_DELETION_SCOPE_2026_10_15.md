# Play account deletion scope, 2026-10-15

Release preparation for [#36](https://github.com/robelene112/redsquare-android/issues/36),
under the approved [#1](https://github.com/robelene112/redsquare-android/issues/1).
Research only: no implementation, submission, owner approval or legal conclusion.
No app/node/account/configuration/credential changes or live probes occurred.

**Do not declare an exemption merely because the wallet is noncustodial or has
no email/password.** Whether this network identity is an app account still needs
a supported publisher determination and actual Play Console review.

## Explicit Google policy

Google's [account-deletion FAQ](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
defines an app account as a user-facing unique identity serving users across
apps/devices, with authentication. Email/password are examples, not mandatory
fields. Creation anywhere in the experience counts, including an external flow
the app directs users to; optional creation does not avoid scope.

The FAQ expressly excludes accounts created **and operated offline**, and exempts
permanently private and enterprise-device-management apps. No anonymous-keypair,
noncustodial-wallet, blockchain or third-party-network-account exemption was found
in the inspected Google deletion documentation. This is a bounded finding, not
a claim that Google has ruled on this application.

The [full User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
requires a discoverable deletion-request path inside an in-scope app and outside
it, with the external URL supplied in Play Console. Account deletion must also
delete associated user data; deactivation or freezing is insufficient. Legitimate
retention, such as security, fraud prevention or regulatory compliance, must be
disclosed. A privacy policy must identify the app/developer, data practices,
contact, retention and deletion practices, and remain publicly accessible.

The FAQ permits an in-app link to the deletion resource and a web form or support
email pathway. The page must identify the listed app/developer and visibly allow
requests without reinstalling the app. State expected processing time. Delete
developer-held data and request deletion from service providers processing it.

Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
separately distinguishes purely local processing from off-device collection.
Pseudonymous off-device data remains reportable; user-initiated sharing exceptions
are not blanket account-deletion exemptions. The publisher remains responsible
for accurate declarations. These form definitions do not themselves decide
whether a Saito keypair is an app account.

## Application facts and inference

The [approved context](../PROJECT_CONTEXT.md) requires automatic local key creation,
compatible wallet import/export across devices, signed social/Chat activity,
optional public profile publication and optional network username registration.
The [peer handshake](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt)
already authenticates with the wallet identity. Private key custody stays local;
this does not make the public identity's operation offline.
[Profiles](NATIVE_PROFILE_EDIT_CONTRACT.md) and
[Registry](NATIVE_USERNAME_CONTRACT.md) publish signed network records, rather
than creating a conventional publisher password database.

**Inference:** those transferable, authenticated, online features make the
offline exception an unsafe release assumption. Publishing a profile or registering
a username is not proven exempt merely because another operator stores the
record. Conversely, the cited policy does not explicitly classify self-generated
decentralized identities. Record the actual Console answers and obtain policy
clarification if the publisher cannot substantiate the classification.

## Concrete deletion boundary to resolve before release

The [implemented post deletion](NATIVE_AUTHOR_ACTIONS_VERIFICATION.md) concerns
normal-view removal on the selected backend and preserves historical-copy
warnings. That is not demonstrated whole-account/data deletion. Clearing a local
wallet alone cannot remove remote profiles, registry records, independent archives
or another device's backup. Removing the last key copy can also destroy access
to funds and signed deletion authority; an eventual flow must preserve explicit
backup/authentication safeguards and explain consequences before destruction.

Prepare an inventory separating local identity/work/cache, publisher-operated
backend records, any future push-token subscriptions, service-provider data and
independent network copies. Establish which party can actually delete each,
the request/verification mechanism, completion evidence, timeframes and justified
retention. Do not assume saito.io is a service provider acting on this publisher's
instructions; its actual role is unverified.

Disclose which records remain, where and why. **Technical irretractability or a
disclosure alone is not an explicit Google retention exception.** The inspected
policy does not settle acceptance of independently retained chain/archive copies.
Do not promise worldwide erasure or treat a reset as deletion compliance.

Next release step: prepare this inventory and a concrete in-app/web request
process for publisher review, then resolve the account/decentralized-retention
classification against actual Console requirements or Google policy support.
No support message, Console declaration or release publication is authorized or
performed by this research note; #36 remains open.
