# Preserve started Talk interruption

Product source: `b19efd41c2d33294c11809950c21539a8462c541`.
Full138 Android17 Chat fails the original TalkRoutes15s exact-token INTERRUPTED
goal. The test had observed the started call and actual foreground notification
before dropping both owned routes. The original log records call-signal failure
before route retirement; that handler unconditionally choseUNAVAILABLE. Its
terminal state then prevented the later route monitor from publishingINTERRUPTED.
The failure screen agrees. This is a real user-visible terminal-state race.

A failed signal now choosesINTERRUPTED for established started phases
(DIALING/JOINING/ACTIVE), retainingUNAVAILABLE for unstarted live invitations.
Exact-token/live guards prevent stale failures from overwriting terminal or
replacement calls. Existing foreground/media cleanup, retry delays and absence of
automatic call restart remain. Both source reviews pass with zero findings; local
AndroidTest compilation succeeds. Original native test bodies and15s/other
budgets are byte-identical. The existing real failing native case is retained;
no shallow duplicate state-predicate test or relaxed assertion was introduced.

Complete original Talk recovery10/17 scopes43980510614/43980514439 both pass
with independent acceptance and the explicit immediate17-capture limitation in
`../talk-focused/README.md`. Fresh whole SDK runs43983497246/43983501451 started
only after those accepted focused results, at frozen9513495d. Signed138 remains superseded and cannot be promoted. Signed139 and its fresh951 observer now exist and are staged only. Actual
131-to139 updates, fresh whole parents and public delivery acceptance remain
required.

The immutable failed Chat ZIP SHA256 is
19db9fc53373fdbb281245f2e3fe287728915967070b3f95434ea5f8bd9e5b16.
All final parent metadata/artifacts and original bytes remain indexed externally.
Original native scene files are retained under their original relative namespaces.
An earlier temporary flatstageJSON extraction collided across the two scenarios;
the explicit qualification names it, and only the original namespaced/TAR records
are authoritative. No original native bytes were edited. Both138 parent outcomes
remain rejected/canceled without reusable sibling or prefix acceptance.
