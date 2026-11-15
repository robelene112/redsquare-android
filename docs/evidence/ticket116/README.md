# Feed refresh spacing, issue #116

The owner’s screenshot `1000038067.jpg` asks for the refresh feedback to sit flush
against the upper edge of the feed viewport. Product and signed beta 131 source:
`d6b8083941d126377bf9e9d1a3f77c8dec7cadc6`. Review base:
`e577b31d93c1249c4f333511f1b2c7a15327176e`.

The existing transient overlay remains at the viewport edge with pointer
pass-through. Its 8dp outer top margin and 3dp shared status padding are removed.
Other status callers retain their 3dp default. This does not reserve a layout row
or claim that the overlay never covers list content. The agent’s proposed
normal-flow row was rejected after native evidence showed it clips top-edge
reading text despite preserving its absolute pixel.

## Acceptance and delivery status

- Focused Android 10 `43677904076` and Android 17 `43677905863` pass all six
  original refresh methods and both reading/applied-result methods per SDK.
  Four scenes per SDK put the strip exactly at both tabs-bottom and viewport-top.
  Strict reading pixels, physical post taps, return navigation, collapsed header,
  pending, outage and recovery assertions remain. The added expiry assertion
  retains the same reading pixel. Native screenshots were inspected.
- Standards and Spec source reviews report zero remaining findings. Independent
  complete Android 17 and signed-update evidence reviews also report zero findings.
  Final complete Android 10 review also reports zero findings, including independent
  late-rejection CLI replays. Public delivery review remains pending.
- Complete Android 17 primary `43678447389` succeeds at exact product source d6.
  It covers 318 classes, 251 archives and 831 observed identities: 826 terminal
  identities plus five verified intentional starts. It records 345 normal
  completions, 44 deliberate interruptions and 43 continuities. Four feed scenes
  have zero outer gap. See `native131-android17`.
- Complete Android 10 primary `43693979501` succeeds at admission source
  `71c117ea9e4bd4e0f6697d5288fc7e2fcc5a60bb`, with the notification-fixture-only
  bridge described below. It covers 318 classes, 251 archives and 830 observed
  identities: 825 terminal/assumption identities plus five verified intentional
  starts. It records 344 normal completions, 44 interruptions and 43 continuities.
  Three separate provisioning probes pass with 512MiB parity on their first boot.
  Four zero-gap feed scenes pass. Physical Cancel resolves in 3,876ms within the
  original 5,000ms budget, with three retained alerts and verified owned-state
  cleanup. See `native131-notification-android10`.
- Actual signed 127-to-131 updates `43680519365` and `43680521171` pass. Both
  original ZIPs and all 96 indexed raw/native originals are independently verified.
  Exact wallet, unsent draft, cached post, Light preference and separate debug
  identity survive. The frozen workflow permits baseline 127. This is neither a
  direct 130-to-131 proof nor a separate pending Vault/Store module-observer claim.
  The inherited scope descriptor says 83; actual versions and downloads bind
  127/131 independently. See `update131-android10` and `update131-android17`.
