# Signed mention-list RED

The next protocol tracer has an independently signed create-tweet mention array
containing the owner, with ordinary text that has no public-key token. A/B pinned
constructors normalize payload.mentions to an array before signing; native must
recognize that signed list independently of the text form. Existing text and
strict reply-kind cases are regression companions. The text-only parser returns
empty instead of the expected A signed mention list. 3protocolcases/2PASS/1failure/0.606s; build44s. No UI/network-wide/chain
claim is made from this pure protocol test.
