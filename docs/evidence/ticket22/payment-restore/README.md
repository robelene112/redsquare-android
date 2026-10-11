# Funded restoration and shared serializer

One independent literal funded-restoration case compiled then failed at the
unimplemented reader (46s build). After implementation, both funded cases pass
(49s build), including an assertion that the original zero-payment restore
rejects the funded candidate. Strict funded restoration checks complete framing,
amounts, unique ordinary funding, self change, output coordinates, canonical
payment metadata and signature. Unsigned input coordinates still require the
original candidate and durable reservation evidence at the application layer.

Review identified repeated Normal wire serialization and late oversized-memo
checking. The fix shares one internal Normal serializer behind distinct funded
and zero-payment factories; their admission and restoration guards remain
separate. Memo size rejects obvious oversized input before JSON allocation.
Both snapshot and payment expose defensive, unmodifiable slip lists.

After these fixes all57 core cases pass,0 failures/errors/skips,49s build.
This includes the pre-existing independent zero-payment protocol vectors and
two funded vectors. Count summary is derived from the complete current JUnit
set; retained payment XML identifies the two new cases. No Android transfer,
persistence, authentication, chain admission, inclusion or final acceptance
claim is made. These remain subsequent #23 cycles.
