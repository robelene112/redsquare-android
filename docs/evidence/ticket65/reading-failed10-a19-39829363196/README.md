# Search65 retained-list feedback diagnostic

Run39829363196 ata1965bf6e, Android10, whole FAILED. Eight original Search
methods pass; partial-history fails when Retry search cannot be reached after
recreation. New classes were not reached. Before capture shows the failure
summary and retry; failure capture shows People at the top without that row.

The animated feedback slot inside the lazy list initially measures empty on
re-entry, allowing the list to anchor on People before the feedback expands.
Feedback is moved to a compact fixed row above results, so retry remains
reachable while the list retains its reading anchor. This also removes an empty
first list item. Existing identity, paging, retry and deadline assertions remain.
The next native run must verify this correction; no complete acceptance is claimed.
