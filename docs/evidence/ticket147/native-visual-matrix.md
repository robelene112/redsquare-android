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

The [owned viewport fixture](../../../test-fixtures/android/spaces-visual-viewports.json) is checked for all 28 axis pairs before launch. Its dedicated Android proof verifies the actual device settings, Option B target size and drawer transitions, and composer bounds with the real keyboard where requested. Use the targeted route journeys for the remaining route-specific checks in the table.

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

## Route states and test identities

The methods below identify the native route checks and their state changes.
Use each run's recorded viewport settings for comparison with V01–V14.
The matching geometry and timestamp inspections apply to the named transition.

| Native route and states | Test identity | Observation |
| --- | --- | --- |
| Module entry, empty My Spaces, public discovery, joined room | `SpacesDiscoveryJourneyTest#publicDiscoveryReadsWithoutJoiningAndCheckpointsTheActualVisitorPixel`, `#coldPublicReferenceReviewsRulesAndKeepsPerChannelDraftsAndMemberReturns` | Access loading and visitor read; public rules before consent; exact saved pixel and separate channel drafts after membership. |
| Option B, drawer, normal and reduced-motion keyboard entry | `SpacesCatchUpJourneyTest#captureOptionBAndTheComposerInAnActualViewport` | Physical device profiles, drawer open/close recording and measured channel/editor targets. |
| Invitation review, required rules, expired and unavailable destinations | `SpacesDiscoveryJourneyTest#coldMemberReturnCreatesAndReviewsInvitationsAndReadsUnlistedLinks`, `SpacesCatchUpJourneyTest#coldExactLinkAndRestrictedLink` | The native invitation records real consent, expiry with a review visible, and absence of membership on refusal. Cold exact and blocked links have separate visible states. |
| Conversation, thread, pending and confirmed pins, edits, reactions, announcements | `SpacesJourneyTest#messageMenusAndEditSurviveIncomingMessagesAndRealIme`, `#pinsOpenTheirExactRootAndThreadWhilePreservingWriting`, `SpacesManagementJourneyTest#coldArchiveRecoveryAndRealReopeningKeepHistoryAndLocalErrorsReachable` | Independently accepted arrivals and an anchored message, exact parent/reply identification, retained channel and thread writing, archived history and disabled controls. |
| Activity and search, grouped replies, filter, actual notification | `SpacesCatchUpJourneyTest#searchAndOpenRealSystemNotification` | Filtered result and grouped row remain tied to their signed source; the notification opens the exact reply through the OS. |
| Article writing, Markdown source, preview, private review and reader recovery | `SpaceArticleJourneyTest#reviewAndPublishArticleWithRealIme`, `#recoverIndependentArticleAndNextDraft` | Native keyboard, author decision before publishing, independently received article and recovered next draft. |
| File library, intake, provider terms, offline copies and shared removals | `SpaceFileJourneyTest#chooseProviderAndUploadFromTheAndroidDocumentPicker`, `#restoreOfflineCopyAndSeparateLocalFromSharedRemoval` | Actual picker, original byte receipt, retained metadata, download or offline recovery and separate removal paths. |
| Quiet prejoin, active room, minimized call, permission refusal and landscape writing | `SpaceRoomJourneyTest#prejoinIsQuietAndTheCallReturnsFromAnotherModule`, `#anAttachedDraftAndTheKeyboardStayClearOfTheLiveCallBar` | Real signed session, notice and active controls; original attachment and text after returning from the call; measured IME, Send and call control bounds. |
| Long identities, membership roles, permissions, report, management and archive | `SpacesManagementJourneyTest#compactLargeTypeShowsActualRoleErrorsAndArchiveControlsWithoutClipping`, `#compactOwnershipAndFormerOwnerDepartureUseTheActualMembershipControls`, `SpaceModerationJourneyTest#promotedAdministratorReviewsReportAndChangesWritingScope` | Named role and action states before and after ownership, scoped refusal/control changes, staff queue and signed log. |
| Protected history, native backup picker, review, missing history keys and restore | `ProtectedWalletRecoveryTest#actualFilePickerUnlocksProtectedHistoryOnlyAfterExplicitReview` at V14 | Real document and PIN flow, review before restore, missing-keys notice and retention of the existing wallet during review. |
| TalkBack, unread replies and active controls | `SpacesCatchUpJourneyTest#talkBackReadsTheOptionBControlUnreadReplyAndComposer`, `SpaceFileJourneyTest#talkBackReadsRealLibraryAndStorageControls`, `SpaceRoomJourneyTest#talkBackReadsTheQuietJoinAndActiveCallControls` | Actual accessibility focus and completed audible rendering, with native keyboard and geometry captured by the corresponding route checks. |