- Signed beta 131 is staged, not promoted. APK: 94,089,760 bytes; SHA256:
  `260634ed86a1487482e097611f11fa0a737a9ea7120bc43e3113ffc385f9ccc4`.
  Established certificate:
  `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.

## Source and reporting qualifications

`source-profile.json` prospectively binds the reviewed product and exact native
app, test, core, script, build and workflow trees. `verify-full.py` admits only
source descendants whose other changes are #116 evidence. It requires its exact
committed profile/adapter bytes before CI creation and hash-pins the immutable
#115 assessor, contract and classification erratum. Five exact-match adaptations
admit this source, redirect output, add geometry/provenance and label the result.
Every inherited native, archive, identity, recovery, preflight, speech, motion,
media and downstream validator remains unchanged.

Android 17 reports `CURRENT_SOURCE_PASS_WITH_INHERITED115_CLASSIFICATION`.
The original #115 frozen collector is not claimed to pass. Terminal/assumption
identities and the five verified intentional process-death starts remain separate.
The old #115 packet and both accepted old source snapshots are untouched.

The Android 10 profile and `verify-notification-full.py` bind the sole additional
fixture path, `FilesharePreviewTest.kt`, to the signed 131 manifest and source.
Ordinary app, core, resources, build, scripts, workflow and protocol source remain
byte-identical to signed 131. The fixture adjusts the API29 notification operator,
retains real alerts, and supplies only enough owned alerts to reach three. It
scrolls within actual plain-message body geometry, avoiding grouped-system
headers. Physical Cancel, transfer-specific cancellation, absent delivered bytes
and the original 5,000ms lookup remain mandatory. Owned tags/channel are removed.
API37 retains the original operator without seeding.

The original Android 17 adapter/profile remain immutable. Both reviewers reran all
accepted Android 17 assertions successfully after this fixture change. Final
focused fixture runs `43692577963` and `43692579857` pass on Android 10/17.
Focused API29 sees three existing message alerts, a gesture range of 184..469 and
physical Cancel in 3,159ms. Earlier old-operator and quiet-alert focused successes
did not prove the intermittent whole-context failure eliminated. Its deterministic
cause remains unproved.

Android 10 reports
`CURRENT_SOURCE_PASS_WITH_INHERITED115_CLASSIFICATION_AND_NOTIFICATION_FIXTURE`.
The original adapter is delegated with only the additional profile and output-root
adaptations. Private staging and suppressed preliminary stdout prevent publication
until every inherited and additional assertion succeeds. The supported real CLI is:

```text
verify-notification-full.py 10 RAW RUN SOURCE LABEL [EXTERNAL_OUTPUT_ROOT]
```

`late-rejection131.json` records a real CLI rejection after inherited assessment.
An isolated hardlinked copy replaces only the copy’s archive inode and changes
extra lookup-budget metadata from 5000 to 5001. All original bytes stay unchanged.
The CLI fails at the additional budget assertion, emits zero stdout bytes,
publishes no success packet and removes private staging. This is report-integrity
verification, not native acceptance.

## Preserved unsuccessful observations

No failed parent or successful prefix supplies acceptance. `unsuccessful-focused.json`
retains the original-gap red and rejected normal-flow observations. The following
complete Android 10 failures at unchanged d6 source remain in their original ledgers:

- `43678445624`: physical Fileshare Cancel was not located within 5,000ms. Transfer
  remained SENDING; other partitions were canceled by the ordinary same-scope
  concurrency rule when another complete run started. See `unsuccessful-full10.json`.
- `43680230627`: WalletImport verified import feedback/address, but its focused
  parent window was blank after dismissal. The original shared 15-second
  visibility budget failed. See `unsuccessful-second-full10.json`.
- `43683761408`: the supported Ubuntu 22.04 pool exceeded the original 15-second
  wait for 20 creator refreshes under the unchanged method tracer. See
  `unsuccessful-third-full10.json`.
- `43687853067`: the standard Ubuntu 24.04 pool again failed the physical
  Fileshare Cancel lookup. See `unsuccessful-fourth-full10.json`.

Unchanged-source focused Fileshare `43680228412`, import `43683570303` and
Vault-reading `43687862233` pass the original assertions. The latter records 20
traced refreshes in 645ms. These are diagnostic observations, not complete-run,
host-cause or physical-performance proof. No native deadline, refresh count,
tracing mode or normative goal was weakened. Earlier causes remain unproved.

A local lint attempt with changing inputs failed. A redundant later local lint
attempt was stopped after all six clean hosted build/lint steps passed. Neither
supplies acceptance. The stable-source signed release build and release checks pass.

## Retained limits and storage

The inherited single API29 Profile assumption, unavailable Chat archive and four
missing legacy failure captures remain explicit. Physical/OEM, real selected-node,
provider, deployed-schema, funding and Play gates remain under #36 and
`OWNER_INPUTS.md`. Source-only Vault rental restoration remains UNDEPLOYED.
No node/provider/Play operation or public financial write occurred. Parent issues
#1, #36, #55, #84, #86 and #105 remain open.

Original ZIPs, native archives, downloaded APKs and temporary helpers remain outside
Git under `/mnt/storage/redsquare-android-artifacts/feed-notice-fix`. Packet indexes
bind the retained bytes. No APK is committed. Work stops after delivery of #116
until the owner requests another ticket.
