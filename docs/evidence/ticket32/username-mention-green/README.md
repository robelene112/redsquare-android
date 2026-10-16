# Verified username mention acceptance

Bounded local slice of[#32](https://github.com/robelene112/redsquare-android/issues/32).
The shared receipt/tap classifier now uses the existing optional verified name
cache. It retains owned-parent reply priority and public-key/list mention paths.
No registration transaction, new network name lookup, wallet mutation, user
registrar setting or protocol-format change was added.

| Check | Exact source / result |
| --- | --- |
| Intended RED10 |70f6a4fc /38975655606 FAILURE,10PASS1FAIL80.705s, expected PUBLISHED but SUPPRESSED after signed name proof/cache setup |
| First minimum10 |78245041 /38976627865 FAILURE,10PASS1FAIL84.613s, username PASSES but earlier group editor observation fails before publication |
| Corrected fixture10 |78e4172d /38977710046 SUCCESS,11PASS81.609s |
| Minimum17 |78245041 /38976630590 SUCCESS,11PASS124.127s |
| Notification compatibility10 |78245041 /38976632886 SUCCESS,32 reading,1 permission,1 actual read-history recovery |
| Notification compatibility17 |78245041 /38976636125 SUCCESS,32 reading,2 permission,1 actual read-history recovery |
| Source release/lint |78245041 /38976622437 SUCCESS;78e4172d /38977709969 SUCCESS |

[Intended RED](../username-mention-red/android10/README.md),
[first failed minimum](../username-group-regression-failure/android10/README.md),
[corrected10](android10/README.md),[17](android17/README.md),
[compatibility10](../username-notification-regression/android10/README.md) and
[compatibility17](../username-notification-regression/android17/README.md)
retain exact logs, original PNG/XML, run metadata and hashes. The prior cancelled
documentation-only source run38975132047 carries no app claim.

Production source/build trees are exactly equal at78245041 and78e4172d, recorded
in[production equivalence](production-equivalence.json). The fixture-only bounded
exact-value wait has source and failed-evidence reviews0/0 before its corrected
run. It adds a15s observation wait without changing the expected AnnotatedString
or final assertion, production behavior or180s class bound. The failure's actual
predicate-time cause remains unknown; a later pass does not establish it.

Root inspected ten new username originals, the corrected10 group draft and five
selected notification permission/recovery originals, and parsed every retained
XML. Loading/failed-history destination states remain explicitly qualified in
the per-SDK notes. All11 social cases execute their restoration/saved-work tails
on both passing SDKs. The earlier failed group's later tails remain unexecuted.
Local minimum core/debug/test build passed3m18s; source reviews0/0 at990739b8.

This accepts verified-name recognition and its local Android alert/tap flow.
Actual Firebase transport/registration, viable backend integration and full#32
acceptance remain unfinished. No new beta, Pixel result or whole-ticket completion
is claimed. Public signed beta24 is unchanged.

Final combined evidence reviews report Standards0/Spec0 at3f3c5bb4. The later
corrected-source push check38977709969 also completed SUCCESS, including release
assembly/lint. Its exact metadata is retained in corrected-source-run.json.
