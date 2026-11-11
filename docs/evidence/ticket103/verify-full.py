"""Verify a complete immutable three-part SDK run against the pre-run contract."""
from collections import Counter
from datetime import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import ast
import tempfile
import struct
import zlib
import xml.etree.ElementTree as ET

assert len(sys.argv) == 6, "Require SDK, complete raw directory, run ID, exact source and fresh label"
sdk, raw, run, source, label = sys.argv[1:]
assert sdk in ("10", "17") and re.fullmatch(r"[0-9a-f]{40}", source)
assert re.fullmatch(r"[A-Za-z0-9._-]+", label)
packet = Path(__file__).resolve().parent
repo = packet.parents[2]
raw = Path(raw)


def git(*args):
    return subprocess.check_output(["git", "-C", str(repo), *args])


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def validate_png(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "PNG signature"
    offset, compressed, header = 8, bytearray(), None
    while offset < len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        chunk = data[offset + 8:offset + 8 + size]
        assert zlib.crc32(kind + chunk) & 0xffffffff == struct.unpack(">I", data[offset + 8 + size:offset + 12 + size])[0]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", chunk)
        if kind == b"IDAT":
            compressed.extend(chunk)
        offset += size + 12
    width, height, depth, color, compression, filtering, interlace = header
    assert width > 0 and height > 0 and depth == 8 and interlace == 0
    stride = width * {0: 1, 2: 3, 4: 2, 6: 4}[color] + 1
    pixels = zlib.decompress(compressed)
    assert len(pixels) == stride * height
    assert all(pixels[row * stride] in range(5) for row in range(height))


def validate_visual(name, data):
    if name.endswith(".png"):
        validate_png(data)
    elif name.endswith(".xml"):
        ET.fromstring(data)


contract_path = "docs/evidence/ticket103/full-contract.json"
contract_bytes = git("show", source + ":" + contract_path)
assert contract_bytes == (packet / "full-contract.json").read_bytes(), "Contract changed after execution"
assert git("show", source + ":docs/evidence/ticket103/verify-full.py") == Path(__file__).read_bytes(), "Verifier changed after execution"
contract = json.loads(contract_bytes)
initial_reference = contract["supersedesPreRunContract"]
initial_bytes = git("show", initial_reference["commit"] + ":" + contract_path)
assert hashlib.sha256(initial_bytes).hexdigest() == initial_reference["sha256"]
initial_contract = json.loads(initial_bytes)
for platform in ("10", "17"):
    old_rows = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in initial_contract["expected"][platform]}
    new_rows = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["expected"][platform]}
    assert len(new_rows) == len(old_rows) + 1 and all(new_rows.get(k) == v for k, v in old_rows.items())
    for key, value in initial_contract["expectedRecoveryTotals"][platform].items():
        assert contract["expectedRecoveryTotals"][platform][key] == value + (1 if key == "normalCompletions" else 0)
for key in ("requiredArchivePaths", "allowedAssumption", "inheritedUnavailableArchives", "identityParserSha256", "inheritedValidatorSha256"):
    assert contract[key] == initial_contract[key], ("Changed initial103 requirement", key)
baseline_bytes = git("show", source + ":docs/evidence/ticket102/full-contract.json")
assert hashlib.sha256(baseline_bytes).hexdigest() == contract["inheritedContractSha256"]
baseline = json.loads(baseline_bytes)
for key in ("identityParserSha256", "inheritedValidatorSha256", "allowedAssumption", "inheritedUnavailableArchives"):
    assert contract[key] == baseline[key], ("Changed inherited requirement", key)
for platform in ("10", "17"):
    previous = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in baseline["expected"][platform]}
    current = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["expected"][platform]}
    assert all(current.get(key) == value for key, value in previous.items())
    assert len(current) == len(previous) + 11
