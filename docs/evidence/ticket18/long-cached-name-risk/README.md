# Long cached username replacement warning

Actual local RED:11 wallet cases,10PASS/1failure; imported maximum-length45ASCII
Saito name returns null instead of the expected cached replacement warning.
No app/node/wallet production data is used: only published unsafe scalar1/2.

Minimal fix accepts exact compatible1–45ASCII@saito cached identifiers while
keeping basename display and the previous16Unicode alias fallback. Matching
public key remains required; this is cached-risk detection, not verified ownership.
Actual full core GREEN:55cases/0failures/0errors/0skipped, build51s. The new test
also confirms options round-trip unchanged. Both bounded source reviews clear.
Affected native backup/import UI regression remains for final acceptance.
Raw build logs/test XML are verbatim; core-totals is derived from all suite XMLs.
