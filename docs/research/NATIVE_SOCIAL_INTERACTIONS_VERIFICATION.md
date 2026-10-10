# Native likes and ordinary reposts

Ticket #12, review base `58aed22`. Accepted seams: observable Android flows
against controlled compatible peers and independent protocol vectors, including
failure, retry and identity/backend recovery (specification #1 boundaries 1–4).
The primary-source contract is in NATIVE_SOCIAL_INTERACTIONS_CONTRACT.md.

First flow: like a signed reference post, verify compatible request/target and
recipient order, observe only backend-served counts, and retain coherent action
state across feed, thread and profile. The fixture serves four likes initially
and five only after receiving the signed operation. It requires one transaction.

Test candidate `789ff6d`; local debug/test assembly passes58s. Focused Android10
run `38016710571` fails24.504s waiting for served "4 likes" at line65, after
the signed root appears. Its inspected screenshot shows no like/count controls.
This is missing behavior, not a build or fixture failure. Public evidence is
retained in ticket11/first-red.

A shared selected-wallet/backend state now persists original signed likes before
transmission, validates exact availability on retry, and shares observed action/
count state across feed/thread/profile. Counts remain unsigned backend metadata,
never local increments. Unavailable/failed state retains the original candidate
and offers Retry. Local debug/test assembly passes1m41s; runtime green is next. No production transactions, private wallet uploads or new APK.
