# Native username verification

Active #19, review base ed5742fd0f57958d284073b7145f1844b72b1dbd.
Source contract: NATIVE_USERNAME_CONTRACT.md. Parent #1 accepted seams1–4:
observable native optional registration against controlled peers, pinned protocol
vectors, persistence/interruption recovery and applicable A/B web interoperability.
No new testing seam or live registration is authorized or performed.

The first tracer resolves exact NativeCase7@saito and public unsafe scalar1
identity from an independently generated registrar statement. The test registrar
is public unsafe scalar2, explicitly supplied at the core interface; it is not
Saito's published registrar and never establishes actual namespace ownership.
The generator checks existing pinned WASM0.3.2/hash and Elliptic6.6.1 metadata,
signs the exact A/B Registry UTF8 preimage and independently verifies both paths.
Network-none/read-only/env-i execution never initializes a Saito application or
reads wallet options. Reference JSON contains only public synthetic information.

Missing reader produced compile RED23s, unresolved SaitoRegistry. Raw stdout is
retained in ignored scratch; compiler stderr appeared in the tool result but was
not included by the initial stdout-only redirection. Do not describe it as a
complete merged stderr log. Later build captures include both streams.
Minimal bounded strict reader validates exact identifier/owner, expected registrar,
block-id/hash proof and signature. An empty array alone means currently available;
unavailable/malformed responses throw. The server lc field is separate unsigned
chain-state metadata, not signed finality. Focused first-case GREEN45s, one test,
zero failures/skips. No Android implementation or native-screen proof yet.

Next one tracer requires exact independently serialized zero-payment registration
bytes, with Registry/register/identifier at top level and published registrar
first, wallet change second. Pure WasmTransaction serialization plus WASM/Elliptic
signature verification generate the reference; this is not a complete initialized
browser wallet or chain-inclusion proof. Native social output order cannot be
assumed to match Registry. Missing registration path compile RED pending.

Earlier read-only live research in the contract observes working Archive/key-map
reads but {err:"no response"} namechecks on both approved endpoints. Do not
interpret that as an available or owned name, claim a connectivity cause, enable
block production, change a node or broadcast a registration. Optional UI must
preserve normal feed/posting/Chat use throughout unresolved registration.

Registration tracer compile RED23s, missing registration path, followed by
focused GREEN47s, two cases. Existing social create retains exact self-first
behavior. The new internal directed constructor puts registrar before self and
shares the existing zero-payment serialization/signing implementation. Registry
registration preserves case, validates1–45 ASCII alphanumerics and adds @saito.
No spend, fee, on-chain confirmation or actual live ownership follows.

Next one tracer restores the independently serialized directed request and
requires its exact original bytes/envelope. Existing restore requires first output
equal input author; the registrar-first reference may expose this assumption.
Native RED pending; do not change restore until observing that test outcome.

Directed restore tracer RED28s: three cases, one IllegalArgumentException at
SaitoTransaction.restore:94, the exact first-output-equals-author requirement.
Merged stdout/stderr and failing XML retained in ignored scratch. Minimal remedy
requires an own output anywhere in the bounded output list instead of first.
The one zero-valued input, all zero-valued slips, normal transaction kind, bounds,
indices and verified signature remain required. No money-transfer support added.
All52 core cases plus debug/test assembly GREEN2m18s, zero failures/skips. Old
independent social/root/reply/hop vectors still pass unchanged. New Registry
request matches independent WASM bytes and its exact original restores. Native
username UI/persistence and final runtime/cross-client acceptance remain unproved.

Core checkpoint2f1ff8e passed both independent review axes with0 actionable
findings. First native tracer5c57cb0 compiles (debug/test assembly1m16s), but no
UI exists yet. Focused Android10 run38113351395 is dispatched for the expected
missing Settings action RED; push source checks38113351503 are separate. The
tracer requires a real authenticated Archive read, exact Registry namecheck,
visible unavailable message, unchanged public wallet and zero broadcasts. Its
PNG/XML failure capture is inside the Activity scope, preserves primary errors
and makes no atomic observation claim. Controlled Registry responses do not
increment Archive-only readiness counters; the new callback precedes the final
Archive callback to preserve old trailing lambdas. New focused10/17 scopes and
full inclusion have independent Standards/Spec reviews with0 actionable findings.

Focused Android10 run38113351395 actual native RED1case/1failure/2.608s at
UsernamePreviewTest.kt:48, missing Saito username action. Build/core/debug/test
assembly1m38s passed; push source38113351503 passed separately. Original failure
PNG individually viewed and XML inspected show Settings/Server/both choices/Done,
with no username action. Raw output/PNG/XML retained verbatim at
docs/evidence/ticket18/first-unavailable-red. Later flow assertions were not reached
and are not claimed as passed. Minimal optional screen now in development;
first local compile was deliberately interrupted after noticing the peer request
interface takes/returns UTF8 bytes, then corrected before restarting compilation.

Minimal screen6bd3cb1 debug/test build2m7s passed. Independent reviews for
5c57cb0...6bd3cb1:0 actionable Standards/0 actionable Spec. First native GREEN
Android10 run38113786054 one case PASS5.465s: real unavailable namecheck,
visible error, enabled New post after Back, same public wallet, zero broadcasts.
Original success PNG individually viewed, separate XML inspected, retained
verbatim with raw log/hashes at docs/evidence/ticket18/first-unavailable-green.
Posting is accessible; this case does not actually send a post or exercise Chat.

Next tracer00bbead requests a registration after a successful empty namecheck,
expects truthful pending state, preserved exact request after Activity recreation,
read-only reopening and identical frame on explicit Retry. Focused10 run38114016651
awaits expected missing Register name RED. Activity recreation is not actual
process death; real interruption remains later acceptance. Writes target only a
controlled loopback peer, never either production backend.

Pending tracer actual RED Android10 run38114016651:2cases, unavailable casePASS,
pending case fails at missing Register name, line89, total8.319s. Original failure
PNG individually viewed and XML inspected show successful availability with no
Register action. Verbatim evidence retained at first-pending-red. Later pending/
recreation/exact-retry assertions were not reached. Minimal request storage/state
now added; debug/test assembly2m6s passed before a two-line synchronous busy guard
addition (the next hosted build verifies that addition). One encrypted immutable
request is validated against author/registrar/module/name and saved/verified
before send. Reopening is read-only; Retry first rechecks ownership and only sends
the original candidate when still available. Native GREEN and review pending.
No actual process-death, identity replacement, proof-cache or name-display claim.
