# Group photo stage timeout, Android 10

Run38730549642 exact213f7f54 FAILED at120s in initial group photo method.
Fixed phase marker is queued-persisted: preview pixels, explicit Send, visible
Queued and exact protected queue/history/identity checks completed. The next
operation is the history photo observer, which searches via performScrollToNode;
that action can run outside waitUntil's polling bound. The marker narrows the
stall to that observer or subsequent capture; no queued-photo raster or actual
kill proof is retained. Capture command reports0 but archive contains a missing
directory diagnostic, with no valid PNG/XML. Restoration passes. No production
renderer cause is inferred. Replace searching scroll with the known second
fixture message in the positively scoped group list, retaining20s display and
exact pixel assertions.
