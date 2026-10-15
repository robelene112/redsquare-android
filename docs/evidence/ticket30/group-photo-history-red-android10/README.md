# Group capacity RED and ineffective private fault, Android 10

Run38730369758 exact634dd820 FAILED,2cases/2failures32.835s. Group capacity
fails at existing EncryptedLocalFile.save5MiB bound, wrapped in group storage
recovery exception. Previously accepted group originals/draft remain before
rethrow. Private capacity/contact discovery passes, then the .new directory
fault fails to cause a save error at89. Android10 AtomicFile writes base/.bak
instead, independently confirmed from AOSP source. No product defect inferred
from that ineffective fault; no rollback/retry acceptance claimed. No UI or PID
gate runs in this storage-only scope.
