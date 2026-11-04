# Ticket87 shared native module shell

Status: ticket87 complete, reviewed and verified. No new beta has been published;
final integrated publication remains94 work. The owner now requests one issue
per request, so stop after87.
Review base: `d5a05d4d8e76afcbe6cfb1536cc29e5087000b8e`.
Last production change: `bb97e62e64b0b853aa18ef96e57df7c06cdf2997`.
Current native motion test source: `565b77413`.

## Delivered behavior

The functional application uses the rounded module-name selector, vector
chevron, account avatar and real Wallet/Settings destinations. Shared native
colours, typography and spacing follow the reviewed85 reference. Selected-node
information opens a real accessible connection view; unavailable block data
stays explicitly unavailable, pending82.

Feed and Stack article reading collapse/reveal shared navigation while keeping
module navigation available. Explicit keyboard, D-pad and accessibility reveal
preserve the reading anchor. Header compensation is applied with the next list
measurement, preventing an intermediate reading jump. Module changes dispose
the previous feature immediately and fade only the new owner. Zero system
motion never renders an intentionally blank opacity frame.

Wallet/backend-scoped saved module routes preserve feed position and real
unsent profile work, including recreation and the actual IME. Stack discovery,
article and three local navigation routes explicitly identify their sample-only
boundary. This shell sends no Stack actions. Real Stack integration remains
first-release work under1.

A current-owner/current-node incoming notification can open Red Square from
Stack after an obstructing information modal closes. The existing Feed resolver
still owns verification and consumption. Wrong-wallet and wrong-node incoming
intents retain Stack; saved text/photo and scope remain intact.

Pre-S Android has a native popup surface-inset/window-bounds defect that clips
accessible48dp menu targets. Its account/module menus use a bounded native
PopupWindow without external shadow surface insets, retaining Material content,
independent lifecycle and saved-state owners, native Back/outside/Escape,
keyboard navigation and short interruptible native transitions. Android31+
retains the ordinary Material3 DropdownMenu. Exit content stays until native
detach; stale callbacks cannot activate a closed menu.

## Verified native checks