prior_archives = {p.replace("social/stack-link-evidence.tar", "chat/stack-link-evidence.tar") for p in baseline["requiredArchivePaths"]}
assert set(contract["requiredArchivePaths"]) == prior_archives | {"chat/fileshare-evidence.tar", "chat/fileshare-process/images.tar"}
metadata = json.loads(subprocess.check_output([
    "gh", "run", "view", run, "--repo", "robelene112/redsquare-android",
    "--json", "headSha,status,conclusion,createdAt,jobs,url",
]))
assert metadata["headSha"] == source
assert metadata["status"] == "completed" and metadata["conclusion"] == "success"
jobs = metadata["jobs"]
assert len(jobs) == 3 and all(job["conclusion"] == "success" for job in jobs)
assert {job["name"] for job in jobs} == {f"native-proof ({name})" for name in ("chat", "social", "wallet")}
frozen_at = git("log", "-1", "--format=%cI", source, "--", contract_path).decode().strip()
assert frozen_at and datetime.fromisoformat(frozen_at) < datetime.fromisoformat(metadata["createdAt"].replace("Z", "+00:00"))
parser = packet.parent / "ticket94/test-identities.py"
assert digest(parser) == contract["identityParserSha256"]
module = importlib.util.spec_from_file_location("frozen_identities", parser)
ids = importlib.util.module_from_spec(module)
module.loader.exec_module(ids)
expected = Counter({(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["expected"][sdk]})
# Reuse the accepted94 visual/motion/speech validators unchanged. Materialize
# their expected view temporarily outside Git; retain raw originals by hash.
prior = packet.parent / "ticket94"
for filename, checksum in contract["inheritedValidatorSha256"].items():
    path = prior / filename
    assert digest(path) == checksum
    assert git("show", source + ":docs/evidence/ticket94/" + filename) == path.read_bytes()
configured = json.loads((prior / "catalog.json").read_text())
legacy_errors = json.loads((prior / f"legacy-visual-errors{sdk}.json").read_text())
known_missing = {}
for partition in legacy_errors["partitions"].values():
    for error in partition["visualErrors"]:
        if error.get("prefix"):
            key = error["path"]
            if key.startswith("all/live-"):
                key = "wallet/" + key.removeprefix("all/")
            known_missing[key] = error["prefix"].encode()
qualified_missing = []
temporary = tempfile.TemporaryDirectory(prefix="fileshare103-visual-", dir=raw.parent)
view = Path(temporary.name)
actual = Counter()
archives = {}
originals = {}
assumptions = []
recovery_totals = {name: 0 for name in contract["expectedRecoveryTotals"][sdk]}
for part in ("chat", "social", "wallet"):
    artifact = raw / ("android-native-proof-" + part)
    root = artifact / ".scratch/device-evidence" / ("api-29" if sdk == "10" else "api-37.0")
    for name in ("proof-source.txt", "proof-workflow.txt"):
        assert (artifact / ".scratch" / name).read_text().strip() == source
    for name in ("partition.txt", "partition-complete.txt"):
        assert (root / name).read_text().strip() == part
    actual.update(ids.identities(root))
    destination = view / part
    destination.mkdir()
    # Preserve the inherited optional JVM report check without inventing
    # counts when CI supplies only its HTML summary.
    for path in sorted(artifact.glob("core/build/test-results/test/TEST-*.xml")):
        suite = ET.fromstring(path.read_bytes())
        assert int(suite.get("failures", "0")) == 0 and int(suite.get("errors", "0")) == 0
        originals[part + "/" + path.relative_to(artifact).as_posix()] = {
            "bytes": path.stat().st_size, "sha256": digest(path)}
    for pattern in ("core/build/reports/tests/test/index.html", "app/build/reports/lint-results-*.html"):
        for path in sorted(artifact.glob(pattern)):
            originals[part + "/" + path.relative_to(artifact).as_posix()] = {
                "bytes": path.stat().st_size, "sha256": digest(path)}
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        key = part + "/" + path.relative_to(root).as_posix()
        originals[key] = {"bytes": path.stat().st_size, "sha256": digest(path)}
        link = destination / path.relative_to(root)
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(path.resolve())
        if path.suffix in (".png", ".xml"):
            data = path.read_bytes()
            try:
                validate_visual(path.name, data)
            except Exception:
                assert known_missing.get(key) == data, key
                qualified_missing.append(key)
        if path.name.endswith("exit.txt"):
            assert path.read_text().strip() == "0", key
        # The shared parser has already byte-verified this exact aggregate
        # against its four individual logs. Count assumptions only once too.
        profile_aggregate = path.name == "profile-edit-ui.txt" and (root / "ProfileEditPreviewTest-ui.txt").exists()
        if path.suffix == ".txt" and not profile_aggregate:
            contents = path.read_text(errors="replace")
            recovery_totals["normalCompletions"] += contents.count("INSTRUMENTATION_CODE: -1")
            recovery_totals["deliberateInterruptions"] += contents.count("INSTRUMENTATION_RESULT: shortMsg=Process crashed.")
            if path.name == "continuity.txt":
                assert contents.startswith("PASS:"), key
                recovery_totals["continuityRecords"] += 1
            fields = {}
            for line in contents.splitlines():
                field = re.match(r"^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$", line)
                code = re.match(r"^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$", line)
                if field:
                    fields[field[1]] = field[2]
                elif code:
                    assert code[1] not in ("-1", "-2", "-3"), (key, fields.get("class"), fields.get("test"), code[1])
                    if code[1] == "-4":
                        observed = {"path": path.relative_to(root).as_posix(), "testClass": fields.get("class"), "test": fields.get("test")}
                        assert observed == contract["allowedAssumption"][sdk], observed
                        assumptions.append(observed)
                    fields = {}
        if path.suffix == ".tar":
            entry = dict(originals[key])
            try:
                with tarfile.open(path) as archive:
                    entry["members"] = len(archive.getmembers())
                    assert entry["members"] > 0, key
                    for member in archive.getmembers():
                        if member.isfile():
                            name = Path(member.name)
                            assert not name.is_absolute() and ".." not in name.parts
                            data = archive.extractfile(member).read()
                            validate_visual(member.name, data)
                            output = destination / "status-originals" / name
                            output.parent.mkdir(parents=True, exist_ok=True)
                            output.write_bytes(data)
            except tarfile.ReadError:
                assert key in contract["inheritedUnavailableArchives"], key
                entry["inheritedUnavailable"] = True
            archives[key] = entry
    if part == "social":
        for kind in ("post", "quote"):
            for phase in ("before-receipt", "after-receipt"):
                folder = root / (kind + "-process") / phase
                interrupted = (folder / "interrupted-instrumentation.txt").read_text()
                assert "test=terminateAfterOriginalPostTransmission" in interrupted
                assert "FAILURES!!!" not in interrupted
                continuity = (folder / "continuity.txt").read_text().splitlines()
                assert continuity[0].startswith("PASS: actual process death " + phase)
                assert re.fullmatch(r"[1-9A-HJ-NP-Za-km-z]{43,45}", continuity[1])
                assert re.fullmatch(r"[0-9a-f]{128}", continuity[2])
                assert (folder / "reconciled-without-rebroadcast.png").read_bytes().startswith(b"\x89PNG\r\n\x1a\n")

fileshare_root = raw / "android-native-proof-chat/.scratch/device-evidence" / ("api-29" if sdk == "10" else "api-37.0")
with tarfile.open(fileshare_root / "fileshare-evidence.tar") as archive:
    def file_evidence(name):
        return archive.extractfile("fileshare-evidence/" + name).read()
    for incoming, cancel, unknown in ((False, False, False), (True, False, False), (False, True, False), (False, False, True)):
        name = "result-" + "-".join(str(v).lower() for v in (incoming, cancel, unknown)) + ".json"
        result = json.loads(file_evidence(name))
        assert result["result"] == "PASS" and result["bytes"] == 150007
        assert (result["incoming"], result["cancel"], result["unknownSizeProvider"]) == (incoming, cancel, unknown)
        assert result["actualWebRtc"] and result["screenOffObserved"]
        assert result["sourcePin"] == ("9a4ca7e9442eb5e872168ced361ce741d7d139d7" if incoming else "e6c937c686495a37d8fc94ecc894189e23a453c3")
        expected_bytes = bytes((i * 13 + 7) % 251 for i in range(150007))
        assert result["sha256"] == hashlib.sha256(expected_bytes).hexdigest()
    for name, fields in (("notifications-disabled.json", ("actualAppOpDenied", "noTransferStarted", "draftRetained")),
                         ("preparation-cancelled.json", ("controlledPipeBlocked", "terminalCancelled", "serviceStopped", "stagingRemoved", "draftRetained"))):
        result = json.loads(file_evidence(name))
        assert result["result"] == "PASS" and all(result[field] is True for field in fields)
    large = json.loads(file_evidence("result-true-false-false-large.json"))
    assert large["result"] == "PASS" and large["incoming"] and not large["cancel"]
    assert large["bytes"] == 4194317 and large["actualWebRtc"] and large["screenOffObserved"]
    assert large["sourcePin"] == "9a4ca7e9442eb5e872168ced361ce741d7d139d7"
    assert large["sha256"] == hashlib.sha256(bytes((i * 13 + 7) % 251 for i in range(4194317))).hexdigest()
    for width in (320, 412):
        for scale in (100, 130, 200):
            for theme in ("light", "dark"):
                cell_label = f"viewport-{width}/fileshare-{width}-font{scale}-{theme}"
                cell = json.loads(file_evidence(cell_label + "-configuration.json"))
                assert cell["widthDp"] == cell["widthPixels"] == width
                assert abs(cell["fontScale"] - scale / 100) < 0.01 and cell["selectedAppearance"].lower() == theme
                validate_png(file_evidence(cell_label + "-saved.png"))
                tree = ET.fromstring(file_evidence(cell_label + "-saved.xml"))
                assert any(node.get("text") == "File saved" for node in tree.iter())
process = fileshare_root / "fileshare-process"
pending = json.loads((process / "pending-checkpoint.json").read_text())
recovered = json.loads((process / "recovered-checkpoint.json").read_text())
assert pending["phase"] == "pending" and pending["pid"] > 0
assert recovered == dict(pending, phase="recovered", recoveredPid=recovered["recoveredPid"])
assert recovered["recoveredPid"] > 0 and recovered["recoveredPid"] != pending["pid"]
assert "test=terminateWithAnEncryptedPartialFileAndUnsentChatDraft" in (process / "interrupted.txt").read_text()
assert "FAILURES!!!" not in (process / "interrupted.txt").read_text()
assert (process / "continuity.txt").read_text().startswith("PASS: actual process death from seeded encrypted partial state")
assert recovery_totals == contract["expectedRecoveryTotals"][sdk], recovery_totals
assert actual == expected, {"missing": ids.rows(expected - actual), "extra": ids.rows(actual - expected)}
assert set(archives) == set(contract["requiredArchivePaths"])
assert len(assumptions) <= (1 if sdk == "10" else 0)
tree = ast.parse((prior / "full-collector.py").read_text())
functions = ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef)], type_ignores=[])
namespace = {"Path": Path, "json": json, "re": re, "ET": ET, "sdk": sdk,
             "struct": struct, "zlib": zlib, "sha": lambda data: hashlib.sha256(data).hexdigest()}
