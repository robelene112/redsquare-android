# Gateway server-code acceptance candidate

Issue[#42](https://github.com/robelene112/redsquare-android/issues/42) supports
#32–34. Whole review base5a048524d8681781c1763f850cc3e0773b8e753c.
Latest implementationddfd7005cd20c4c75d04bbe12b9a2fe595fe7d36. Status: source and
individual evidence accepted; final integrated source CI/evidence review pending.
Do not close42 until those finish. Configured cloud/device acceptance stays#32.

| Requirement | Controlled evidence |
| --- | --- |
| Signed binding, renewal, FID/backend/categories and keyless retirement | [Binding](subscription-green/README.md), [renewal](renewal-green/README.md), [standalone JVM stop/restart](process-green/README.md). Real gateway HTTP/journal with synthetic identities. |
| Durable client recovery and fresh consent/identity/backend demand | [Lost bind/revoke acknowledgements](client-green/README.md), [native encrypted adapter](native-binding-green/README.md). Native Android10 run38994269120 and17 run38994270968 use an owned HTTP contract peer and synthetic FIDs, not the production JVM gateway or configured SDK. Portable JVM checks use the actual gateway. |
| Selected-backend Archive/parent/registered-name eligibility | [Archive](archive-green/README.md), [optional names](name-green/README.md), [missing/restored parent](parent-green/README.md). Both approved selectors against owned peers. |
| Full-block discovery, durable position and missing/reorganized history | [Chain](chain-green/README.md), [cursor](observer-green/README.md), [rewind](reorg-green/README.md), [retained warnings](gap-history-green/README.md), [recent-history recovery](history-recovery-green/README.md). No unavailable old-history or consensus/finality guarantee. |
| Durable opaque event/resolver and delivery recovery | [Reply](delivery-green/README.md), [lost sender acknowledgement](delivery-retry-green/README.md), [chain-derived withdrawal](chain-social-green/README.md), [automatic re-inclusion](runtime-green/README.md). Recipient proof/current binding/expiry remain required. |
| External data-only sender and explicit standalone operation | [Sender](sender-green/README.md), [current runtime](runtime-green/README.md), [current complete gateway suite](parent-green/README.md). Synthetic external HTTP acceptance does not establish Google delivery. |
| Bounded retention and cleanup facts | [Operating contract](../../PUSH_GATEWAY.md), [client configuration](../../PUSH_CLIENT_CONFIGURATION.md). Limits and periodic expiry cleanup are source-reviewed, not exhaustive expiry/lifecycle fixtures. |

Current exact implementation passes the full owned gateway suite10/0/0 in1m53s,
plus installDist; core:test UP-TO-DATE after prior fresh114-test acceptance.
[parent-green](parent-green/README.md) retains10 manifested files/23 source hashes.
Earlier native/source evidence remains pinned to its own implementation; it is
not presented as new native execution after the later gateway-only changes.

## Standards

Whole-source audit atddfd7005: no new documented breaches or actionable heuristic
smells. The stalled-observation limit received an explicit operational disposition.
Parent evidence/docs review at48537b27:0 findings. Final integrated review pending.

## Spec

Whole-source audit atddfd7005 found one stale client-configuration paragraph;
48537b27 corrects it. Final source/documentation disposition at48537b27:0 outstanding
findings. Parent evidence accepted. Final integrated review pending.

## Practical limits and external acceptance

- Seven-day subscriptions/retirement, seven-day signed-post dedup, wake resolution
  at most24hours and bounded by binding/post expiry. Startup/60second cleanup
  physically prunes expired state while running; stopped processes/backups do not
  receive that guarantee. Journal4MiB,2048 subscriptions/retirement records and4096 events.
- The128-point ancestry journal, recent bootstrap, Archive head and processing
  budgets limit available history. Unsupported/unreadable blocks or repeated
  proof-budget exhaustion can stall the saved cursor despite retries. The
  [stalled-observation contract](../../PUSH_GATEWAY.md#stalled-observation-limit)
  explicitly preserves that limitation; no exhaustive recovery is promised.
- Interrupted atomic saves, every exceptional shutdown interleaving, reset with
  pending social events, interior-gap suffix trimming, individual Android channel
  toggles and real SDK callback/rotation are source-reviewed or separately pending
  as identified in the individual packets. No fabricated execution claim.
- The sender bounds caller metadata wait and retains at most one pending daemon
  task. Cancelling it does not prove arbitrary external credential code ends.
- Hosting/TLS/body timeouts/supervision, project/IAM/credentials and deployment
  require separate explicit configuration/authorization. No such operation occurred.
  Existing owner Firebase setup question remains pending and is not repeated.
- Actual Google delivery, configured SDK registration/callback/rotation,
  closed-app/locked/Doze/physical Pixel checks stay#32 and Chat successors. #32 stays
  open. No real subscriber signing key is accepted by the gateway.
- Published beta24 is unchanged. This server-code acceptance does not complete
  all original35 tickets, Play publication or physical-phone acceptance.
