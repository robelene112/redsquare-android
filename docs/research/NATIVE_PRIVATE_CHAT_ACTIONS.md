# Existing private Chat actions

Named public source inspection, 2026-10-13. This addresses the whole #25 Spec
review's existing-capability finding; it does not add another ticket or claim
native implementation. Parent #1 requires preserving existing Saito capabilities
within the agreed scope. Later group/queue/mute/media tickets do not own these
ordinary private-message actions.

## Provenance

Pins A `9a4ca7e9442eb5e872168ced361ce741d7d139d7` and B
`214504ecece76d1cd20a245fe4468eccbfca2951`, SaitoTech/saito. Named raw files
were fetched without node/runtime initialization or private-state access.

| File | A SHA256 | B SHA256 |
| --- | --- | --- |
| node/mods/chat/chat.js | 35e372b7d9da0ddfd6286a93059c00a3e34eb494b62350db88a9380c4814b1af | 28a8609343e75dec7982498c0bafd7aa99be0a499156f43c5a543435426209eb |
| node/mods/chat/lib/chat-manager/popup.js | 425f94fb533b5291d520a814bb6a70d836074970005495231c9f34c35347b133 | ab9888fea96629773ae8275fdae6732678dc96da125069c9ab2dbf2ff5eb1d22 |

The named createChatLikeTransaction and receiveChatLikeTransaction method bodies
are byte-identical at A/B. SHA256 respectively
`33630c67a1e39dce129ef72c11b100d33ac4f785b30ee684b0df0c61ec0933b9`
and `da4a7c7900d4d8ff45742261f288e201963a2c7f8f966d67a788f914e4cbc4a1`.
This verifies those source bodies, not complete deployment/version equivalence.

## Reply and Copy

