# Early visual beta

Owner priority #37 follows completed private Chat #25 before group Chat #26.
Review base: `ca2ade6a`. This is an early visual slice of #35, not final
integrated-feature or physical Pixel acceptance.

## Appearance tracer

The approved platform UI seam exercises first-install System default under
Android Dark, explicit Light while Android is Dark, persisted Light across
activity recreation, explicit Dark while Android switches to Light, persisted
Dark across recreation, and returning to System default under both settings.
It observes rendered native pixels and full device-only wallet continuity,
backend selection and absence of controlled-peer transaction/report publication.
The isolated emulator harness clears only the synthetic `.dev` application
before any feature fixtures run. No production app data is reset. Original
supported Android night mode is restored; unsupported modes fail before mutation.

Test/harness source `192976f8`. Local Gradle reports BUILD SUCCESSFUL in 1m40s for test APK; Docker wrapper
exited143 after that output. No owned build container remains; do not claim
a clean wrapper exit.
Actual RED dispatched on Android 10: Actions `38491630186`, expected source
`192976f8e115c1102e952954736785b6448a9a59`. No production appearance implementation
exists yet. Do not treat source compilation as runtime evidence.

Minimum tracer reviews `98b06800...49049d0c`: Standards zero documented breaches
or actionable heuristic smells; cleanup qualification for custom night mode.
Spec two gaps: initial default absent, explicit Dark indistinguishable from
System. Correction reviews `49049d0c...192976f8`: Standards zero; Spec zero.
These reviews do not establish whole visual-beta acceptance.

## Presentation reference and remaining delivery

Inspected pinned web tweet CSS and compact identity header: avatar/content
columns, compact name and timestamp, readable body, separators. Reviewed native
Android 17 original owner-feed and cached private-message/reaction screenshots
from ticket24 evidence. The header/actions consume most of the feed; status-bar
insets/contrast and default purple palette need repair. Preserve existing wallet,
backend, cache, draft, import/export, signed-operation and manual-recovery rules.

After actual appearance RED, implement the minimum real theme/override flow,
then compact navigation, feed and Chat presentation. Verify changed native UI on
Android 10 and 17, review original captures and separate specification/standards
reports, run final full acceptance, and publish a signed in-place beta review
with explicit unfinished-feature notes. No store submission is part of #37.

Actual Android10 RED completed: one failure/12.9s at initial System-dark
observation line61. Original failure PNG still renders light; root inspected
it and parsed its XML. Fresh synthetic reset reports Success; instrumentation
exit0 still contains FAILURES, and the harness correctly rejects it. Later
override/recreation/continuity assertions were unexecuted. Raw source/run/exit,
PNG/XML and checksums retained under appearance-red/android10.

Minimum production appearance source3a2ed238 adds System/Light/Dark and an
independent device preference, IO save/load, truthful save error and window
insets/bar contrast. Compilation, bounded reviews and native GREEN pending.

Minimum source review `67bc6922...3a2ed238`: Standards zero documented breaches
or actionable smells. Spec one accessibility finding: theme labels need selected
radio-row/group semantics rather than decorative radios next to ordinary
TextButtons. The bounded correction follows Android's documented selectable-row
pattern: https://developer.android.com/develop/ui/compose/components/radio-button .
Correction and selected-state native assertions are pending while the owned
Gradle build completes. This review remains separate from whole-beta acceptance.

Appearance attempt1 at340c0430: Android10 run38492622482 fails initial dark
observation/13.478s, original still light; Android17 run38492625112 rejects its
unsupported original mode before mutation/0.201s. Raw logs/exits retained in
appearance-attempt1; root parsed10 XML and viewed its original PNG. Neither is
GREEN. The original RED's system-mode application was not recorded, so it does
not yet prove a valid first-install dark environment.

Primary AOSP android10-release UiModeManagerService.setNightMode refuses changes
before user_setup_complete. The current source shell handler also supports
custom_schedule/custom_bedtime. Bounded fixture correction completes setup on
only the guarded emulator, records the original setup flag, verifies exact
shell-result mode plus applied Configuration before rendering, preserves and
restores the original supported shell mode including custom schedules, and
captures non-secret system-control diagnostics. This remains a setup hypothesis
until runtime confirms it. Acceptance surface/override/identity predicates and
original timeouts remain unchanged. A corrected baseline RED and current-product
GREEN are required before accepting this slice.

Primary source inspected:
https://android.googlesource.com/platform/frameworks/base/+/android10-release/services/core/java/com/android/server/UiModeManagerService.java
https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/UiModeManagerService.java

Corrected-environment attempts atb9a91085:
- Android17 run38493376388 passes1/37.586s. Original shell custom_bedtime is
  recorded and restored exactly. Requests yes/no apply configuration32/16;
  full wallet/backend/no-publication and accessible selection assertions pass.
  Root parsed8XMLs and viewed default-dark, Light, explicit-Dark-under-Android-
  Light and System-Light original PNGs. Window contrast/insets are improved.
  Retained under appearance-green/android17. This source precedes feed/Chat polish.
- Android10 run38493373431 fails before app launch/0.433s: requested yes returns
  no. Original setup flag was1, so the setup explanation is not established.
