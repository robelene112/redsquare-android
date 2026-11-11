# Fast-scroll responsiveness, issue101

Review base: `c8f1654a1`, accepted beta109. Delivery is pending.

A nondebuggable performance app drives three rounds of20 actual native swipes,
retaining Android frame statistics, Perfetto and native screenshots/window dumps.
Whole baseline43024558841 and composition-attribution43028401807 succeed.
The latter trace explicitly identifies main-thread `SocialActionOperations.busy()`
waiting34.398700ms on `SaitoLocalState.lock` while the background owner executes
`FileDescriptor.sync()`. Further waits cover name and avatar cache saves.
[Original trace query](busy-lock-contention.csv) retains exact observed names and times.
The large original traces, APKs and other diagnostics remain outside Git under
`/mnt/storage/redsquare-android-artifacts/scroll101`.

Only the rendering status read now reads the reactive SnapshotStateMap directly.
All actual operation acquisition, repeated-like queue mutations, identity changes
and durable journal writes retain their existing synchronization. No animation,
content, key, selected node, reading position or transaction format changes.
Diagnostic composition libraries opt in only for the isolated trace build;
ordinary release and default performance builds retain their original inputs.

The [actual old-source RED](lock-red/feed-scroll-io-ui.txt), run43032048915 on
`fc150cfecc904040706b8001f2b9ef7d492a03c6`, renders a new real signed post only
after8375ms, failing the predeclared3000ms goal and8000ms controlled store gate.
The test performs an actual owned PublicStringStore write and delays its enclosing
durable transaction; it does not claim that physical fsync itself lasts8000ms.
The owned worker stops and owned originally absent files restore even on failure.
The rendered-while-held assertion observes the actual native post before cleanup;
the final successful PNG is explicitly captured after cache restoration.

The pre-run101 contract retains every accepted100 goal, archive and original
deadline. Exactly one native identity/class/archive/normal completion per SDK
is added:651/652 identities,228classes,194archives,247/248normal completions,
37deliberate interruptions and36continuity records. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications remain.
The verifier changes only ticket paths and temporary prefix from100; inherited94
visual/motion/speech validators are unchanged. Full and signed-update gates remain pending.
[Independent source reviews](source-reviews.json) pass both axes with zero findings.

Diagnostic qualifications: public live content, network, JIT and runner scheduling
are uncontrolled; SwiftShader is a software emulator renderer. Compare the same
three-round workload but do not infer a controlled physical FPS improvement from
different live feed contents. Controlled native responsiveness regression provides
the causal before/after goal. No physical Pixel/OEM smoothness, store or provider
acceptance is claimed; parent1/36/55/84/86 remain open.

Original failed/cancelled diagnostics remain failed:43019853257 lacked the assumed
CI debug key (fixed with a disposable key);43021486726 failed trace path permission;
43023200562 failed classification of invalid OS completion timestamps;
43025397095 was cancelled before runtime after checking tracing handshake codes;
43025562386 failed native window startup observation;43026810992 required a footer
below the viewport of a long real post. Subsequent observer corrections preserve
actual public UI, capture failures and retain incomplete OS timestamps as unknown.
No failed execution contributes whole-run acceptance. A local optimizer experiment
did not complete its bounded build and was removed; no optimizer speed claim.
