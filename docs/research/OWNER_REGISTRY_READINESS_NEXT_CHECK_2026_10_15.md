# Owner Registry readiness: next check, 2026-10-15

Source-only proposal for [#19](https://github.com/robelene112/redsquare-android/issues/19).
Only this note was written. No live probe, initialization, process/configuration/
wallet/database/log inspection, build, CI or production change occurred.

## What remains unknown

[Actual native evidence](../evidence/ticket19/native-public-after-fix/android10/README.md)
verifies official proof/empty availability after the callback correction. Owner
namechecks still time out. This establishes absent usable responses within the
original deadline, not missing peers, a required upgrade or block production.

Pinned [Registry forwarding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L837-L868)
requires an exact peer-public-key match to the effective `registry_publickey`.
[Helper selection](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/modtemplate.js#L1258-L1291)
uses the server's `app.network.getPeers()` and returns silently when none match.
[Key-map fallback](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L787-L834)
is different, so successful hints do not establish forwarding.

[Initialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L171-L194)
can replace the compiled registrar for localhost/testnet. No configuration was
read; this is a source possibility, not an Owner finding. A generic Registry
service announcement does not identify authority or prove connectivity.
The [dispatcher](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/modules.ts#L134-L185)
can emit the same fallback for unhandled requests or swallowed handler errors.

## Narrow observation seam

First compare only exact deployed public Registry/helper/dispatcher source files
and hashes against the pins. The [maintained operations guide](/home/david/snake-stickerbook/docs/OPERATIONS.md)
locates production at `/opt/saito/node`; source on disk still does not prove
running code or effective authority. Do not initialize another app to inspect it.

No peer-status operation appears in the inspected Registry handler. A client's
own peer list reveals its connections, not Owner's internal peers. Another
unchanged public namecheck would not distinguish the remaining causes.

The useful future seam is a bounded observation **inside the existing server**
for one fixed public Saitoverse namecheck: module entered; effective authority
matches published registrar; self-is-registrar; matching peer count; forwarding
enqueue; callback received; response kind; elapsed time/error category. Retain
booleans/counts only, never options, complete peer lists/IPs, SQL, payloads or
logs. A matching peer/enqueue proves selection, not successful registrar receipt;
a verified returned proof establishes useful read forwarding for that request.

No existing authorized runtime observation hook is established here. Adding one,
attaching instrumentation, restarting or deploying requires separately scoped
operational authorization and staging verification under that guide. Root must
separately authorize any new concrete read-only protocol probe. Repairs, peer
changes, upgrades and registration transactions remain outside this research.

## Subsequent root source-only check

Root subsequently compared only the three exact public disk-file hashes in a
network-none/read-only, capability-dropped container. Registry and dispatcher
match pinA; helper matches bothA/B. See
[ticket19 source evidence](../evidence/ticket19/owner-public-source-check/README.md).
No options/configuration/wallet/database/log/process state was read. This does
not establish running code, effective authority or peer selection. Since both
pins contain the relevant behavior, an upgrade is not a demonstrated repair.
