# Ticket52: offline draft privacy and safety center

Parent36/spec1 remain open. Review base:
`45a90620211c2c272ae2c76bd3e00f46a27ba070`.

The owner requested `OWNER_INPUTS.md` and explicitly authorized implementation
with recognizable placeholders on2026-10-19. This child supplies a read-only
Settings reader for six bundled drafts. It does not implement agreement to final
terms, reporting/deletion operations, working public contact channels or store
submission. None of those parent criteria is completed by a document reader.

## Observed red and corrections

Run39211393123 at8f08a1627 is FAILURE, with the new single native case failing
because Settings has no `Privacy and safety` action. Retained original XML shows
the actual Settings window, and the PNG was captured while its Activity remained
open. No production source changed before that committed test.

Native minimum74e54b5ce adds the reader. The first Standards review identified
missing SDK-specific exclusions for the two new focused workflow scopes;
e53cb56b9 fixes those two conditions. Focused10 run39212130707 and17
run39212133586 pass at e53, but root's visual inspection finds low-contrast
Android10 dark-dialog system icons. `contrast-before/` retains that original
screen and separate XML observation. The initial full attempts39212758904/39212762063
were intentionally CANCELLED to correct production first, not accepted.

795aef39e sets light/dark system-icon appearance on the Dialog's own Window,
keeps a Draft label in the non-scrolling header and strengthens the existing
case to reach each document's final paragraph. No deadline or existing
acceptance assertion is weakened. Earlier74e focused/source attempts were
superseded/CANCELLED while correcting workflow selection, not accepted.

## Corrected focused runtime

| Android | Run and source | Native result | Retained originals |
| --- | --- | --- | --- |
|10|39212876918,795aef39e|SUCCESS,1PASS,17.244s|16PNG and16XML, native output/exit and environment/scope records|
|17|39212879562,795aef39e|SUCCESS,1PASS,61.083s|16PNG and16XML, native output/exit and environment/scope records|

The same real Settings journey reads all six documents in Light/Dark, rejects
new owned-peer connections and disconnects its authenticated sockets for the
Dark pass, recreates the Activity while the deletion document is selected,
returns to the saved draft and verifies the unchanged complete wallet/backend
and zero controlled-peer transactions/reports. Android Back closes the center.
No test sends to a public backend, contacts a policy service or modifies a phone.

Root opened seven corrected original PNGs: Android10 Dark overview, Dark deletion
and Light privacy; Android17 Light/Dark overviews, restored Dark deletion and
Light contact. System icons are readable, the Draft header remains visible
after scrolling/recreation, and incomplete contacts/procedures remain explicit.
Screenshots and XML are separate observations, not atomic pairs. Paragraphs
longer than the viewport remain scrollable; these images do not imply every
line fits on screen simultaneously.

`sha256.json` binds91 original files. All33 new/red PNGs passed signature,
chunk-CRC, IHDR, decompression/scanline-length and filter-byte validation; all33
matching XMLs parsed. Two historical contrast originals are also retained.

## Review and remaining acceptance

Spec/Standards source review at795aef39e reports0/0, including the dispatch and
system-bar fixes against the original base. One test indentation-only correction
follows the focused source, with no statement, identity or deadline change.

Source run39212876588 at795aef39e is SUCCESS (core/gateway tests, Debug/Release/
Performance assembly, Debug/Performance lint and instrumentation assembly).
Full Android10/17 regression and signed ordinary preview publication are pending. The original application identities and existing full
gates remain in the harness, with this one additional class in the wallet/all
partition. The parent physical, configured-provider, approved-policy/operations
and actual Play requirements stay open in `OWNER_INPUTS.md` and issue36.
