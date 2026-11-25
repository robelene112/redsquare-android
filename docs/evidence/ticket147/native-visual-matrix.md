# Spaces native visual acceptance for #147

The baseline is the signed [native prototype 3](https://github.com/robelene112/redsquare-android/tree/4dae92cdfacd24232902ab1024174e69b4be5a0a/prototypes/spaces-native), with [owner-approved Option B](https://github.com/robelene112/redsquare-android/issues/134). Retain the original prototype screens and the real app's captures with their source and Android configuration. Prototype examples establish the intended layout. Production recordings, accessibility output, and independent receipts establish what the implemented app does.

## Viewports

The configurations cover every pair across Android version, width, font, orientation, theme, animation, IME and navigation mode. Width is the configured short side in dp. Record actual content bounds, system insets, font scaling and navigation mode separately. All rows are prospective. A passing related journey only fills a row when its actual configuration and checks match.

| Case | Android | Width | Font | Orientation | Theme | Motion | IME | Navigation | Main check |
| --- | ---: | ---: | ---: | --- | --- | --- | --- | --- | --- |
| V01 | 17 | 320 | 200% | Portrait | Light | Normal | Closed | Gestures | Exact thread reply, status clearance, private-route error |
| V02 | 10 | 360 | 130% | Landscape | Dark | Reduced | Open | Buttons | Channel and reply composer, actual IME/insets |
| V03 | 17 | 360 | 200% | Landscape | Light | Reduced | Open | Gestures | Call bar, pending attachment and composer |
| V04 | 17 | 412 | 100% | Portrait | Light | Normal | Closed | Gestures | Switcher and delayed history/media during fast scrolling |
| V05 | 10 | 600 | 100% | Portrait | Dark | Normal | Closed | Buttons | Overview, selected navigation and category drawer |
| V06 | 10 | 600 | 130% | Portrait | Light | Normal | Open | Gestures | Native article editing, Markdown, review and reader |
| V07 | 10 | 412 | 200% | Landscape | Dark | Reduced | Closed | Buttons | Long identity, role and unread badge |
| V08 | 17 | 320 | 100% | Landscape | Dark | Reduced | Open | Buttons | Search and return from result with IME dismissal |
| V09 | 17 | 360 | 130% | Portrait | Light | Normal | Closed | Buttons | Invitation, rules and public-read/join distinction |
| V10 | 17 | 600 | 200% | Portrait | Dark | Reduced | Closed | Gestures | File information, quota and archive dialogs |
| V11 | 10 | 320 | 130% | Landscape | Light | Normal | Closed | Gestures | Prejoin, call controls and minimized call |
| V12 | 10 | 412 | 130% | Portrait | Light | Normal | Open | Gestures | Announcements, editable drafts and role-permitted replies |
| V13 | 10 | 360 | 100% | Portrait | Light | Normal | Closed | Gestures | Settings, archive and ownership review |
| V14 | 10 | 600 | 100% | Landscape | Light | Normal | Closed | Gestures | Backup and restore with missing history keys |

Capture the loading, populated, and applicable empty, error, offline, or restricted states per route. Tabs, app picker, Option B title, Back, Information, drawer rows, unread count, reply, composer, file actions, and call controls need at least 48dp touch targets with clear labels and discernible selected or toggled states. Use real TalkBack with switcher, unread count, reply, composer, file and call controls on both Android versions. Keep screenshot and temporal findings separately. For flows involving live changes, check actual frame timestamps with the channel title and reading anchor visible whenever the current state allows it. Cover switching, refresh, delayed embedded content, opening the IME, opening a reply thread, the call bar and modal entrance/exit.

## Direct comparison inventory

Prototype filenames refer to the original [owned native run](https://github.com/robelene112/redsquare-android/actions/runs/44209158851). Generic prototype captures represent the shared design beneath the five navigation options. Use the Option B captures as the header authority for every matched route. The functional capture column names the source of comparison evidence. A mapped route is still pending until the two renders have been inspected.

| Route | Prototype reference | Functional journey and required comparison |
| --- | --- | --- |
| Module entry / My Spaces / Discover / Activity | `01-home`, `design-2-overview`, discovery and activity states | Discovery, catch-up. Global picker separate, native tabs visible, activity grouping and unread treatment. |
| Option B and categorized channel drawer | `design-2-after-scroll`, `design-2-drawer` | Discovery, navigation, category access. Channel-title action and chevron, Space label and independent Back/Information. Compare pinned position before and after scroll and safe area. |
| Invitation, rules, public read, restricted links | `15-visitor`, `16-denied`, invitation and rules states | Discovery, private-management, catch-up. Invitation context, explicit consent and readable unavailable state. |
| Chat / thread / reaction / announcement | `02-chat`, `03-thread`, `24-thread-draft`, `29-announcement-restoration` | Conversation overlays, basic journey, catch-up. Distinct parent, persistent drafts, readable list and write controls, stable position. |
| Search | Shared prototype context and settings states | Catch-up. Native Space-scoped entry, filters, results, back to same query. Record any new screen absent from prototype as an extension with its own clipping and focus checks. |
| Article editor / Markdown / review / reader | `04-articles`, `05-editor`, `06-review` | Article journey. Separate title, editable source, explicit review, scoped publication, legible reading. |
| Files / quota / expiration | `07-files`, `19-storage-full`, `20-storage-expired` | File journey. Readable file metadata, explicit download, offline versus shared removal and status. |
| Prejoin / active call / minimized call | `08-prejoin`, `09-call`, `26-listen-only` | Room journey. Quiet join, media controls, one visible return/hang up affordance and no call-bar/IME overlap. |
| Members / moderation / admin log | `11-members`, shared report and settings states | Discovery, membership, moderation. Long identity + role, distinct local/member actions, understandable confirmation. |
| New Space / settings / archive / ownership | `12-settings`, `23-archived-room`, shared modal states | Management, category access and private ownership. Feed remains stable on back, destructive actions distinguishable and complete when enlarged. |
| Backup / restore | Shared recovery and modal states | Private conversation, recovery, MLS and signed update. Restore wording, actionable storage and visibility states, actual recovery receipt. |

[Normative quality plan](../../quality/SPACES_QUALITY_PLAN.md)
