# Native posting and drafts

Ticket#10, delivery slice9 of35, in progress. Review base78163d6. Parent#1
approved application, failure/persistence and independent crypto boundaries.

First vertical case observes actual MainActivity with a controlled signed feed,
opens New post, saves synthetic draft text, recreates the activity and requires
the same editable text. The external peer records public transaction frames
and must observe no publication on saving/reopening. This tests app recovery
and explicit Send separation, not physical process death or draft portability.
Controlled archive requests remain normal verified reads. No production writes.

Actual red37964083012 ateb45228 reaches the signed feed and fails at missing
New post action, line32,3.29seconds. Public log/image inspected and retained
ticket09/draft-red. Minimal7360289 adds root composer plus encrypted local
wallet/backend-scoped drafts. AES256GCM AndroidKeystore key, package/filename
AAD, noBackup storage, AtomicFile/file and directory sync, decrypted readback
before closing. Missing key with existing drafts fails closed. Loads/saves run
on IO; failed saving retains editable text and does not confirm persistence.
Draft safety bound is1MiB of UTF8, not a Saito protocol limit. No Send yet.
Local core/debug/test assembly passes1m33s. Green37964861535 is running.

Primary read-only researchf31ed06 is in NATIVE_POSTING_CONTRACT.md. It records
compatible recipients/hops/transport and practical web validation limits. This
document does not authorize production writes or prove live delivery.

