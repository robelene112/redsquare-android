Positive chat-list draft check and unrelated Retry readiness failure

Android10run38430012756 at a306f3f reports20cases19passes and one Retry-body visibility failure in93.832s/core86. The new draft-navigation case passes: positive list readiness, exact unsent text after reopening, erasure/reopen and recreated-empty under unavailable history, identity/backend unchanged and zero transmissions. Root28XML and the original failure plus both unique draft captures inspected.

The failure is in anUncertainPrivateMessageRetriesTheSavedOriginalOnlyAfterAnExplicitTap atline372: UNKNOWN is visible but the saved outgoing text is not yet a semantic node when performScrollTo runs. The image shows incoming reference, UNKNOWN, disabled Message/Retry and Loading messages. The existing source publishes saved status before history/ready; this supports a readiness hypothesis, not a proven exact scheduling cause. No Retry attempt or its later tail executes in this failed case. Rawexit0 retained, OK gate fails.

Subsequent test-only change waits positively for outgoingText before the unchanged strict scroll/display check. No longer wait budget, weakened assertion or product change. Android17 companion passes all20 at this same source, but does not execute the subsequent wait.
