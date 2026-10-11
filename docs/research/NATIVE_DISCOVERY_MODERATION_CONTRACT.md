# Native discovery, personal controls and reporting

Read-only preparation for #20–#22, 2026-10-10. A/B mean official Saito pins `9a4ca7e9442eb5e872168ced361ce741d7d139d7` and `214504ecece76d1cd20a245fe4468eccbfca2951`. Cached pinned primary sources and existing baseline notes were inspected first; selected public official files were fetched in memory. Only this document was written. No node, wallet, database, configuration, service, application initialization, build or live transaction access occurred.

These are compatible source contracts, not current deployment pins or delivery proof. [Version provenance](SAITO_VERSION_PROVENANCE.md) separates the owner's inspected A source from saito.io's newer served bundle. Runtime availability must still be checked independently on both approved backends.

## #20: Search available history

Neither inspected RedSquare pin implements a text-search endpoint. Both sidebar templates explicitly park Search until it is wired up; the previously captured official served source map contains that same parked UI. [A sidebar:24](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/sidebar.template.js#L24), [B sidebar:24](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/sidebar.template.js#L24).

Use the authenticated selected peer's existing application RPC:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","flagged_ne":1,"limit":100,"created_earlier_than":1700000000000}}
```

Archive matches schema fields exactly; `field2` selects author, `field4` selects parent, `field5` selects thread, and `sig` selects exact transaction. There is no body-substring predicate. Created/updated timestamp bounds are strict `<`/`>`; limits default to 10 and clamp to 10–100. Presence of `ascending`, even false, enables ascending ordering. Server overwrites requester/time metadata. Timestamp pagination has no secondary tie cursor, so over 100 matching rows sharing a timestamp can be missed. [A handler:353](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353), [A query:712](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L712), [B query:713](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L713).

Native strategy: search verified cached posts immediately, progressively fetch bounded pages from that backend, verify/decode before matching text, and deduplicate signatures. Include replies by omitting `field4:""`. Preserve partial results on later failure; distinguish no matches from unavailable search. Provide Load more and describe coverage as available history scanned, never exhaustive chain search. Refresh/edit/delete and personal/backend filtering must also apply to results. Avoid inventing raw-SQL or unsupported wildcard requests.

People: discover identities from verified posts, retained follows and already loaded profiles; locally match known identifiers/public keys. Resolve an exact public key or finished username through Registry:

```json
{"request":"registry query","data":{"request":"registry query","keys":["publicKey"]}}
{"request":"registry query","data":{"request":"registry namecheck","identifier":"Name@saito"}}
```

Namecheck uses exact equality, not prefix/fuzzy directory search; registrar connectivity can leave callbacks unanswered. Use deadlines and existing signed registration-record verification. Key-to-name maps alone are unsigned lookup results. After resolving a key, fetch Profile archive records with `{field1:"Profile",field2:key,limit:100}` and the selected-backend profile/posts flows. Profile snapshots replace prior fields; legacy updates merge chronologically. [Registry A:539,837](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L539), [Profile A:280](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L280), [verified-name details](NATIVE_USERNAME_CONTRACT.md).

## #21: Personal mute and block

Modtools persists `options.modtools.{blacklist,whitelist,permissions}`. List entries contain `publicKey,moderator,duration,created_at,hop`; duration is milliseconds, with `-1` representing indefinite entries, but inherited moderation can downgrade/halve duration. Browser Block defaults to 200000000 milliseconds. The shared author filter gives whitelist precedence. Local Block is applied before any peer send. Server blacklist changes require trusted moderators; `{ok:1}` does not prove mutation. [A event:89](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L89), [A persistence:1015](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L1015), [B filter:718](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/modtools/modtools.js#L718).

No account-mute implementation was found. Chat conversation mute is separate LocalForage state; options-backed `chat.black_list` and keychain `mute` markers have different purposes. Standard wallet export copies options, including legacy lists, but not LocalForage conversation state. [Export:1244](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1244), [Chat controls](REDSQUARE_MODERATION.md).

Implement the accepted native policy explicitly: identity-scoped persistent personal mute hides posts/social notifications; block additionally suppresses incoming Chat; conversation mute retains messages. Share personal choices across both backend sessions, keep backend assertions separate, and apply personal blocks even when a backend whitelists the author. Refilter cached feed/thread/profile/search and later notification/Chat boundaries immediately. Save failure must preserve previous policy. Preserve opaque imported options; do not convert every foreign-moderator legacy entry into a permanent personal choice. Define a versioned native backup field if exporting native controls; wallet key import alone cannot recover absent local preferences. [Detailed import/provenance contract](NATIVE_MODERATION_CONTRACTS.md).

## #22: Truthful reports

Signed native payload: `{module:"RedSquare",request:"flag tweet",data:{signature:postId}}`. Send the signed transaction through application RPC to the selected authenticated backend, not a broadcast to every peer or a notification to the post author. The constructor permits keys but browser Report supplies none, using the wallet's default self recipient; transport destination determines the moderator backend. Browser instead sends unsigned outer `flag tweet` with the same data and optimistically flags locally. [Constructor A:1206](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1206), [menu A:182](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L182).

Both pinned receivers accept the module-qualified request and return correlated `{}` after the flag handler, including when the target is absent. They set archived target `flagged=1`; no durable report receipt, reason field, reporter authorization check or separate report record is established. Moderator approval sets `flagged=2,curated=1`; moderator deletion removes the archive row without a universal tombstone. These are distinct actions. [A acknowledgment:1036](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1036), [A flag:1813](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1813), [B flag:1584](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1584), [review A:2043](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2043).

Show destination before confirmation. Distinguish transmission, correlated handler acknowledgment, explicit failure and unknown delivery after timeout. None means moderator review or automatic removal. Exact Archive recheck without `flagged_ne:1` can observe backend flag metadata; absence can mean pruning/retention and is not deletion proof. Isolate report state and cache moderation by backend. No backend modification is required for this truthful contract; stronger receipts are optional additional server work.

## Verification limits

Controlled Android flows must prove search paging/partial errors/navigation, identity-isolated durable controls across switches/imports, original report bytes/destination, timeout handling and acknowledgment wording. Both live backends still need read-only capability/readiness checks; controlled writes establish repeatable interoperability without production reports. A/B differences do not establish a necessary node update. Global prefix directory search, exhaustive archive search, permanent history and durable moderator receipts are unsupported claims.
