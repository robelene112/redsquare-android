# Ticket 113 independent reviews

Base: `4f7b9923ea214b18cb76a9193c238ae232839b84`.
The two read-only axes began with separate fresh contexts. Neither reviewer
changed files or ran tests. Source reviews do not establish runtime acceptance.
Final source sign-off: `9452b92ee152f764c94b28244de8c6320191ade6`.
Both axes independently report zero remaining actionable findings. Native
runtime acceptance is recorded separately and is not implied by source review.

## Spec

At `16cb4af0e55135768fa642a6e2e7e4456bc9b7f9`, two P2 findings:

- A count cap rejected valid all-eligible quotes with more than512sales inside
  the existing128KiB response budget. Actual pinned Store/SQLite/WASM generation
  now supplies a513-sale quote and exact candidate. The regression was RED then
  GREEN after removing only the arbitrary count cap.
- A lifetime100-record journal cap permanently blocked further withdrawals.
  Paging now retains every submitted/uncertain original and every fee reservation,
  with24-row node-filtered native history and cross-page original lookup.

Both were independently resolved at
`8623473c29837dddf8c9950c6a9dc9fef4aa47bc`, without scope creep.
At771cbcaa the Spec axis additionally identified incomplete first-index creation:
`pages.new` counted as a committed index before authenticated page recovery ran.
The exact filesystem replay was added at3175a0508. At9452b92ee,
the reviewer independently confirms that only committed base/.bak index files
count as an index. Authenticated contiguous retained pages recover without
changing original identities, submission flags or wallet-wide reservations.
Missing or corrupt committed data remains a barrier. Actionable saved requests
also precede remote balances, avoiding movement when reports arrive. No new
actionable Spec findings or scope creep.

## Standards

At16cb4af0e: zero documented-standard violations and one P3 judgement call for
repeated Android prompt policy. StoreDeviceAuthentication now shares the platform
adapter while each operation retains its token, lifetime and send authority.
The reviewer resolved this finding at8623473c2.

At8623473c2: one P2 documented-standard violation. A new page committed before its
index could leave every original and wallet reservation inaccessible after process
loss. Actual native43594141768 reproduced this at the missing-index check.
At771cbcaa, complete authenticated orphan pages recover through a local index
repair. Submitted/uncertain flags, original bytes and fee reservations remain;
no financial RPC is sent. Both SDKs pass the absent-index replay.
The remaining `pages.new` crash window was independently confirmed by both axes.
At9452b92ee the Standards reviewer confirms that partial-only indexes
no longer block authenticated recovery and actionable rows stay above remote
loading/balance changes. No new documented-standard violations or actionable
heuristic smells. Dedicated native acceptance is recorded separately.
