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
