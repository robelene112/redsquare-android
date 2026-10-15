# First public peer projection attempt

2026-10-15T19:05:50Z, one read-only HTTPS GET to Owner /json/peers. HTTP200; collector incorrectly expected a top-level array. Retained only fixed schema-failure metadata, never response contents or unrelated peers. No registrar-presence, readiness or forwarding conclusion.

Root subsequently reread exact pinned server.ts825–840: actual response wrapper is {peers:array}. The separate corrected observation is retained alongside this failure. No production state or wallet operation changed.
