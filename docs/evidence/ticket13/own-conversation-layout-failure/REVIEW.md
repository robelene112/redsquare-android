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
