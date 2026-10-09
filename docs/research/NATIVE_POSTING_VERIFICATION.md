# Native posting and drafts

Ticket#10, delivery slice9 of35, in progress. Review base78163d6. Parent#1
approved application, failure/persistence and independent crypto boundaries.

First vertical case observes actual MainActivity with a controlled signed feed,
opens New post, saves synthetic draft text, recreates the activity and requires
the same editable text. The external peer records public transaction frames
and must observe no publication on saving/reopening. This tests app recovery
and explicit Send separation, not physical process death or draft portability.
Controlled archive requests remain normal verified reads. No production writes.

Initial actual functional red is pending. No composer/draft implementation yet.
Protocol researcher is checking primary construction/routing facts separately;
its source doc does not authorize production writes or imply delivery proof.
