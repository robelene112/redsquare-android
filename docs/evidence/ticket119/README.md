# Incoming Fileshare acceptance ownership, #119

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `f973bd61e3d6a6782e097adcfd70a4152a400556`.
Product fix: `cfac7287a6beb297709787d31965e214e27f1d0b`.
Final native source: `6b231a4e106fa0d471b919fe228db040f5c6e4d2`.
The final source adds stronger native assertions and pending notification Cancel;
its production source equals the product fix.

An accepted incoming file reserves foreground ownership before its queued actor
can advance past earlier signalling. The service acknowledges the exact token
only after Android foreground activation. No receive grant precedes that readiness.
Cancellation/interruption release the reservation and prevent a late queued grant.
The original two-second idle window, outgoing preparation and protocol remain.

## Reproduction and intermediate observations

Android17 red `43740334605` at `01537b4f018a5325466f689d41dbe9b2f1cfa3ea`
reproduces the disappearing foreground notification while the prior real Relay
reply remains held. The added test fails after 63 screen-off samples; the original
receive test passes. This is an actual native red, not a source-only inference.

Intermediate focused Android17 `43741901160` at product cfac passes the original
receive case and delayed acceptance/completion. It predates the stronger foreground
classification assertions and pending Cancel case, so is not final acceptance.

Intermediate Android10 `43741903757` at cfac and final-source Android10
`43743688976` fail runtime provisioning before any
app test. All three permitted cold boots expose a measured 16MiB heap despite the
declared 512MiB heap. The cause is unproved. Its complete setup outputs are retained;
no application pass/failure or successful-prefix acceptance is inferred from it.
The heap check, boot bound and application deadlines remain unchanged.

## Current-source verification

Complete Android10 `43744801973` and Android17 `43743686504` succeed at exact final
source6b. Each completes all nine ordinary classes/eleven methods and two normal
process methods alongside one separately classified intentional process-death start,
with distinct PIDs. The10 run uses the existing supported ubuntu22 pool and passes
the unchanged heap preflight on its first boot. Both delayed paths record61/63
foreground samples respectively, all passing. Receipt/export checks
verify150007 exact bytes (SHA256 `38ea1455fbe8e50f07f360c80d0b815159ed98daa419b9ee5def259398a90fc1`).
Pending notification Cancel confirms the original token is durably CANCELLED with
zero grants/chunks/bytes, released ownership, stopped service and retained draft.

Both independent SDK evidence reviews pass: fresh GitHub metadata/digest,
all113/102 raw files and111/107 captures, complete hash indexes, full original log and
every assessment assertion. Original180second class and120second process bounds
remain unchanged. Source review reports zero findings on both axes.

Source/debug `43745406490` passes compilation, core/gateway tasks and lint at exact6b.
Its reports record224 core tests/44 suites, zero failures/ignored, lint zero errors,
94warnings and3hints. Gradle can reuse unchanged outputs; no uncached execution claim.
The first source/debug `43743691425` was replaced after an unusually long build on
the default runner. Its cause is unproved, all54 original files remain retained,
and no prefix supplies acceptance. Both independent final source/evidence reviews
accept #119 with zero remaining findings.

The new native journeys hold the existing authenticated peer-description response,
accept the offered file, then Home/screen-off for 3.2seconds. Every sample requires
the actual notification foreground flag and running-service foreground classification.
The completion path retains original byte-exact receipt/export and background goals.
The cancellation path executes the actual exact-transfer notification PendingIntent
before the held reply is released, requiring durable CANCELLED, zero grants/chunks/
bytes, stopped service and retained unsent Chat. Original cases remain additive.

## Boundaries

This is focused Fileshare qualification at the existing owned peer/Android seams,
not whole-app acceptance or beta publication. Final integrated delivery belongs to
#131. Public beta remains0.1.131. Physical/OEM, live network/provider and Play gates
remain open. No production node/provider change or public financial write occurred.
Whole original ZIPs/logs/captures and temporary collection/assessment tools remain
outside Git under `/mnt/storage/redsquare-android-artifacts/fileshare-accept119`.
