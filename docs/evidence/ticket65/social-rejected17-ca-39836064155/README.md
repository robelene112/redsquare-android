# Rejected full Android 17 dispatch

Run 39836064155 was cancelled after its Social job failed. Original Social artifacts
are retained with run metadata and archive bindings. No result from this run
forms accepted full verification.

PostPresentationPreviewTest.ownPostActionsOpenFromEveryBrowseSurfaceWithoutPublishing
failed at line141 because hasScrollAction matched both the horizontally scrolling
query input and results LazyColumn. The native failure screenshot shows the own
post and action button present. The fixture-only correction at52ef65072 selects
the results list through ScrollToIndex for both Search-route operators. Target
matchers, assertions, deadlines and production are unchanged. Paired review0/0.
