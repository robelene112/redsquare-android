# Final host-only public Registry read

Read-only observation2026-10-11T09:53:48Z, separate from native measurements.
The unchanged retained probe authenticates both approved public WebSocket peers,
requests Archive rows and unsigned key maps, then unknown and known-name checks.
It never sends a transaction broadcast (frame4) or reads private wallet options.
Its public scalar1 key is an unsafe compatibility fixture, not a user wallet.

Both peers authenticate, return ten Archive rows and nonempty key maps.
Those host row counts do not establish validated post content. Both unknown
NativeReadOnlyProbe20261011@saito and known Saitoverse@saito checks return
{"err":"no response"}; these are unusable ownership/availability proofs.
The subsequent `proofRows.filter` exception is this helper's summary error on
an error object, not a server key-map failure or evidence of an invalid signature.
The raw returned objects are preserved. No live registration, service cause,
chain inclusion, protocol upgrade requirement or block-production fix follows.

Executed using a read-only network-enabled chroot with empty environment:

```sh
docker run --rm --network host --read-only --cap-drop ALL \
  --cap-add SYS_CHROOT --cap-add DAC_OVERRIDE --security-opt no-new-privileges \
  --mount type=bind,src=/,dst=/host,readonly --entrypoint chroot nginx:alpine \
  /host /usr/bin/env -i /opt/saito-runtime/node-v24.13.0/bin/node \
  /home/david/projects/redsquare-android/.scratch/registry-readiness.cjs
```

A first invocation without DAC_OVERRIDE failed before any peer connection with
MODULE_NOT_FOUND. A targeted metadata read found a permission error traversing
the protected public dependencies; dependency absence was not established.
The corrected invocation exited0 and wrote no stderr. No live node files,
services, configuration, databases or production wallets were modified.

Prior independently measured native results remain separately retained under
native-public-registry-unavailable. This host probe does not replace those
native measurements or establish complete #19 acceptance.
