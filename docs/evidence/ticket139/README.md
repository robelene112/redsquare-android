# Ticket139: community governance

In progress. The owned client/service archive increment implements authenticated
archive/reopen and blocks stale queued contributions after reopening while
preserving readable history and new drafts. It retains the independent MLS epoch
and historical-key delivery. Missing archive input gets a durable rejection.
See [the archive boundary record](archive-boundary.json) for three actual REDs,
subsequent GREENs, exact source hashes, evidence qualifications and source reviews.

Both incremental Spec and Standards reviews report zero findings. The nine affected
journal/private-content/archive cases also pass without failures or skips. This is backend source work,
not native or complete139 acceptance. The archive intent API/UI and the remaining
channel/role/private-access, future E2E exclusion, ownership, visibility and
Space dissolution requirements remain. Beta142 and all production node/provider,
public-write, financial and community-file-hosting gates stay unchanged.

The [archive intent/status increment](archive-intent.json) adds a real queued
command and per-channel durable receipt state. Actual accepted-ack loss preserves
the exact original; uncertainty is not success. A real competing control produces
a rejected change, while a deliberate new action can succeed after refresh.
Five current SQLite cases pass, including independent admission and available
private history while archived. Both incremental source review axes are clear.
The preceding nine related checks retain their earlier source qualification.
The new API's RED was compilation-only; no runtime RED is fabricated. UI/native
and the remaining139 requirements are still pending.
