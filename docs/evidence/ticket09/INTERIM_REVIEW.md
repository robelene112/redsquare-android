# Posting interim review

2026-10-09. Read-only project code-review agents reviewed base
`78163d62d47fd95698da477052e2594fa17dc401` through
`42c577498cca3b588b0e9542a40a7e3d4926489c`. This is an interim review,
not completed ticket acceptance. Findings remain actionable until resolved and
the final reviewed source and full verification are recorded.

## Standards

Three documented preservation/recovery breaches:

- Remembered composer edits are saved only on its own Save/Back/Send actions.
  Backend switching disposes unsaved text. Wallet replacement changes the
  store namespace. Violates preserving user identity and recoverable work.
- Back/Save remain enabled while Send creates and persists its candidate.
  A concurrent save can capture transaction=null and overwrite that candidate
  before its assignment to UI state, losing the original publication identity.
  Serialize persistence/send transitions and preserve any saved candidate.
- Transport/archive failures use a storage-error message. After enqueue,
  Send is disabled without a usable same-screen retry. Violates explaining
  unavoidable recovery steps in product language.

Two heuristic findings: possible Divergent Change in the composer combining
rendering, persistence, message construction and peer reconciliation; Duplicated
Code in repeated public frame/archive-row fixture extraction and independent
vector assertion shapes. Resolve after securing behavior, avoiding speculative
abstractions. No additional actionable privacy, ADR or test-boundary findings.

Reviewer: `/root/ticket09_interim_standards`.

## Specification

Six actionable findings:

- Parent#1 requires locally saved drafts so navigation/outages do not discard
  work. Unsaved text resets on backend navigation and activity recreation.
- #10 requires truthful submission state. Restored signed candidates show
  neither pending state nor reconciliation, while the copy says drafts remain
  unsent. Storage and transport failure explanations must differ.
- Parent#1 requires preserving retry identity and avoiding duplicate posts.
  After enqueue, provide a visible Retry/Check action using the original
  candidate, reconcile previously available work and allow subsequent creation
  without indefinitely occupying the composer.
- Parent#1 requires explaining names/pending work before wallet replacement.
  WalletImport currently reads backup-option risks, missing native posting
  records. Namespacing prevents wrong-wallet signing but does not explain
  outgoing native work during replacement.
- #10 and parent testing boundaries require recovery/failure verification:
  pending cold recovery, failed saves, retry identity, wallet replacement
  isolation and writes through both controlled backend contracts remain.
- Resolve and verify the declared text compatibility policy: the researched
  web baseline distinguishes a 500-unit unfunded threshold from longer funded
  text. Avoid an unsupported universal 500-unit native cap.

No unrelated scope creep. Independent construction/hop references and verified
archive availability are sound. Reviewer: `/root/ticket09_interim_spec`.
