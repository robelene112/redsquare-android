# Registry callback experiment, 2026-10-15

Offline source experiment for #19 while root owns #40. No Android implementation,
CI, live peer request, node/service/database/wallet access, signing, credentials
or package installation. Only this document and ignored experiment scratch
were written. This tests the [source candidate](REGISTRY_READINESS_DIAGNOSIS_2026_10_15.md),
not the deployed explanation for #19.

## Exact source reused

A = `9a4ca7e9442eb5e872168ced361ce741d7d139d7`;
B = `214504ecece76d1cd20a245fe4468eccbfca2951`.
Downloaded six fixed public files. Extracted methods by their unique declaration
and closing method delimiter, retaining exact bytes/ranges and SHA256.

| Method | A lines | B lines | Exact method SHA256, identical across pins |
| --- | --- | --- | --- |
| Registry.handlePeerTransaction | 530–562 | 613–645 | `6a5456275360cfa0b95b2604539dda2eabfb67a2d4d2ced60171e18c6b01367d` |
| Registry.checkIdentifierInDatabase | 837–868 | 933–964 | `04f60acfc31f6f6ce94f77fe65c265a2a26b9bb664f346de8633835d0cf58dd4` |
| ModTemplate.sendPeerDatabaseRequestWithFilter | 1207–1238 | 1207–1238 | `60ca22943728187e459a908792b2b98fe93a1378b1106f2f6a2ec5a5ceae677e` |
| ModTemplate.sendPeerRequestWithFilter | 1258–1291 | 1258–1291 | `92a0a6c53274788fc8dcc8ec5e25cbdadd165319de6c6f7e5d0ea9f98d7aca3c` |
| Modules.handlePeerTransaction | 134–185 | 134–185 | `d7d12abda20c7c2b1c707dc4884a5e6977d61717572b622c37f29b78eec5673d` |

Primary sources:
[A Registry](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js),
[B Registry](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js),
[A helper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/modtemplate.js),
[B helper](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/templates/modtemplate.js),
[A dispatcher](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/modules.ts),
[B dispatcher](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/modules.ts).

Evaluated only these method definitions in isolated VM contexts, never module
top-level code/constructors/imports. Registry/helper methods are verbatim.
Dispatcher execution removes only TypeScript parameter annotations; its body
is byte-identical to the extracted body. Separate body hashes are retained.
No return, await, callback or dispatch logic was rewritten.

Stubs supply an in-memory transaction's message, permissive moderation, one
Registry module, peer-list lookup, slug/module lookup and the remote callback.
The direct-registrar control uses an empty-array database stub, never a real
database. Delayed delivery is deliberately held until a later event-loop task
after dispatcher completion; immediate delivery calls back synchronously from
the forwarding stub. No latency is claimed representative of production.

## Results

All eight scenarios passed, four per pin:

| Controlled scenario | Callback order |
| --- | --- |
| Relay, delayed registrar response | `{"err":"no response"}` before dispatcher completion, then `[]` afterward |
| Relay, no matching registrar peer | Fallback only; no forwarding call |
| Relay, immediate forwarded response | `[]`, then duplicate fallback |
| Actual-registrar branch | `[]` only; no forwarding/fallback |

Both callbacks use the original closure tagged request index 7. This proves
the same original callback can receive fallback and later rows in the exact
source flow. The tag is a controlled callback identity, **not an executed
network-frame serializer or native socket test**. Empty rows are stub data,
not signed ownership or namespace availability. The fallback serializes to 21
UTF-8 bytes; this does not identify the uncaptured native 21-byte response.

## Reproduction and retained evidence

From the repository root:

```sh
python3 .scratch/registry-callback-experiment-2026-10-15/prepare.py
timeout 20s docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges --pids-limit 32 --cpus 1 --memory 128m --user "$(id -u):$(id -g)" --mount "type=bind,src=/home/david/projects/redsquare-android/.scratch/registry-callback-experiment-2026-10-15,dst=/experiment,readonly" --entrypoint node sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e /experiment/experiment.cjs > .scratch/registry-callback-experiment-2026-10-15/results.json
```

The first command downloads only pinned public source. Execution used the
already cached Node 24.13.0 image, disabled network and a read-only scratch
mount. Exit 0, completed true, eight successful scenarios. Scratch retains
six full sources, extraction/body/source hashes, harness, ordered results and
`SHA256SUMS`. SHA256 anchors:

- Harness: `7c93861f5674dd25d228176cd4eaa25f220c2b31758ddcdddb46b9f36c017d8e`.
- Results: `ba91e584ac37e10a16913a1bcfc762e39d573d857eb5ca5e1aa8cb0ed506901d`.
- Provenance: `99233860ea2698a9ecae88d51c9cc88dcd064bea3b09f46713ca6d98e92b2c2f`.

No deployed revision, actual late response, cryptographic proof, live registration
or native workaround is established. Preserve the proposed bounded native
observer before drawing a live-cause conclusion. #19 remains open.
