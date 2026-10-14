Run38563262595 atc567725e8ca714d74244905d2425271a304a7ec8, Android10:
13 cases,12PASS,1failure,89.26s. removedAuthorsCannotHideAvailableEarlierGroupHistory
fails waiting for Load older messages at fixture260. Root inspected original
rejected-page-failure.png/XML: joined state, distinct history warning, empty
message area, no older control. Later cursor/allowed older/cache/pruning assertions
in this case did not execute. This is native rejected-page RED, not completion.

Ordinary signedGroupHistoryCanPageOlderAndRemainCachedAfterArchivePruning passed:
first/last messages,50-page cache, ancient live receipt with positive acknowledgement,
explicit next request with original signed page cutoff,52-cache records after pruning
and recreation, no extra submission. Root inspected original loaded/cached PNG/XML: ancient live and available older
messages remain visible alongside the beginning of recent history. The viewport
shows a subset; all50/52 and first/last assertions are reported by the fixture. No actual PID/physical
or whole26 proof. Public unsafe controlled-fixture identities only.
