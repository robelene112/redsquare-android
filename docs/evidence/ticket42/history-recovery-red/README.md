# Unknown retained ancestry before durable recovery

Exact4cadda5c owned build FAIL1m26s, gateway10 tests/1 failure/coreUPTODATE.
First OWNER journey completes prior six raw requests/reorg/restart, then the
seventh unknown-ancestor response leaves the old cursor object. It fails at
GatewayChainTest198 expected no active cursor. Later empty-state restart, SPV
recovery, following Full request and OFFICIAL are unexecuted. Ten manifested
files retain build/eight JUnit/23sourcehashes; raw report supplies exact time.

Both reviews found a separate tracer mismatch: assertNull also rejects valid
explicit JSON null. The minimum will accept absent-or-JsonNull at both new sites.
This actual failure held an old object, not explicit null; no strengthened-RED
outcome is claimed. No unavailable old-history reconstruction or whole42 acceptance.
