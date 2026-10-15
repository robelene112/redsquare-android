# Known public Registry proof, host-only observation

A separate bounded follow-up to host-first-late-oct15 retains one additional
observable: whether a late known public row passes the existing core verifier.
This is not another unchanged readiness retry. Each approved peer receives only
one unsigned read for Saitoverse@saito; no registration/broadcast/transfer occurs.
The observer uses the same pinned pure WASM/Node/library-only read-only container
setup. Exact timestamps, endpoints, frame indices/types, elapsed times, lengths,
hashes and12s complete windows are retained in result.json.

The owner again returns only the exact fallback within12s. Saito.io returns that
fallback then a431-byte one-row array for the SAME request index. The observer
retains raw text only for this exact public name when all row keys belong to the
published public Registry schema and every value is bounded/scalar. The retained
raw-body SHA matches the frame hash. No arbitrary response/owner data is dumped.

An isolated network-none Java harness calls the existing compiled production
core SaitoRegistry.readNamecheck on those exact raw bytes. Result: verified-active,
Saitoverse@saito owned byqeyGZPxRZYFeCtMTZze7He2wzCUs3uLW8U8eZKC5Bb86.
The unchanged strict parser validates exact name/key shape, registrar signer and
signature, bounded block height/64-hex block hash and numeric lc. Here active is
the server's unsigned lc=1 hint; this is not independent canonical-chain/finality
verification or proof of future namespace availability. No requirement is relaxed.
Core source/class/dependency and complete compiled-class hashes, exact compile/
verify commands, harness and result are retained. No core/app/test source changed.

This is host transport plus execution of the shared JVM verifier, NOT the Android
application flow or a native workaround test. It supports a narrow investigation
of the first-response Registry path after #40. Normal peer error semantics,
strict proof handling, unavailable outcomes, wallet/backend binding, immutable
candidate and explicit Retry must remain. #19 stays open for native acceptance.
No public wallet registration, on-chain writer, node/service/configuration/database
change, publisher operation, credential access or complete app release claim.

Reproduce the host capture by the first/late observation's command with the probe
mount/output changed to .scratch/registry-public-proof40. core-inputs.json records
separate javac and java commands using the existing compiled core plus pinned
Kotlin2.1.20/Gson2.12.1/BC1.86 jars. The verifier runs with network disabled and
only public library/class/probe mounts; generated Java classes stay in scratch.
Public source: core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoRegistry.kt;
Registry schema/control flow is linked in REGISTRY_READINESS_DIAGNOSIS_2026_10_15.md.
