# Ticket 111 independent reviews

Review base: `c6157dd47a469923b3c7e9a1511cb27742c87f18`.
Final reviewed implementation/test source: `696a1a4759ccad3fa0e30248abfd990bb5d6c1f5`.
Two independent, read-only agents reviewed the full implementation and subsequent
corrections. These static reviews do not establish native or live acceptance.

## Spec

No remaining findings. The review identified legitimate partial-sale inventory
that ordinary seller-envelope hydration rejected. The shared lineage reader now
authenticates the original seller offer, purchase and Store-signed fulfillment,
persists their provenance and uses the actual remaining custody output. It binds
multi-lot remainders to the final consumed lot, preserving Store-signed display
metadata and comparing SAITO prices by exact nolan value. The official WASM
reference includes a two-lot purchase with equivalent `2.50` / `2.5` prices.
The native management case exercises retained remainder delisting and restoration.

## Standards

No remaining hard findings. Local financial-footer transitions now use the
existing reduced-motion-aware reveal. Explicit typed journal validators remain;
the observed duplication is a judgment heuristic, and the reviewer accepted
retaining protocol-specific validation over introducing a generic financial
journal solely to eliminate repetition.

Both axes reviewed the final test-only delta: waits observe the relevant pending
marketplace status within the unchanged 15-second deadline, Refresh scrolls into
view, and any failed class still makes the complete scope exit nonzero. Continuing
independent classes preserves diagnostics without accepting successful tails.

Spec: 0 remaining findings. Standards: 0 hard findings; the documented journal
heuristic does not require a refactor. Native completion remains a separate gate.

The final management-test delta also passes both axes. It waits at most 15 seconds
for the actual inventory row before scrolling to Delist. Both original native
failure pictures show the cloud read still loading. Review waits, all financial
assertions and the 180-second class bound remain unchanged. The production trees
are identical across this test-only correction.

Both reviewers also accepted archive normalization: bounded web routing and
assigned output block/ordinal fields are removed when reconstructing the original
signed purchase, while signed amounts, types, indices, input positions and message
remain unchanged. The public-interface regression covers routed listings and
routed, block-assigned purchases through restored remaining inventory.

The actual Android 10 run found `BigInteger.intValueExact` unavailable in the new
spend reader. Both axes accepted its replacement with explicit `0..255` bounds
and ordinary conversion. The final complete core suite passes 201 cases. Complete native seller and catalogue scopes now pass on that final source on both SDKs.
