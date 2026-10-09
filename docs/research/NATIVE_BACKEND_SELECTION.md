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
selection. Verification of the implementation is pending.
