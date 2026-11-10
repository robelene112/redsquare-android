# Neutral profile history information, issue98

The permanent Available history status used an accent dot and ambiguous copy,
which looked like an error or unfinished loading. The correction gives only
this profile guidance a neutral information icon and the label "History may be
limited". Accessible Details explains local history and history from this node,
including possibly missing older posts, replies or likes. Actual loading,
failed reads, unreadable/cache/likes conditions keep their existing separate
states and controls. Other status callers retain their existing rendering.

[Baseline42912497737](baseline/qualification.json) fails the new public-UI
history-copy assertion in the existing ProfileHierarchy method. The original
Posts/Replies/Likes assertions execute first. The same method additionally
requires accessible Details and no live announcement for static guidance in
its existing theme/text-size matrix. Restored-history selectors track the
clarified copy without changing their recovery goals or deadlines.

Issue97 corrective source and focused native review was complete before98
implementation began. Both fixes will share complete Android10/17, independent
final reviews, compatible signed-update and exact beta-publication gates.
Those final gates remain pending. Public latest remains105; superseded106
must never be promoted. Parents1/36/55/84/86 remain open.
