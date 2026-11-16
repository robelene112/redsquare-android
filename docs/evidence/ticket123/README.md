# Talk account ownership across Activity replacement, #123

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `8df19feb8dd0adda0d95c337c548ad1fd970dd22`.
Final native source: `cb575f1731b8ffe884c603c5a06ba9d950259c73`.
Source/lint source: `1a3643ccf5d53e0f7efc910a4b7bec78e2767da3`.

Acquiring a Talk account now reserves ownership before returning it. Reservation
and zero-owner retirement share a lock, so a queued old detach cannot close an
account already acquired by the replacement. An acquisition that finds a closed
account creates a new one. Registry removal remains conditional on the exact old
instance, and wallet/backend/exact-endpoint keys are unchanged.

The Activity stores each acquisition in a small Compose RememberObserver owner.
Committed ownership attaches; forgotten or abandoned composition releases the
same reservation. Visibility handling remains tied to the Activity lifecycle.
An active user-started call retains its existing independent background ownership.
No media/signalling protocol, permission gate or deadline changes.

## Relevant native acceptance

| SDK | Complete successful parent | Normal methods | ZIP entries | TAR members |
| --- | --- | ---: | ---: | ---: |
| Android 10 | [43780001228](https://github.com/robelene112/redsquare-android/actions/runs/43780001228) | 5 | 93 | 16 |
| Android 17 | [43780003645](https://github.com/robelene112/redsquare-android/actions/runs/43780003645) | 5 | 82 | 17 |

Both complete parents run the exact final native source. Four classes complete
five normal methods on each SDK, with no assumptions/skips and zero capture exits:

- The explicit debug/emulator actor barrier queues old detach, acquires/attaches
  replacement ownership, then releases old detach. The second barrier completes,
  a real authenticated signed incoming invitation reaches the replacement, and
  it starts a controlled call with a real foreground notification. A real
  MainActivity recreation retains the same wallet/backend/call, and its active
  call remains available in the background.
- Unchanged ownership tests preserve stale/current notification action isolation,
  synchronous wallet/node/blocking capture shutdown and obsolete-control rejection.
- The unchanged audio-focus test interrupts the exact call and releases native
  audio mode and foreground notification ownership.
- The unchanged camera lifecycle test exercises real capture, switching, Home,
  screen-off, exact notification return and resumed frames. Both emulators expose
  the existing supported camera-switch path; final receipts observe 92/37 frames.
  Android17 additionally retains its supported foreground-service-type receipt;
  Android10 does not execute that newer-API-only check. This is not a skipped
  native method or a new relaxation.

Actor trace timestamps can coincide within a millisecond. The barriers and queued
operations establish ordering, not a requirement for different clock values.
The owned fixture declares incoming/outgoing room IDs before setup and retires
only matching wallet/backend/rooms, including setup failure before local token
capture. Activities close before independent identity/files/preferences restoration.

External originals are retained at
`/mnt/storage/redsquare-android-artifacts/talk-account123`:
run/artifact API metadata, complete original logs, server-digest-verified ZIPs,
every extracted original ZIP/TAR member, and collectors/assessor. The actual recreated-active-call images show Talk controls after recreation.
The replacement-call images show the launcher after Activity disposal; they
represent background context, not an onscreen call. Native notification/token
assertions and the stage receipt establish the retained background call.
The packet copies 22 files for Android10 and 23 for Android17, all byte-exact: four native
logs, verification/assessment receipts and every capture member.

## Source checks and explicit source qualification

[43779519342](https://github.com/robelene112/redsquare-android/actions/runs/43779519342)
succeeds at source `1a3643ccf5d53e0f7efc910a4b7bec78e2767da3`. Its 55 original ZIP
entries are independently verified byte-exact. Actual debug/test Kotlin
compilation and assembly succeed; separate lintDebug succeeds with zero errors,
94 warnings and three hints. Core reports 224 tests across 44 classes, zero
failures/ignored, restored FROM-CACHE. The gateway test task also completes
FROM-CACHE. These focused checks establish no release/performance assembly.

The only difference from this source to final native `cb575f173` is the reviewed
single AndroidTest cleanup correction and equivalent extraction of its outgoing
room into a local variable. Product/core/gateway/resources/build/workflow/harness
are byte-identical. Assertions, native goals, debug guards and all deadlines are
unchanged. Final native parents compile and execute that corrected fixture. Both
source review axes accept this explicit test-only bridge; lint is attributed to
its actual source, never described as exact-final-source lint.

Both independent source and native evidence review axes report zero findings.
Both axes also accept every copied packet byte and this README. Beta delivery
is not implied here.
Whole-app qualification and the combined signed beta remain #131.

## Original adverse result and replaced runs

Red [43778610039](https://github.com/robelene112/redsquare-android/actions/runs/43778610039),
source `1d9cecbba4025e774bba9232c4dcc25bf8806615`, times out at the five-second second
actor barrier after all three queued/acquire/release trace events. Old disposal
retires the actor before replacement attachment can run. This failed parent does
not qualify its unexecuted incoming/start or prior lifecycle checks.

Initial run43778509487 at source4fca was replaced/cancelled during test preflight
correction. The1a native parents43779514126/43779516598 were replaced/cancelled when the exact
fixture-room cleanup was strengthened. All retain original artifacts and status;
none count toward acceptance. There is no passed-prefix acceptance.

All call/signalling traffic uses owned loopback peers. These emulator checks do
not establish physical/OEM media quality, live selected-node Relay/Stun,
TURN/NAT, provider/funding or killed-app incoming delivery. Existing release
qualifications remain open. Public beta remains 0.1.131 until final #131 delivery.
