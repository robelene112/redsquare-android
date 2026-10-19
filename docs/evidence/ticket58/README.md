# Content-first application frame58

Review baseaa40be525. Initial tracer sourceb84f472c2, Android17 run39326953921,
retains the actual RED:1JUnit failure in11.938seconds because no selected Home
destination exists. Instrumentation process exits0; the test result is FAILURE.
Original PNG/XML and the complete class output are retained without image edits.

First production slice introduces a compact shared header, content-first backup
notice shown on the home feed, an explicitly selected Home destination, consistent
primary navigation and an overflow for saved/queued publishing work. The identity/
node guard and existing draft-save navigation remain. Returning to Home restores
feed position; Search and own-profile state are retained by the scoped navigation
memory. Wallet and Settings stay reachable, and node context remains visible.

This is implementation in progress, not complete58 or app-wide UI acceptance.
Native green, theme/font/IME/motion/recovery walkthroughs, full regression,
two-axis review and signed UI delivery remain pending. The separate Node45 beta
is wording-only and is not delivery of this redesign.

The first production execution e50538e60/run39328611127 is also FAILURE:
Home selection now passes, but the post body begins below half the320px-wide
viewport. Original capture/root inspection confirms the remaining action row
uses reading space. This is retained as reading-space-red17, not green proof.
The next minimal slice65e1d6002 moves compose to a persistent native vector action,
keeps maintenance in the feed toolbar and reserves list space beneath the action.