exec(compile(functions, str(prior / "full-collector.py"), "exec"), namespace)
for name in ("status", "post", "composer", "conversation", "chat_entry", "profile", "search", "activity"):
    validator = namespace["validate_" + name + "_evidence"]
    if name == "status":
        validator(view, configured)
    else:
        validator(view)
for filename, function in (("viewer-check.py", "validate_viewer_evidence"), ("navigation-check.py", "validate")):
    specification = importlib.util.spec_from_file_location(filename, prior / filename)
    inherited = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(inherited)
    getattr(inherited, function)(view, validate_png)
temporary.cleanup()
target = packet / label
target.mkdir(exist_ok=False)
(target / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
(target / "identities.json").write_text(json.dumps(ids.rows(actual), indent=2) + "\n")
(target / "original-SHA256.json").write_text(json.dumps(originals, indent=2) + "\n")
(target / "archives.json").write_text(json.dumps(archives, indent=2) + "\n")
summary = {"result": "PASS", "sdk": sdk, "source": source, "run": run,
           "completedIdentities": actual.total(), "recoveryTotals": recovery_totals, "classes": len({key[1] for key in actual}),
           "assumptions": assumptions, "inheritedMissingFailureCaptures": qualified_missing, "originalArchives": len(archives), "rawDirectory": str(raw),
           "qualification": "Complete primary run, all3partitions. Original raw artifacts retained externally. No partial assembly or physical/provider/store acceptance."}
(target / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary))