## Initial direct comparisons

Inspected the original native prototype3 captures beside the functional captures listed below. Reference captures outside the Option B trial include the shared design. These comparisons identify concrete differences on the recorded source and do not stand in for the final integrated build, event traces or the complete viewport matrix.

| Reference and observed route | Observation | Native evidence |
| --- | --- | --- |
| My Spaces and Discover | The app retains the three tabs, global picker and real community rows. The first empty overview displayed the prototype's “Independent Voices” banner. The fresh empty view contains one create action, an invitation action and a membership empty state. The same run then joins a real community and opens its visible drawer row. | [Initial discovery](https://github.com/robelene112/redsquare-android/actions/runs/44617057656), [empty and joined views](https://github.com/robelene112/redsquare-android/actions/runs/45683428876) |
| Option B, drawer and thread | The conversation uses one Space-name and channel-title control with independent Back and Information. Thread replies retain the parent preview and separate draft. The drawer follows the same community and channel grouping. The app adds reading-position controls and real account status. The narrow exact-reply capture shows both body lines. Its author line is partially above the list viewport at that reading position. | [Discovery](https://github.com/robelene112/redsquare-android/actions/runs/44617057656), [conversation](https://github.com/robelene112/redsquare-android/actions/runs/45495990945), [compact exact reply](https://github.com/robelene112/redsquare-android/actions/runs/45669562341) |
| Scoped search and Activity | Search is a dedicated native route using the shared app header and Back action. At 320dp and 200% text, the heading wraps to two lines. The filter, saved-history explanation, channel and matched text remain readable in the recorded result. In Activity, one card holds the grouped reply and its Space and timestamp. The route tests confirm the exact reply opens after tapping it. | [Compact search and Activity](https://github.com/robelene112/redsquare-android/actions/runs/45681925650) |
| Public read, invitation and blocked link | Public readers keep the channel in view beneath the house-rules sheet. It shows the Space name, rules, consent checkbox and disabled Join action until consent. The blocked route uses a scoped sheet over the original screen and describes what happened to saved writing. The prototype represented that state within the channel layout. | [Rules before consent](https://github.com/robelene112/redsquare-android/actions/runs/45683428876), [blocked link](https://github.com/robelene112/redsquare-android/actions/runs/45669562341) |
| Article review and file library | The article review preserves the title, excerpt, channel-scoped audience and a distinct final action. The library presents current storage status before the list and disables new uploads on expiry. The app uses the theme's actual colors and the supplied document state. | [Article review](https://github.com/robelene112/redsquare-android/actions/runs/45543613078), [file library](https://github.com/robelene112/redsquare-android/actions/runs/45567363227) |
| Prejoin, active and minimized room | Prejoin communicates the quiet default next to the join action. The active room shows the participant, separate camera and microphone, browse and leave controls. The compact return bar is above the conversation when the keyboard is open. Its production location differs from the prototype's above-composer bar and keeps the actual editor above the keyboard. Full call-bar, pending attachment and IME crossing remains to be verified. | [Room and keyboard](https://github.com/robelene112/redsquare-android/actions/runs/45601189802) |
| Members, administration and archive | Member name, role, profile entry and private chat remain visible. Signed changes are readable in a dedicated admin log. The archived announcement stays readable with a disabled composer and an explanation. These screens extend the prototype's shared layout for real membership and channel status. | [Members](https://github.com/robelene112/redsquare-android/actions/runs/44617057656), [admin log](https://github.com/robelene112/redsquare-android/actions/runs/45638013335), [archive](https://github.com/robelene112/redsquare-android/actions/runs/44832654787) |
| Backup and restore | The prototype shows a dedicated Backup page with separate recovery actions and a warning about history keys. The real app uses the shared Wallet and native file flow. Its import review shows the active and restored identity, whether message keys are present, and the limits of chat recovery. The confirmation requires scrolling to acknowledge the limits. The captures show successful export and import. The signed upgrade and landscape V14 still have their own checks. | [Protected wallet backup and restore](https://github.com/robelene112/redsquare-android/actions/runs/45516283155) |

## Landscape typing transition

The [V03 Android 17 crossed recording](https://github.com/robelene112/redsquare-android/actions/runs/45699144348)
contains 299 decodable frames with strictly ordered native presentation timestamps.
Between 22.091s and 22.384s the Gboard font notice is visible while the call bar
still uses its vertical position and clips the writing field. The recorded
frame at 22.946s shows the editor and all three call controls side by side.
The settled screenshots and native action test had passed. Compact writing
now uses the actual available height to place the controls before Android's
IME visibility signal arrives. The settled text, attachment and exact room
session checks remain part of the crossed journey.
