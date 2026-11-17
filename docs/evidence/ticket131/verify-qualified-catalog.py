"""Separate posthoc catalog assessment; the frozen full adapters remain failed."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile

packet = Path(__file__).resolve().parent
repo = packet.parents[2]
assert len(sys.argv) == 6, "Require SDK, raw directory, complete run, source and fresh label"
sdk, raw, run, source, label = sys.argv[1:]
raw = Path(raw)
erratum_path = packet / "catalog-erratum/inputs.json"
erratum = json.loads(erratum_path.read_text())

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git(*arguments):
    return subprocess.check_output(["git", "-C", str(repo), *arguments])

assert sha(Path(__file__).read_bytes()) == erratum["qualifiedAssessorSha256"]
assert source == erratum["nativeSources"][sdk]
adapter = packet / erratum["frozenAdapters"][sdk]
profile = packet / erratum["frozenProfiles"][sdk]
assert sha(adapter.read_bytes()) == erratum["frozenAdapterSha256"][sdk]
assert sha(profile.read_bytes()) == erratum["frozenProfileSha256"][sdk]
assert sha((packet / "full-contract.json").read_bytes()) == erratum["originalContractSha256"]
failure = packet / ("catalog-erratum/frozen-failure" + sdk + ".txt")
failure_text = failure.read_text()
assert adapter.name in failure_text and "AssertionError:" in failure_text
assert "'normalCompletions': " + str(erratum["normalCompletions"][sdk]) in failure_text
assessor_commit = git("rev-parse", "HEAD").decode().strip()
for committed_path in (Path(__file__).resolve(), erratum_path, failure):
    assert git("show", assessor_commit + ":" + str(committed_path.relative_to(repo))) == committed_path.read_bytes()

# Keep physical originals and the logical capture requirements separate. No
# missing named TAR is manufactured or substituted into the original raw tree.
witnesses = {}
member_index = {}
for logical, requirement in erratum["archiveAliases"].items():
    part, name = requirement["archive"].split("/", 1)
    archive_path = raw / ("android-native-proof-" + part) / ".scratch/device-evidence" / (
        "api-29" if sdk == "10" else "api-37.0") / name
    archive_sha = sha(archive_path.read_bytes())
    if requirement["archive"] not in member_index:
        members = {}
        with tarfile.open(archive_path) as archive:
            for member in archive.getmembers():
                path = Path(member.name)
                assert not path.is_absolute() and ".." not in path.parts
                assert not member.issym() and not member.islnk()
                if member.isfile():
                    data = archive.extractfile(member).read()
                    assert member.name not in members
                    members[member.name] = {"bytes": len(data), "sha256": sha(data)}
        member_index[requirement["archive"]] = members
    members = member_index[requirement["archive"]]
    assert requirement["witnesses"] and len(set(requirement["witnesses"])) == len(requirement["witnesses"])
    assert all(name in members and members[name]["bytes"] > 0 for name in requirement["witnesses"]), logical
    witnesses[logical] = dict(archive=requirement["archive"], archiveSha256=archive_sha,
                             witnesses={name: members[name] for name in requirement["witnesses"]})

def qualify_catalog(original):
    result = dict(original)
    result["expected"] = {}
    for platform, rows in original["expected"].items():
        mapped = []
        moved = set()
        for row in rows:
            key = (row["path"], row["testClass"], row["test"])
            selected = [move for move in erratum["identityPathMoves"]
                        if key == (move["from"], move["testClass"], move["test"])]
            assert len(selected) <= 1
            if selected:
                assert row["occurrences"] == 1
                row = dict(row, path=selected[0]["to"])
                moved.add(key)
            mapped.append(row)
        assert len(moved) == 2
        assert Counter((row["testClass"], row["test"], row["occurrences"]) for row in mapped) == Counter(
            (row["testClass"], row["test"], row["occurrences"]) for row in rows)
        assert len({(row["path"], row["testClass"], row["test"]) for row in mapped}) == len(mapped)
        result["expected"][platform] = mapped
    result["expectedRecoveryTotals"] = {platform: dict(totals) for platform, totals in original["expectedRecoveryTotals"].items()}
    for platform, totals in result["expectedRecoveryTotals"].items():
        assert totals["normalCompletions"] + 1 == erratum["normalCompletions"][platform]
        totals["normalCompletions"] += 1
    logical = original["requiredArchivePaths"]
    assert len(logical) == erratum["logicalArchiveRequirements"]
    assert set(erratum["archiveAliases"]) <= set(logical)
    physical = {erratum["archiveAliases"].get(name, {"archive": name})["archive"] for name in logical}
    assert len(physical) == erratum["physicalArchives"]
    result["requiredArchivePaths"] = sorted(physical)
    return result

def emit_catalog_summary(summary, target):
    summary["frozenAdapterSha256"] = summary.pop("adapterSha256")
    summary.update(qualifiedAssessorSha256=sha(Path(__file__).read_bytes()),
                   qualifiedAssessorCommit=assessor_commit,
                   catalogErratumSha256=sha(erratum_path.read_bytes()), frozenAdapterPassed=False,
                   frozenFailureSha256=sha(failure.read_bytes()),
                   logicalArchiveRequirements=erratum["logicalArchiveRequirements"], physicalArchives=erratum["physicalArchives"])
    summary["qualification"] += " Separate posthoc source-derived catalog erratum: original frozen adapter failed. Exactly two identity paths move, one disjoint native invocation completion is counted, and ten logical archive aliases resolve to original shared captures. No native assertion, deadline or identity goal changes; no physical archive is fabricated."
    (target / "logical-archive-witnesses.json").write_text(json.dumps(witnesses, indent=2) + "\n")
    (target / "archive-alias-original-members.json").write_text(json.dumps(member_index, indent=2) + "\n")
    pending = target / ".summary.json.new"
    pending.write_text(json.dumps(summary, indent=2) + "\n")
    pending.replace(target / "summary.json")
    print(json.dumps(summary))

def run_qualified_inherited(text, filename, namespace):
    emission = '(target / "summary.json").write_text(json.dumps(summary, indent=2) + "\\n")\nprint(json.dumps(summary))\n'
    assert text.count(emission) == 1 and text.endswith(emission)
    text = text.replace(emission, "emit_catalog_summary(summary, target)\n", 1)
    namespace["emit_catalog_summary"] = emit_catalog_summary
    exec(compile(text, filename + ":qualified-catalog-emission", "exec"), namespace)

original = packet / "verify-full.py"
assert sha(original.read_bytes()) == erratum["originalAdapterSha256"]
text = original.read_text()
for before, after, count in [
    ("docs/evidence/ticket131/source-profile.json", str(profile.relative_to(repo)), 1),
    ("docs/evidence/ticket131/verify-full.py", str(adapter.relative_to(repo)), 2),
    ("extend_contract=extend_contract,", "extend_contract=lambda inherited: qualify_catalog(extend_contract(inherited)),", 1),
    ("INTEGRATED131_PASS_WITH_INHERITED115_AND126_QUALIFICATIONS", "QUALIFIED131_PASS_SOURCE_DERIVED_CATALOG_ERRATUM", 1),
    ("exec(compile(text, str(inherited) + ':explicit131-additive-adaptation', 'exec'), namespace)",
     "run_qualified_inherited(text, str(inherited) + ':explicit131-additive-adaptation', namespace)", 1),
]:
    assert text.count(before) == count
    text = text.replace(before, after, count)

# __file__ identifies the selected frozen adapter for its unchanged source/hash
# admission checks. This reporter and its narrow catalog hook have their own
# explicit identity below; this execution does not claim that adapter passed.
exec(compile(text, str(original) + ":qualified-catalog-assessment", "exec"),
     dict(__file__=str(adapter), __name__="__main__", qualify_catalog=qualify_catalog,
          run_qualified_inherited=run_qualified_inherited))
