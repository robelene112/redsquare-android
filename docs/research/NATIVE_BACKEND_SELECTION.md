# Native backend selection

Ticket #5, review base 7085bcf. Approved application and protocol boundaries
are the parent specification's observable Android flows with controlled peers.

First slice: choose either fixed server in Settings, preserve wallet identity,
isolate public caches, persist selection and keep the selected server during
an outage/recreation without contacting the other backend. Shared fixture
records the actual authenticated client identities; posts remain independent
WASM references. No production writes.

Run 37865535083 at 39cd843 compiled and passed the four existing feed tests,
then failed the new selector case because no Settings node exists. Downloaded
trace records the expected functional red. Evidence is under
docs/evidence/ticket04/red. Implementation now adds a fixed core backend enum,
a small atomic selection store and per-server public cache names, preserving
the existing owner cache. Malformed saved selection requires explicit choice
and does not silently introduce a server. Wallet options/peers never configure
selection. First controlled Android 10 checks pass; final acceptance remains pending.

Focused run 37866242352 at d08c5c7 passes all five controlled Android 10 cases
and cached cold launch; the new switching/identity/cache/outage test passes.
This is not full Android 17 or official live-read acceptance. The next slice
exercises a real selection-file obstruction: failed saving must retain the
current server and avoid contacting the new one, then succeed after recovery.
The fixture preserves and restores only the small public selection file, never
wallet state. Per-test public screenshots are retained on both success/failure.

Run 37866857744 at73f4579 passes six controlled Android 10 cases, including
failed selection saving and retry, plus cached cold launch. Public results and
screenshots are retained under docs/evidence/ticket04/save-green; screenshots
were inspected. The obstruction fails before mutation and preserves selection.
It does not prove recovery after a post-write storage error.

Both reviews of7085bcf...1b39936 found the same P2 issue: a successful atomic
write followed by failed directory sync/readback can leave the new disk choice
while the UI retains the old one. A new controlled post-write sync failure
and recreation case at0966d9b is awaiting the expected functional red in
run37867404367. The intended recovery blocks connections until an explicit
choice resolves an uncertain save; pre-write failures still retain the old
server. Do not mark #5 complete from earlier green evidence.

Full run37867221377 at1b39936 separately checks both approved public native
feeds, malformed selection recovery, Android 10/17 wallet/update regressions.
That candidate predates the review fix and cannot establish final acceptance.
Native imported-options/endpoint boundary proof was added at0966d9b; actual
wallet import activation remains #7. No production transaction was broadcast.

Post-write functional red37867404367 at0966d9b passes seven of eight controlled
Android10 cases and fails the new uncertainty case at the absent recovery
message after the forced commit-sync failure. The expected15-second timeout
is captured under docs/evidence/ticket04/uncertain-red. It is a behavior failure,
not a build or fixture setup failure. Malformed stored selection recovery passes.

The fix introduces a durable nonsecret backend.pending barrier before mutation.
Uncertain failure closes the active feed; startup refuses to choose from an
unfinished save. Explicit Settings selection writes/syncs/reopens the chosen
ID, then removes the barrier as its last fallible publication step. There is
no fallible work after removal. A removal that reappears after physical power
loss conservatively requests another explicit choice; this does not claim a
physical power-cut test. Pre-write obstruction still retains the previous feed.
Runtime verification and independent fix review remain pending.

Fix7578661 compiles app/instrumentation locally (57seconds); unchanged core
checks remain up-to-date. Standards/specification fix reviews each report zero
remaining findings. Full final candidate run37867977044 is pending. The prior
full run37867221377 passed Android10 including both public reads before being
canceled by the workflow's new-candidate concurrency rule during Android17.
It therefore does not establish complete two-SDK acceptance.

First fix full run37867977044 at7578661 passes all eight controlled Android10
cases, cached cold launch and both fresh public reads (three visible authors
each). It then fails the new NativeCompatibility imported-peer case because
its synthetic wallet omitted the format's required version field. Prior two
compatibility cases pass. This is an invalid test fixture, not evidence that
peer options changed connections. Added the independently pinned version5.677
already present in the accepted backup fixture. Android17/full acceptance was
not reached; final verification must rerun with the corrected fixture.
