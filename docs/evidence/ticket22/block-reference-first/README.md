# First full-block compatibility tracer

Independent synthetic reference executes0.38s with pure pinned WASM0.3.2 and
Elliptic6.6.1. Published unsafe scalar3 creates/verifies the209-byte creator
preimage; no Saito initialization, wallet/private node state, live requests or
real funds. The existing independently proven621-byte payment is embedded in
a1010-byte full block: height2000002,time1700000010000,parentab32,creatorunsafe3,
singleleaf5844cda6... andhashb33d2fc4... . Exact signed metadata/input coordinates
are retained; outputs acquire blockheight2000002/logicalordinal0/indices0and1.
Expected post-generation bytes are independently constructed and compared to
pinned WASM serialization; deserialize/reserialize and all-relevant lite agree.
This single all-relevant fixture does not prove mixed-SPV compression behavior.
The reproducible generator is build-support/references/block-reference.cjs;
literal fixture is core/src/test/resources/native-block-reference.json.

First compiled native test observes an unimplemented-reader RED49s at
SaitoFullBlockTest.kt:16, not a compile/setup failure. Minimal reader/matcher
passes the same case GREEN49s (0.45s actual case). Full core suite then passes
58tests/0failures/errors/skips in18s.8hashes retain original logs/XML/reference
and summed core counts. The single-block XML is retained separately for that
broader run; core counts cover all generated XML, not only the new case.

The reader bounds complete framing and resource use, validates creator signature
and header/hash, reconstructs the root from every non-SPV leaf, and rejects
unsupported SPV full representations. Matching requires the original Normal
signature/timestamp/message/complete inputs/amounts/fee, correct enclosing output
coordinates before normalization, and strict authenticated payment restoration.
Routing changes are allowed; no routing validation is claimed here. Content
verification is not consensus/system-leaf eligibility or current-chain proof.

This first case is insufficient for hostile framing, odd/system leaves,
unsigned-coordinate mutation, large integers, resource bounds, ancestry/reorg
or Android inclusion UI. Those remain separate cycles. Source/evidence review
is pending; no native chain-status or whole-ticket acceptance follows.
