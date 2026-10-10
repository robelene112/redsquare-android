# Native quote verification

Ticket #13; review base `1b7f0f6`. Accepted boundaries are #1 seams1–4:
observable Android flows with controlled compatible peers, independent signed
vectors, recovery/failure and cross-client representations. The primary-source
contract is NATIVE_QUOTES_CONTRACT.md; no live write is authorized here.

First tracer publishes one quote with commentary, exact signed original plus
current embedded presentation, then reads both commentary and original in feed
and the quote's own conversation. Peer checks request, reference, recipient
ordering, both nested representations and one transmission. Image composition
is completed by #15. Local debug/test assembly passes57s. Actual Android10 red in38025957239 atabb9d85: missing Quote action,
4.833s. Downloaded log and inspected inside-activity image retained under
ticket12/first-red. First minimal implementation is in progress; no completion
claim.
