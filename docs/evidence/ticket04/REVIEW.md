# Ticket #5 review

Base7085bcf; first review candidate1b39936.

## Standards

One P2 documented-standard finding: the selection file may already contain
the new backend when directory sync or reopened verification fails. The UI
reports failure and retains the old active backend, but recreation can load
the new choice. This breaches preservation of specified behavior and recovery
verification. Zero actionable Fowler heuristic smells.

## Specification

One P2 finding: the same post-write uncertainty violates persisted selection
and observable failed-save recovery. Restore and verify the previous state or
block connections until an explicit recovery choice. The pre-write obstruction
case does not cover this failure. No scope creep identified. Imported-options
endpoint isolation belongs here; actual import activation is ticket #7.

## Resolution status

Post-write directory-sync injection and restart regression committed0966d9b;
functional red run37867404367 reproduced the expected missing recovery state.
A durable uncertainty barrier fix is implemented; runtime/fix reviews pending.
Final Android10/17 acceptance pending; do not close the ticket from an earlier
passing candidate. Physical Pixel7 acceptance remains ticket #35.