Both popups expose these actions on ordinary messages. Reply excludes an
existing blockquote, obtains visible body text, takes the last30 JavaScript
characters when longer, and prefixes a sanitized blockquote whose href is
original sender plus timestamp. Input appends the ordinary typed body.
[Reply A](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/lib/chat-manager/popup.js#L503),
[Reply B](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/lib/chat-manager/popup.js#L518).
The current native formatter only reads this shape. Native reply composition,
removal, draft retention and controlled encrypted sending still need verification.

Copy excludes the quoted prefix and hidden action controls, uses treated links'
actual hrefs, and trims outer whitespace before writing the clipboard. Native
copy must preserve the displayed typed body's literal text rather than exposing
reply-wrapper HTML or stripping arbitrary typed tags.
[Copy](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/lib/chat-manager/popup.js#L530).

## Chat reactions

The popup calls createChatLikeTransaction(group, originalSignature,
originalAuthor). The signed body is an unencrypted object with module Chat,
request `chat like`, group_id, sender, signature and mentioned. Outputs address
the mentioned original author and group members. The signed transaction is
propagated, then sent through the usual Chat Relay wrapper. Thus reaction
metadata follows the existing public signed protocol; it is not encrypted
private-message text.
[Construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2057).

Receipt finds the original message by signature and toggles liked_by[sender]
and its count. The dispatch paths apply hasSeenTransaction before calling the
reaction receiver, preventing an identical relayed/confirmed operation from
being toggled again. Native receipt must authenticate the original signer,
validate the addressed private pair/group/target, retain original event identity
for deduplication/retry, and keep local reaction state isolated by wallet/backend.
[Receipt](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2103),
[Relay dispatch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L911),
[Confirmation dispatch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L786).
No reaction vector, native action, complete browser UI or live delivery has been
verified by this source inspection. Implement one interaction slice at a time.

## Native clipboard boundary

Android's official clipboard guidance supports ordinary ClipData and a sensitive
content flag that suppresses the Android13+ copied-content preview. It recommends
app feedback only through Android12L, because newer systems supply their own
confirmation. Private Copy should use that sensitivity flag and copy only on an
explicit action. Verify actual foreground clipboard contents with public fixture
text and restore the prior emulator clipboard without printing its contents.
[Android clipboard guidance](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste#add-sensitive-content).

## Reply composition details

Pinned Input.getInput at308–335 auto-mentions quote_src before prefixing the
quote and appending literal typed input. Popup passes its SaitoUser wrapper's
publicKey as that source; Chat renders the wrapper from sender (1815–1820).
[Input](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-input/saito-input.js#L308).
Popup rejects a quote without typed content. Clicking an existing quote scrolls
to a locally available sender+timestamp anchor; absent targets do nothing.
[Quote navigation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/lib/chat-manager/popup.js#L593).

The optional --reply-compose reference reproduces the pinned popup's last30
UTF16 excerpt construction and executes actual pinned Browser.sanitize. It
records the prefix/body/auto-mention expected by the native UI fixture. This is
a source-guided composition oracle, not a DOM run or native send acceptance.
Existing default/mentioned/reply generation stays unchanged.

Reply acceptance additionally requires --check-native-reply-send to independently
verify the captured native original with actual pinned WASM signature verification,
Crypto recipient decryption, exact full body and mentioned array, and actual
Chat.addTransactionToGroup receipt/duplicate suppression. UI text-only receipt
cannot prove mention metadata. The public capture stays inside the approved
unsafe-scalar fixture boundary. Reopened draft preview is checked before removal.

Named pinned SaitoUser template directly confirms outer data-id is publicKey
(lines20–23), SHA256592fafd1ee8b9e89faf8ea5565b063edd0e56ad737f2cafe76e9f1d8f2cefe55.
[User wrapper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/ui/saito-user/saito-user.template.js#L20).

The optional composition corpus also includes an actual independently signed/
encrypted timestamp-zero original and its pinned sanitized reply prefix. A new
public UI case requires quote-only Send disabled, literal draft retention/removal,
explicit encrypted send and cached recovery. --check-native-zero-reply-send must
verify that captured original against compositionZero. Default/reply corpora
remain unchanged. This targets the minimum review's producer/parser bounds gap;
no zero-timestamp native result is implied by corpus generation.

## Executed reaction reference

`build-support/references/private-chat-like-reference.cjs` executes actual pinned
Chat.createChatLikeTransaction and receiveChatLikeTransaction, plus actual
saito-js Transaction.toJson/packData, in the isolated network-disabled runtime.
Wallet fee/unsigned-transaction construction and outbound network are adapters;
fixed public unsafe signing uses independent WASM/elliptic reference. Two distinct
signed operations target the independent original1→2 and toggle scalar2's like
on/off in the actual receiver. Corpus is native-private-chat-like-reference.json.
This proves named construction/body/outputs/Relay data and receiver toggling.
It does not execute dispatch deduplication, real wallet fees, constructors, DOM,
storage/application initialization, native UI/state or real recipient delivery.
Missing copied input and ordinaryJSON BigInt serialization initially failed;
correct publicinput/JSONBig output fixes them before valid corpus/exit0. No native
failure inferred. Reply26 runs remain separate, not accepted by this oracle.

## First native reaction tracer

The public UI/controlled-peer/cached-recovery tracer requires explicit Like,
count1/Unlike, truthful Forwarded-without-confirmation text, exact authenticated
body matching the independently constructed reaction corpus, same signed original
through propagation/Relay, and retained count/action/status after failedArchive
recreation with exact wallet/backend and no automatic rebroadcast. It captures
only the public unsafe signed action. Native Like is not implemented yet; no RED
or acceptance inferred from this tracer. Unlike/live/dedup/retry follow separately.

`--check-native-like` independently reconstructs the captured publicunsafe native
operation with actual pinned Chat.createChatLikeTransaction at its captured signed
timestamp, compares exact detached/payload bytes and recipient/signer/body metadata,
then executes actualreceiver toggle. Default reactioncorpus remains byteidentical.
An ordinary encryptedreply control actually fails with exit1/ERR_ASSERTION. A
successful nativeLike capture/checker is stillpending. This doesnot execute full
walletfee or dispatchdedup/realnetwork behavior, retaining explicitadapterlimits.
