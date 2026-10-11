# Second Android10 run rejects an unreliable metadata check

Full10 run38146223301 at0caebdac92af2e5104844034ba183b5ae97e4087 stops
before Search. Posting reports18cases/15PASS/3FAIL76.329s, all three wallet
replacement journeys fail the new direct File existence/size guard. The shared
chooser does not execute, so this run cannot establish whether its correction
resolves the first failure. Source/core/debug/test/release/lint PASS.

Root individually viewed original PNG and separately parsed XML. PNG shows
native wallet dialog; later XML shows DocumentsUI Recent, test-provider entries
and No items. These distinct window observations are not an atomic pair. They
show a transition, not a missing provider or a proven missing file.

The direct instrumentation-process File probe is not a reliable inspection of
the separate test provider's private app directory. Existing username/profile
fixtures inspect only stat size through fixed run-as of the .dev.test identity.
Correction ee70a83 reuses that maintained metadata-only pattern and retains
positive size, actual validated import preview and all acceptance safeguards.
No file contents are read or logged by the diagnostic. Exact first scheduling
cause remains unproved; no production defect or final acceptance inferred.

Raw posting log and both original observations retained unchanged. Android17
companion remains active.
