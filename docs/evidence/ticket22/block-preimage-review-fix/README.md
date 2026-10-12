# Shared raw transaction signature bytes

First full-block source review throughd99c779: Spec0; Standards0hard breaches
and1DuplicatedCode improvement. Both first reference/process-rerun evidence
axes clear. Separate d99c779 source push38193713102 passes release/lint/core58.

The narrow internal saitoTransactionPreimage helper now serves the two real raw
binary consumers: detached Archive message verification and full-block leaf
hashing. Each caller retains its framing, type, authentication and resource
checks. Only the common timestamp/43-byte slip/replacements/type/message byte
shape is shared. Typed transaction construction remains unchanged. The two
block cases share their fixture reader during this review stage as well.

Full core suite after the fix passes59tests/0failures/errors/skips in50s,
including the independent wallet/zero-payment/funded-payment signatures,
positive1010-byte block and original input-coordinate mutation discrimination.
All14original generated XML files are retained so59 can be independently
summed;16hashes cover them, the raw build log and counts. No console output in
the XML contains wallet/signing material. Bounded fix/source/evidence review
remains pending.

This is compatibility/content verification, not consensus admission, live
UTXOs, selected-chain ancestry, Android inclusion UI, finality or whole-ticket
acceptance. Other block/failure/QR/ancestry cases remain separate cycles.