- Corrected baseline branch proof/appearance-baseline source dd9bfc4d run38493367649
  also fails before app launch; this is not a valid baseline RED. Raw/exits/control
  logs retained in appearance-attempt2 and appearance-baseline-attempt1.

Read-only support inspected Android10 AOSP defaults and Shell manifest. The
system locks night-mode changes and its Shell lacks MODIFY_DAY_NIGHT_MODE;
setNightMode silently returns, while the shell prints the old mode with exit0.
Current AOSP Shell declares the permission. This supports a permission hypothesis
for the10 refusal, pending actual emulator lock/identity/system-log diagnostics.
No production app workaround is justified by this fixture failure.

Presentation source7bd532bc compacts the existing header/backup controls,
places four existing destinations at the bottom, uses accessible selected feed
tabs and Saito-generated avatars, groups social controls, adds coherent type and
native vector/launcher resources. Existing callbacks, full public-key semantics,
wallet/import safeguards and signed status remain intact. Local debug/testAPK
build passes3m33s. Both bounded review axes0; screenshots/current flows pending.
Chat sourcec80239d3 adds contact avatars and incoming/outgoing bubbles with
verified timestamps, retaining text, reply/copy/reaction/draft/history callbacks.
Both bounded review axes0; local build48055 active and runtime pending.
Diagnostics/layout10run38494088756 and17run38494091375 at4f691121 remain pending.

Diagnostic10run38494088756 at4f691121 confirms the rejection path: shell UID2000,
mNightModeLocked=true and UiModeManager logs "requires MODIFY_DAY_NIGHT_MODE
permission". Original setup=1; repeated setup writes do not fix the failure.
System-only source/exit/lock/identity/log evidence retained in appearance-diagnostic.
Android29-only fixture now uses the guarded AOSP emulator's su0 for night-mode
commands, recording that identity and verifying command result plus applied
Configuration. The app retains its ordinary UID.17 keeps its supported shell
command. Unnecessary setup writes removed; no rendering/identity assertions or
timeouts weakened. Current app explicitly disables automatic Force Dark so its
real palette and explicit Light/Dark choices control rendering on Android10.
Chat debug/testAPK compilation passed3m25s; whole-beta runtime still pending.

Corrected baseline RED: Android10 run38494667516 at8dbb855a9ecfa32057840c11326827a1ec8f77ab
uses the same corrected tracer as current9f, with pre-appearance production
app sources. Root-mode command reports yes and applied Configuration32 before
initial rendering fails/13.678s. Original light PNG inspected and XML parsed;
restore reports no. Retained under appearance-valid-red/android10. Unlike earlier
attempts this establishes the intended actual dark environment.

Current appearance source9f2ff715c79faaaa29b25747a7ad008800885e64:
Android10 run38494673941 PASS1/11.546s;17run38494677156 PASS1/36.954s. Each retains
8 original PNG/XML pairs and system-controls, raw exit/summaries. Root parsed all
16XMLs and inspected current10 Light/current17 explicitDark-under-AndroidLight,
plus valid baseline original. These prove the recorded theme/selection/full-wallet/
backend/no-publication flow. They do not prove current final4c Chat layouts or
physical Pixel performance. Separate feed-appearance17run38494091375 also passed
at4f691121; its Light original shows compact header/reminder/navigation/avatar
presentation. All sources/limits remain explicit.

Whole source review ca2ade6a...8f7ae687: Standards0, Spec0. Last visual refinements
8f7...a533 keep existing server controls prominent, clarify selected-feed underline
and fix English singular counts without changing Long values or status/callbacks.
Spec0; Standards0 documented breaches, one possible duplicated-tab-shape heuristic.
Correction a533...4c8f14ca consolidates two real callers in private RowScope.FeedTab:
both axes0, duplication resolved. Local a533 debug/testAPK PASS3m3s.

Final full10run38495652348 and17run38495655076 are dispatched at
4c8f14ca34c2726881e074dab6de73cbce6f5e77, with separately enabled public feed
reads. Both full gates and current Chat original-capture inspection are pending.
Signed review build42136 is preparing the next reserved version; it is not
published or accepted yet. Do not close37 until full verification, final evidence
reviews and public in-place publication complete. Parent35 remains open.

First final full attempts4c both failed and are retained in full-attempt1:
17run38495655076 stops at VideoPreviewTest280, BEFORE background/pause. Three
video cases pass; instantaneous hasObject Playing check fails. Original PNG/XML
show Playing controlled video visible after failure and one provider request.
A timing explanation remains a hypothesis, not a demonstrated product cause.
10run38495652348 stops at UsernamePreviewTest564 after14of15 cases pass: the
unscoped exact public-key selector matches two nodes. Root original wallet modal
PNG/XML confirms the expected full key in Wallet address. No wallet mismatch.
Earlier successful class summaries, raw failed logs/exits, source/run metadata
and relevant public originals retained. Later gates were unexecuted.

e95e127f changes only fixtures: bounded wait for the same Playing DOM predicate
within existing10s deadline, and exact-key+Wallet address scope. All original
state/identity/no-publication assertions remain. Explicit app/core diff against4c
is empty; prepared signed14 remains product-equivalent and unpublished.
Corrected full10run38497736473 and17run38497738892 at
e95e127f527ae031425813044f01f9fa5223f1b6, public_feed_read=true, are active.
Do not close37 or publish before final results/current captures/evidence reviews.