| Boundary | Android10 | Android17 | Source and limits |
| --- | --- | --- | --- |
| Header reading/module motion and additive native menu keyboard motion, normal/zero system settings | [41834874589](https://github.com/robelene112/redsquare-android/actions/runs/41834874589) | [41834880384](https://github.com/robelene112/redsquare-android/actions/runs/41834880384) |565b77413,2 mandatory classes per OS, each180s bound. Header137.757s/140.763s; menu33.605s/37.235s. |
| Complete frame,8-cell130/200% light/dark matrix, actual native48dp/disjoint targets, reading and normal/zero motion | [41759483964](https://github.com/robelene112/redsquare-android/actions/runs/41759483964) | [41759484311](https://github.com/robelene112/redsquare-android/actions/runs/41759484311) |050a2a5e6,7 tests per OS. Later production changes only native input focus ownership. |
| Retained module routes, exact1px feed anchor, real unsent bio, IME and recreation | [41758508895](https://github.com/robelene112/redsquare-android/actions/runs/41758508895) | [41759484803](https://github.com/robelene112/redsquare-android/actions/runs/41759484803) |8a/050,2 tests per OS. |
| Actual system reply from Stack, obstructing modal, wrong scopes, original post/photo draft | [41759484886](https://github.com/robelene112/redsquare-android/actions/runs/41759484886) | [41760230496](https://github.com/robelene112/redsquare-android/actions/runs/41760230496) |050/bb,1 journey per OS.17bb tolerates a transient lack of Compose roots during task transition, then requires positive/final Stack within the original2s scope observation. |
| Actual six-class TalkBack | [41755091590](https://github.com/robelene112/redsquare-android/actions/runs/41755091590) | [41759485294](https://github.com/robelene112/redsquare-android/actions/runs/41759485294) |3fa/050; actual engine, completed audio synthesis, focus, recovery and six native classes. |
| Viewer initial modal focus followed by exact-control focus/completed speech | [41760815692](https://github.com/robelene112/redsquare-android/actions/runs/41760815692) | Above six-class pass | f1abfa6d3; same10s modal readiness now observes service-owned initial focus and speech before moving to controls. |
| Complete current-control UI regression | [41761100054](https://github.com/robelene112/redsquare-android/actions/runs/41761100054) | [41761102793](https://github.com/robelene112/redsquare-android/actions/runs/41761102793) | dbdc6804e,44 tests in19 normal class invocations plus cold profile recovery after two actual process deaths per OS. |

The UI regression covers Appearance, Navigation, retained reading, Browse,
post presentation/viewport/content/contexts/pending/status, viewer/viewport/
motion, status recovery/motion and all four profile classes. The duplicate
profile-edit collector is not counted twice. Both retained host reports show
117 tests,0 failures and0 ignored tests. This is affected-regression evidence,
not94's final integrated full-app dispatch or physical Pixel performance.

## Review and observation corrections

Independent Spec and Standards reviews are clean through productionbb:
0 confirmed spec findings,0 documented standards violations and0 actionable
Fowler findings. A fresh paired incremental review ofbb...de8 also reports
0 findings per axis. A second incremental review ofde8...565 confirms all
assertions and deadlines survive the additive menu-class split. Spec reports0;
Standards reports0 documented breaches and one non-blocking duplication advisory
for short local await/recording helpers. Their existing local test pattern is
retained to keep this correction focused. Production code is unchanged.

Existing real-control migrations retain content, identity, backend, draft,
request, recovery,48dp and1px goals. Former header shortcuts now follow actual
Account -> Wallet/Settings. Offscreen Bio/history is reached by real scrolling
before original visible-content assertions. The original per-class and
per-assertion deadlines remain. Temporary DEBUG-87-a11y probes are removed.

The native focus observer reads the actual input/accessibility focus, not speech
or geometry as a substitute. TalkBack can focus a clickable ancestor whose label
lives on a child, so its name is resolved only within that focused subtree.
The menu observer now refreshes native nodes before accepting input-focus flags;
vanished nodes remain unknown. Exact row movement and immediate Escape stay
required, including before any Tab has provided focus.

## Failure provenance

Earlier failures remain failed and are not counted as acceptance:

-41748247331: reading collapse missing before feature-owned chrome.
-41749786914: stale native snapshot; unknown now remains unknown within5s.
-41750049651/41750889166: initial keyboard reveal needed real focus-group entry.
-41751292451: real Bio offscreen; same10s editor reachability preserved.
-41751291671/41751291163: old reading control expectation needed actual navigation reveal before original backup/Home/1px checks.
-41753168287/41753168497 and41754079276: actual accessibility focus existed on an unlabelled clickable ancestor. Direct-label lookup was incomplete.
-41752924382/41754079312/41755092586: pre-S popup WindowRect was offset16px; actual48px Settings clipped to40px in UiDevice. Timing-only and post-attachment elevation attempts failed and were removed.
-41753168220/41755254679: real liked-author/post rows were below viewport. Original exact identity/Edit/Delete/Back/zero-write assertions remain after physical reachability.
-41757513568: first Escape was lost to the native popup decor.
-41758508338: actual clickable row carried keyboard focus, while its own text was on a child.
-41759484430: immediate Account Escape still lacked a touch-mode native key target; bb adds that owner.
-41759483824/41760230162/41760230484: directional test queried cached prior-row focus. f1 runs41760934453/41760937617 retain initial Red Square/expected Stack/observed Red Square while actual failure XML and highlight show Stack focused on both OS versions. de8 requires live refresh before the same assertion.
-41759484691: viewer test requested Zoom focus16ms before TalkBack's pending initial-window navigation replaced it. f1 observes real modal readiness within the same10s and passes41760815692.
-41759484789: recovered profile text passed exact Compose assertions but was below the native viewport. dbdc reaches the actual editable before the same visible-text capture; both complete replacements pass.
-41759485132: wrong-scope incoming intent briefly had no Compose roots during task transition; positive/final scope checks remain in bb and pass17.

-41833544519: menu focus refresh works and both scales reach the final article
recording, but native frame decoding exceeds the original180s class bound
(exit124). This is still a failure. The newly added keyboard/menu scenario now
has a separate mandatory180s class, while the original header class retains its
180s limit and all six original movies per scale. Both focused and full Chat
catalogs require both classes. Per-assertion bounds and every actual keyboard/
Escape/directional/nonactivation/window-scale goal remain unchanged.

Cancelled/superseded runs and build-only failures are not native app passes.
Original state assertions, controlled peer verification and fixture restoration
remain independent of successful presentation observations.

## Rendered evidence and remaining acceptance

Current565 native decoded frames were inspected on both OS versions for module
changes, native menu opening/closing, visible keyboard focus and rapid account
reopening. Normal transitions fade progressively; zero motion changes directly
without an intentional blank content phase. Actual original movies and complete
frame-timestamp manifests are retained for all14 recordings per OS, alongside
representative decoded frames and native captures. Menu inspection used an
explicitly cropped header contact sheet; full original movies remain intact.
The earlier06 chronological review separately verified the corrected keyboard
reading anchor and zero-motion article transition on both OS versions.

Both actual window contexts report the matching0/1 MotionDurationScale. The
recordings have irregular sample cadence, so they do not establish physical
Pixel performance, frame rate or audibility. Existing first-use200% density is
still visible in the baseline capture and belongs to78. Unknown block data is
82 work. No claim of app-wide polish acceptance is made here.

Text evidence normalizes line endings and trailing whitespace; original PNG and MP4 bytes are unchanged.
`native-results.json` retains native per-class results and movie SHA256 values.
Representative frame PNGs supplement the retained native MP4 and complete
manifest; uncommitted full decoded frame sets remain in the original Actions
artifacts. Their exact sampling order can be reproduced from those movies.

Keep parent55 owner acceptance, release36 actual owner/provider/device/store
items and real Stack integration under1 separate. Integrated beta publication
remains94 work. Stop after87 and wait for the next owner request before78.
