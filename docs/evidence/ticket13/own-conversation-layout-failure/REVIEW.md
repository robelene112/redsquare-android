# Own-post conversation layout regression

Android10 quote run38055428901 at8b3b898 passes the first five cases, including
original Retry recovery. Publication signs exactly one quote and displays it in
the feed. Static markers stop at scrolling commentary after opening Conversation;
the existing180s suite limit expires. The inspected host screenshot and XML show
Back/Reply/Edit/Delete/Refresh but no Conversation title, history or usable post
list. These are actual system captures, not the previously inconsistent Compose
bitmap evidence.

Adding own-author Edit/Delete to the existing single header Row exhausted its
horizontal space. Conversation title uses a weighted Text without a line limit;
the crowded header is consistent with the missing content and scroll hang.
The bounded production correction keeps Back/title/Refresh in one row and moves
Reply/Edit/Delete to a separate row. The quote test now also asserts the title
and scrolling list are displayed before scrolling commentary. Existing quote,
transaction, original and conversation assertions and suite timeout are retained.
Focused runtime and final full acceptance of this source correction are pending.

## Standards

Supplemental8b3b898...a545b94:0 actionable. Small coherent layout correction,
existing callbacks/conditions retained, observable visibility assertions and
accurately scoped host diagnostics. No actionable smell or documented breach.

## Spec

Supplemental8b3b898...a545b94:0 actionable. Visible normal views support author
action acceptance; no ownership weakening or unrelated feature addition. Quote
assertions and transaction counts retained. Full runtime acceptance still required.

## Runtime follow-up

Android10 run38055970805 at a545b94 PASS quote7 in15.585s plus actual process-
death recovery3.906s. Every publication phase completes. Inspected Compose bitmap
shows title, commentary and verified original together, with Reply/Edit/Delete
accessible separately. Logs/frame retained in conversation-layout-android10-green.
Local core/debug/test/release/lint PASS4m27s. Android17/full acceptance pending.

Android17 run38055973203 at a545b94 PASS quote7 in62.655s plus actual process-
death recovery3.642s. Every publication phase completes; inspected image shows
visible title/commentary/original/action rows. Refresh is disabled and Loading
replies visible during that capture; no claim it is a completed-refresh image.
Full same-source pair38056259800/38056261794 at7dcea19 remains pending.
