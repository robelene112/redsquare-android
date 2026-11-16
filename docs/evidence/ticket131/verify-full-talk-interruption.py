"""Select a separate prospective source snapshot without changing any native goal."""
import hashlib
from pathlib import Path

packet = Path(__file__).resolve().parent
original = packet / 'verify-full.py'
assert hashlib.sha256(original.read_bytes()).hexdigest() == '2895a8c6623d875b68d36512cc4f28dbcb1795b3ba4c3aa24e8de381c4f277e8'
text = original.read_text()
# Keep the original245b profile/adapter runnable for its complete primary. The
# corrected product has a distinct, pre-execution profile and adapter identity.
for before, after, occurrences in [
    ('docs/evidence/ticket131/source-profile.json', 'docs/evidence/ticket131/source-profile-talk-interruption.json', 1),
    ('docs/evidence/ticket131/verify-full.py', 'docs/evidence/ticket131/verify-full-talk-interruption.py', 2),
]:
    assert text.count(before) == occurrences
    text = text.replace(before, after, occurrences)
exec(compile(text, str(original) + ':separate-talk-interruption-profile', 'exec'),
    dict(__file__=__file__, __name__='__main__'))
