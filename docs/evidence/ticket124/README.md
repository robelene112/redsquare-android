# Idle Talk signalling recovery, #124

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `b0e628ab78f3e9e308424e00f130c79824d362ee`.
Android 10/source/lint: `a84517c57d94f90fb765e42af5fe1b9905fcb944`.
Corrected Android 17: `42989c5ad5ba9c7a05335ba2138d666391dcae13`.

Visible idle Talk now retries lost routes under its existing account ownership.
Each route owns its socket, registrations, monitor and authenticated Relay-ready
state. Retirement removes that exact route before closing its resources; obsolete
monitor callbacks cannot remove a replacement. Availability requires at least one
healthy authenticated Relay route, rather than merely a pending socket.

The existing one-second actor tick checks due retries. Failed attempts wait
1/2/4/8/16 seconds, then a delay capped at 30 seconds, sampled by that tick. Healthy
idle routes perform no additional wallet/policy IO on each tick. Background idle
sessions do not acquire foreground or wake ownership. Existing active-call
interruption and user-started foreground ownership remain intact.

Talk displays actual connecting/available/unavailable state near its heading.
Unavailable state has an actual Retry control that bypasses the retry delay
without duplicating in-flight connections. Invitation input and review controls
also use the shared small spacing token. The incoming-delivery limitation remains
explicit: active calls can continue in the background; killed-app incoming is not
claimed. Existing 12-second handshake/service budgets, call budgets and native
class deadlines are unchanged.

## Complete relevant native acceptance

| SDK | Successful whole parent | Normal methods | ZIP entries | TAR members |
| --- | --- | ---: | ---: | ---: |
| Android 10 | [43785835845](https://github.com/robelene112/redsquare-android/actions/runs/43785835845) | 11 | 105 | 42 |
| Android 17 | [43787674128](https://github.com/robelene112/redsquare-android/actions/runs/43787674128) | 11 | 94 | 43 |

Each complete parent passes seven groups with 11 normal method completions, no
assumptions/skips and zero native/capture exits:

- Automatic idle recovery after all routes are dropped, without navigation,
  lifecycle restart, Activity recreation or Retry, followed by a fresh signed
  authenticated incoming invitation. Recovery after test-node restoration takes
 343/1,339ms in the two retained observations.
- Launch during outage, actual visible unavailable state and enabled Retry, then
  recovery through the actual control and fresh incoming delivery. The retained
  Retry recovery observations are 402/790 ms.
- One failing route and one healthy route, with truthful available UI and an
  invitation delivered through the healthy fallback.
- A started call with real foreground ownership loses all signalling routes,
  transitions to the exact token's INTERRUPTED state and removes its foreground
  notification. Network restoration reconnects for incoming calls without
  restarting that interrupted call. Teardown verifies no owned registrations or
  authenticated connections remain.
- Unchanged account-replacement ordering, routing/ACK ownership, notification and
  wallet/node/blocking capture isolation, native audio-focus interruption, actual
  camera switching, Home/screen-off and resumed-frame assertions.

The controlled dual-route override is restricted to the debug development package,
emulator hardware and loopback endpoints. It lets the real production fallback
implementation run against two owned servers. Release clients cannot use the
extra; normal production route selection is unchanged. Every test's application
traffic stays on owned peers.

## Source qualification and original failures

Source/lint [43786132998](https://github.com/robelene112/redsquare-android/actions/runs/43786132998)
succeeds at `a84517c57`, with 55 original ZIP entries verified byte-exact. Actual
debug/test assembly and separate lintDebug succeed: zero errors, 95 warnings and
three hints. Core reports 224 tests across 44 classes with zero failures/ignored;
core/gateway test tasks are explicitly FROM-CACHE. Focused checks establish no
release/performance assembly or whole-app acceptance.

The only difference to corrected Android 17 source 42989c5 is four lines in
TalkAccountReplacementTest: the established five-second server-authentication
precondition before injecting an invitation, plus recipient/ACK diagnostic counts.
The existing routing fixture already uses that boundary because client readiness
can precede server consumption of the last handshake frame. Product/core/build/
resources/workflow/harness and all original actor/incoming/UI deadlines and
assertions are byte-identical. The corrected parent compiles and runs this stronger
fixture; its receipt records one authenticated recipient and one acknowledgement.
Both independent review axes accept this explicit one-test source bridge.

The first Android 17 parent 43786130608 at a845 remains failed and excluded. All
four new recovery cases pass there, then the inherited replacement test fails its
five-second incoming await after the actor barriers pass. The exact cause is not
established by that run's evidence. Corrected success does not prove the old cause,
and successful prefixes are not combined into a passed parent.

Earlier owned observations remain separately retained:

- Idle red 43781701767/source c62c6de94 fails automatic recovery's 20-second wait
  after actual loss and restoration. Automatic green 43782476595/source 59fb2f2ce
  succeeds for that one tracer.
- UI red 43783068074/source f610c666e fails the actual initial-outage feedback wait;
  automatic recovery still passes. Retry green 43783933046/source b5f7677e9 completes
  both methods after truthful status and Retry are implemented.
- Routes baseline 43785018334/source e8e2d223a fails because the owned fallback
  override is not yet supported. It does not establish an accepted product
  regression or qualify unexecuted active-call assertions.
- The first final dispatch returned an HTTP 504, but GitHub did create the
  successful Android 10 parent above. Its identity was verified before the other
  scopes were dispatched; no duplicate success or cancelled replacement is inferred.

## Capture and scope limits

Initial-outage images substantiate unavailable state and Retry; incoming images
substantiate restored incoming UI. Some immediate transition captures retain
preceding rendered content: Android 17 automatic/all-routes-lost still shows Ready;
both active-interruption captures still show Connecting call/End call. Those images
do not establish rendered unavailable/interrupted endpoints. Exact native state,
notification-removal assertions, subsequent visible readiness and incoming checks
establish the stated functional results. Account replacement's recreated-active-call
images show Talk controls; replacement-call images intentionally show the launcher
after Activity disposal and represent background context.

Both independent source/native review axes accept the relevant evidence with these
qualifications. A low-priority possible fixture-duplication smell is explicitly
deferred to keep the correction coherent. Both axes also accept all copied packet
bytes and this README.
External originals, API metadata, complete logs, server-digest-verified ZIPs,
every extracted ZIP/TAR byte and collectors/assessor remain at
`/mnt/storage/redsquare-android-artifacts/talk-idle124`.
The packet copies 51 Android 10 files, 52 Android 17 files and the source/lint receipt.

These owned-emulator results do not establish physical/OEM media behavior, live
selected-node Relay/Stun, TURN/NAT, funding/provider or killed-app incoming delivery.
Existing gates remain open. Public beta remains 0.1.131 until #131 completes
whole-app qualification and the combined signed release.
