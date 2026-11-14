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
sdk, raw, run, source, output_label = sys.argv[1:]
assert sdk in ("10", "17") and re.fullmatch(r"[0-9a-f]{40}", source)
assert re.fullmatch(r"[A-Za-z0-9._-]+", output_label)
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


def validate_runtime_preflight(root):
    folder = root / "runtime-preflight"
    accepted = json.loads((folder / "accepted.json").read_text())
    last = accepted["attempt"]
    assert type(last) is int and 1 <= last <= 3
    assert {p.name for p in folder.iterdir() if p.is_dir()} == {f"boot-{i}" for i in range(1, last + 1)}
    assert (folder / "accepted.json").read_bytes() == (folder / f"boot-{last}/result.json").read_bytes()
    records = []
    configuration_names = {"avd-config.ini", "emulator-capacity.txt", "system-image.txt",
        "system-image.properties", "system-build.txt", "device-abi.txt", "page-size.txt", "requested-page-size.txt"}
    for attempt in range(1, last + 1):
        boot = folder / f"boot-{attempt}"
        record = json.loads((boot / "result.json").read_text())
        assert record["attempt"] == attempt and record["maxAttempts"] == 3
        startup = (boot / "startup.log").read_bytes()
        emulator_log = (boot / "emulator.log").read_bytes()
        assert startup and emulator_log.startswith(startup)
        assert hashlib.sha256(startup).hexdigest() == record["emulatorStartupSha256"]
        assert set(record["configurationSha256"]) == configuration_names
        for name, checksum in record["configurationSha256"].items():
            assert digest(boot / name) == checksum
            assert (boot / name).read_bytes() == (folder / "boot-1" / name).read_bytes()
        raw_probe = (boot / "probe.log").read_bytes()
        assert hashlib.sha256(raw_probe).hexdigest() == record["probeSha256"]
        text = raw_probe.decode()
        values = re.findall(r"^INSTRUMENTATION_STATUS: heapProbe=(.+)\r?$", text, re.M)
        assert len(values) == 1 and json.loads(values[0]) == record["observation"]
        assert re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$", text, re.M) == ["-1"]
        assert "OK (1 test)" in text and "FAILURES!!!" not in text
        assert "INSTRUMENTATION_STATUS: class=nl.davidengelen.redsquare.RuntimeHeapProbe" in text
        assert "INSTRUMENTATION_STATUS: test=observesActualTargetProcessHeap" in text
        observed = record["observation"]
        assert observed["package"] == "nl.davidengelen.redsquare.dev" and observed["sdk"] == 29
        assert observed["hardware"] in ("ranchu", "goldfish")
        assert observed["pid"] > 0 and observed["parentPid"] > 0
        assert observed["actualBytes"] > 0 and observed["declaredBytes"] > 0
        properties = json.loads((boot / "properties.json").read_text())
        assert set(properties) == {"dalvik.vm.heapsize", "dalvik.vm.heapgrowthlimit", "ro.kernel.qemu.dalvik.vm.heapsize"}
        if attempt == last:
            assert record["result"] == "PASS" and observed["actualBytes"] == observed["declaredBytes"]
        else:
            assert record["result"] == "INVALID_HEAP" and observed["actualBytes"] != observed["declaredBytes"]
            termination = json.loads((boot / "termination.json").read_text())
            assert termination["fullyReaped"] and termination["deviceDisconnected"]
            assert termination["action"] == "cold-boot" and termination["emulatorPid"] > 0
            assert termination["emulatorLogBytes"] == len(emulator_log)
            assert termination["emulatorLogSha256"] == hashlib.sha256(emulator_log).hexdigest()
            serial = termination["emulatorSerial"]
            assert re.fullmatch(r"emulator-[0-9]+", serial)
            devices = (boot / "devices-after-shutdown.log").read_text().splitlines()
            assert devices[0] == "List of devices attached"
            assert not any(line.split() and line.split()[0] == serial for line in devices[1:])
            assert (boot / "shutdown.log").stat().st_size > 0
        records.append(record)
    return records


def native_action_bounds(data, text):
    tree = ET.fromstring(data)
    parents = {child: parent for parent in tree.iter() for child in parent}
    bounds = set()
    for node in tree.iter("node"):
        if node.get("text") != text or node.get("visible-to-user") != "true":
            continue
        while node.get("clickable") != "true" and node in parents:
            node = parents[node]
        assert node.get("clickable") == "true", ("No native action", text)
        value = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        assert len(value) == 4 and value[2] - value[0] >= 48 and value[3] - value[1] >= 48
        bounds.add(value)
    assert len(bounds) == 1, ("Require one visible native action", text, bounds)
    return next(iter(bounds))


contract_path = "docs/evidence/ticket115/full-contract.json"
contract_bytes = git("show", source + ":" + contract_path)
assert contract_bytes == (packet / "full-contract.json").read_bytes(), "Contract changed after execution"
assert git("show", source + ":docs/evidence/ticket115/verify-full.py") == Path(__file__).read_bytes(), "Verifier changed after execution"
contract = json.loads(contract_bytes)
trace_helper = packet / "trace-check.py"
assert digest(trace_helper) == contract["traceValidatorSha256"]
assert git("show", source + ":docs/evidence/ticket115/trace-check.py") == trace_helper.read_bytes(), "Trace validator changed after execution"
assert contract["creatorRefreshTrace"] == dict(mode="method", refreshes=20, completeMainMerges=20)
assert contract["runtimePreflight"] == dict(sdk=29, maxColdBoots=3, beforeApplicationTests=True,
    unchangedConfiguration=True, noHeapOrServiceChanges=True)
assert contract["runtimeProbe"] == dict(testClass="nl.davidengelen.redsquare.RuntimeHeapProbe",
    test="observesActualTargetProcessHeap", casesPerBoot=1, rawExtension=".log", separateFromAcceptanceCounts=True)
assert contract["ownerTalkUiPolish"] == dict(minimumTargetDp=48, controlGapDp=12, endCallGapDp=16,
    widthsDp=[320, 412], fontScales=[1, 1.3, 2], themes=["light", "dark"], separateInvitationCard=True,
    accurateHostRoomReady=True, originalApplicationIdentitiesUnchanged=True)
for path, checksum in contract["runtimePreflightSourceSha256"].items():
    assert digest(repo / path) == checksum
    assert git("show", source + ":" + path) == (repo / path).read_bytes()
baseline_bytes = git("show", source + ":docs/evidence/ticket103/full-contract.json")
assert hashlib.sha256(baseline_bytes).hexdigest() == contract["inheritedContractSha256"]
baseline = json.loads(baseline_bytes)
old_verifier = git("show", source + ":docs/evidence/ticket103/verify-full.py")
assert hashlib.sha256(old_verifier).hexdigest() == contract["inheritedCollectorSha256"]
assert old_verifier == (packet.parent / "ticket103/verify-full.py").read_bytes()
# Every accepted103 transfer validator is reused verbatim below, as are94's validators.
old_text = old_verifier.decode()
old_file_checks = old_text[old_text.index("fileshare_root = "):old_text.index("assert recovery_totals ==")]
assert old_file_checks in Path(__file__).read_text()
for key in ("identityParserSha256", "inheritedValidatorSha256", "allowedAssumption", "inheritedUnavailableArchives"):
    assert contract[key] == baseline[key], ("Changed inherited requirement", key)
prior104 = json.loads(git("show", source + ":docs/evidence/ticket104/full-contract.json"))
assert hashlib.sha256(git("show", source + ":docs/evidence/ticket104/full-contract.json")).hexdigest() == contract["inherited104ContractSha256"]
assert hashlib.sha256(git("show", source + ":docs/evidence/ticket104/verify-full.py")).hexdigest() == contract["inherited104CollectorSha256"]
module_identities = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["moduleIdentities"]}
for platform in ("10", "17"):
    inherited104 = {(r["path"],r["testClass"],r["test"]):r["occurrences"] for r in prior104["expected"][platform]}
    assert not inherited104.keys() & module_identities.keys()
    assert {(r["path"],r["testClass"],r["test"]):r["occurrences"] for r in contract["expected"][platform]} == inherited104 | module_identities
assert contract["moduleNormalCompletions"] == len(contract["moduleOrdinaryGoals"]) + 2 * len(contract["moduleProcessGoals"])
assert len(contract["moduleProcessGoals"]) == 5
new_identities = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["talkIdentities"]}
assert len(new_identities) == 24 and contract["talkClassCount"] == 15
assert contract["talkNormalCompletions"] == 16
for platform in ("10", "17"):
    previous = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in baseline["expected"][platform]}
    current = {(r["path"], r["testClass"], r["test"]): r["occurrences"] for r in contract["expected"][platform]}
    assert not (previous.keys() & new_identities.keys())
    assert current == previous | new_identities | module_identities
    assert contract["expectedRecoveryTotals"][platform] == {
        key: value + {"normalCompletions":16 + contract["moduleNormalCompletions"], "deliberateInterruptions":6, "continuityRecords":6}[key]
        for key,value in baseline["expectedRecoveryTotals"][platform].items()}
assert set(contract["requiredArchivePaths"]) == set(baseline["requiredArchivePaths"]) | set(contract["talkRequiredArchives"]) | set(contract["moduleRequiredArchives"])
assert len(contract["talkRequiredArchives"]) == 15
assert contract["talkTimingGoals"] == {
    "nativeClassSeconds":180, "processPhaseSeconds":120, "heldRelayMuteMillis":3000,
    "initialMuteMetadataMillis":5000, "directAuthenticationMillis":20000, "acceptedMediaMillis":25000,
    "cameraSwitchMillis":5000, "backgroundPauseMillis":5000, "nativeNotificationMillis":5000,
    "viewportRecreationMillis":15000, "unansweredCallObservationMillis":50000}
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
temporary = tempfile.TemporaryDirectory(prefix="vault-store115-visual-", dir=raw.parent)
view = Path(temporary.name)
actual = Counter()
archives = {}
originals = {}
assumptions = []
recovery_totals = {name: 0 for name in contract["expectedRecoveryTotals"][sdk]}
runtime_preflight = {}
for part in ("chat", "social", "wallet"):
    artifact = raw / ("android-native-proof-" + part)
    root = artifact / ".scratch/device-evidence" / ("api-29" if sdk == "10" else "api-37.0")
    for name in ("proof-source.txt", "proof-workflow.txt"):
        assert (artifact / ".scratch" / name).read_text().strip() == source
    for name in ("partition.txt", "partition-complete.txt"):
        assert (root / name).read_text().strip() == part
    if sdk == "10":
        runtime_preflight[part] = validate_runtime_preflight(root)
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

# Added module acceptance uses complete originals; no failed/partial run contributes.
wallet_root = raw / "android-native-proof-wallet/.scratch/device-evidence" / ("api-29" if sdk == "10" else "api-37.0")
for goal in contract["moduleProcessGoals"]:
    folder = wallet_root / goal["prefix"]
    pending = json.loads((folder / "pending-checkpoint.json").read_text())
    recovered = json.loads((folder / "recovered-checkpoint.json").read_text())
    assert pending["phase"] == "pending" and pending["backend"] == "owner" and pending["candidate"] and pending["pid"] > 0
    assert recovered == dict(pending,phase="recovered",recoveredPid=recovered["recoveredPid"])
    assert recovered["recoveredPid"] > 0 and recovered["recoveredPid"] != pending["pid"]
    assert goal["methods"]["interrupted.txt"] in (folder / "interrupted.txt").read_text()
    assert (folder / "continuity.txt").read_text() == goal["continuityText"]
    assert (folder / "capture-status.txt").read_text().splitlines()[0] == "0"
    for filename in ("recovered.txt", "restored.txt"):
        assert "OK (1 test)" in (folder / filename).read_text()
        assert "FAILURES!!!" not in (folder / filename).read_text()
module_view = view / "wallet/status-originals"
with tarfile.open(wallet_root / "store-purchase-evidence.tar") as archive:
    names = set(archive.getnames())
    for width in (320,412):
        for scale in (100,130,200):
            for theme in ("light","dark"):
                label=f"rental-{width}-{width}-font{scale}-{theme}"
                prefix="store-purchase-evidence/"+label
                configuration=json.loads(archive.extractfile(prefix+"-configuration.json").read())
                assert configuration["widthPixels"]==configuration["widthDp"]==width
                assert abs(configuration["fontScale"]-scale/100)<.01 and configuration["selectedAppearance"].lower()==theme
                for suffix in ("rental-terms","rental-cost-actions","vault-rights-actions"):
                    validate_png(archive.extractfile(prefix+"-"+suffix+".png").read())
    # All seven SAITO-priced methods and the distinct USDT-priced rental have
    # independent retained captures. Legacy method screenshots remain separate.
    for price,ticker in [("saito",t) for t in ("bnb","btc","eth","usdc","usdt","sol","trx")]+[("usdt","usdt")]:
        for suffix in ("native-instructions","exact-delivery"):
            validate_png(archive.extractfile(f"store-purchase-evidence/rental-{price}-priced-{ticker}-{suffix}.png").read())
with tarfile.open(wallet_root / "store-seller-evidence.tar") as archive:
    assert json.loads(archive.extractfile("store-seller-evidence/rental-keyboard-observation.json").read()) == dict(actualFocusedWindowIme=True,imeActionDidNotPublish=True,duration=3)
    validate_png(archive.extractfile("store-seller-evidence/rental-duration-real-ime.png").read())
with tarfile.open(wallet_root / "vault-reading-evidence.tar") as archive:
    refresh=json.loads(archive.extractfile("vault-reading-evidence/creator-refresh-observation.json").read())
    assert refresh["refreshes"]==20 and refresh["authenticatedMasterRetained"] is True
    assert refresh["traceMode"] == contract["creatorRefreshTrace"]["mode"]
    trace=module_view / "vault-reading-evidence/creator-discovery.trace"
    result=subprocess.run([sys.executable,str(trace_helper),str(trace),"--expect","absent","--mode","method"],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
    for scale in (0,1):
        observed=json.loads(archive.extractfile(f"vault-reading-evidence/scale-{scale}-window.json").read())
        assert observed["windowMotionScale"]==observed["systemMotionScale"]==scale
        for transition in ("open","close"):
            info=json.loads(archive.extractfile(f"vault-reading-evidence/rental-scale-{scale}-{transition}.json").read())
            frames=info["decodedOriginalFrames"];assert info["frameCount"]==len(frames)>=2
            assert all(a["presentationTimeUs"]<b["presentationTimeUs"] for a,b in zip(frames,frames[1:]))
            pixels=[archive.extractfile("vault-reading-evidence/"+f["file"]).read() for f in frames]
            assert len({hashlib.sha256(b).hexdigest() for b in pixels})>1
            for b in pixels:validate_png(b)
chat_root = raw / "android-native-proof-chat/.scratch/device-evidence" / ("api-29" if sdk=="10" else "api-37.0")
with tarfile.open(chat_root / "talk-fileshare-evidence.tar") as archive:
    mixed=json.loads(archive.extractfile("talk-fileshare-evidence/vault-store-shared-channel.json").read())
    for key in ("signedStoreOffer","authenticatedVaultFile","signedLikeDelivered","cancelledStoreRequest","lateStoreReplyRetired"):assert mixed[key] is True
    assert mixed["physicalConnections"]==1 and mixed["socialPosts"]==20
    group=json.loads(archive.extractfile("talk-fileshare-evidence/mixed-module-group-retained.json").read())
    assert group["groupMessageRetainedDuringTalkAndFile"] is True and group["authenticatedNodeConnections"]==1
with tarfile.open(chat_root / "vault-store-talkback-evidence.tar") as archive:
    for label in ("vault-keep","vault-offline-status","vault-export","rental-terms","rental-total","rental-confirm"):
        result=json.loads(archive.extractfile("vault-store-talkback-evidence/"+label+"-result.json").read())
        assert result["actualAccessibilityFocus"] is True and result["engineCompletions"] and result["physicalAudibility"] is False
        assert archive.extractfile("vault-store-talkback-evidence/"+label+"-speech.txt").read()

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
# New Talk outcomes are additive to the unchanged inherited checks above.
talk_root = fileshare_root
for part in ("chat", "social", "wallet"):
    camera_setup = raw / ("android-native-proof-" + part) / ".scratch/device-evidence" / ("api-29" if sdk == "10" else "api-37.0") / "emulator-cameras.txt"
    assert camera_setup.read_text().strip() == "-camera-back emulated -camera-front emulated"

def talk_archive(prefix):
    return tarfile.open(talk_root / (prefix + "-evidence.tar"))

def text_fields(data):
    return dict(line.split("=",1) for line in data.decode().splitlines() if "=" in line)

def required_fields(prefix, filename, fields):
    with talk_archive(prefix) as archive:
        observed = text_fields(archive.extractfile(prefix + "-evidence/" + filename).read())
    assert all(observed.get(key) == value for key,value in fields.items()), (prefix, filename, observed)
    return observed

with talk_archive("talk-routing") as archive:
    assert archive.extractfile("talk-routing-evidence/routing.txt").read() == b"Talk1 Chat1 ACK13/13"
with talk_archive("talk-capture") as archive:
    native_camera = archive.extractfile("talk-capture-evidence/camera.txt").read().decode()
    assert re.search(r"Actual emulator camera frames=([3-9]|[1-9][0-9]+); ended=ENDED; retainedTrack=false", native_camera)
permission = required_fields("talk-permission", "result.txt", {"actualAndroidDenial":"true", "noCaptureAfterDenial":"true", "explicitAllowedRetry":"true"})
assert int(permission["actualCameraFrames"]) >= 3
if sdk == "17":
    assert (talk_root / "talk-permission-original-flags.txt").read_text() == (talk_root / "talk-permission-restored-flags.txt").read_text()
else:
    # API29's actual permission UI runs; only its USER_SET shell-reset capability is qualified.
    assert "USER_FIXED" not in (talk_root / "talk-permission-original-flags.txt").read_text()
for prefix,suffix in (("talk-invitation","prejoin"),("talk-active","active")):
    with talk_archive(prefix) as archive:
        for width in (320,412):
            stem = ("invitation" if prefix == "talk-invitation" else "active") + f"-{width}-{width}"
            for scale in (100,130,200):
                for theme in ("light","dark"):
                    name = f"{prefix}-evidence/{stem}-font{scale}-{theme}"
                    config = json.loads(archive.extractfile(name + "-configuration.json").read())
                    assert config["widthPixels"] == config["widthDp"] == width
                    assert abs(config["fontScale"] - scale/100) < .01 and config["selectedAppearance"].lower() == theme
                    validate_png(archive.extractfile(name + "-" + suffix + ".png").read())
                    if prefix == "talk-active":
                        actions = [native_action_bounds(archive.extractfile(name + "-active.xml").read(), text)
                            for text in ("Mute", "Camera off", "Switch camera", "Audio output", "End call")]
                        assert all(actions[i + 1][0] - actions[i][2] >= 12 for i in range(3))
                        assert actions[4][1] - max(action[3] for action in actions[:4]) >= 16
                        validate_png(archive.extractfile(name + "-invitation.png").read())
                        invitation = archive.extractfile(name + "-invitation.xml").read()
                        share = native_action_bounds(invitation, "Share invitation")
                        native_texts = {node.get("text") for node in ET.fromstring(invitation).iter("node")}
                        copy = native_action_bounds(invitation, "Copy invitation" if "Copy invitation" in native_texts else "Invitation copied")
                        assert copy[0] - share[2] >= 12
        if prefix == "talk-invitation":
            for name in ("implicit-view-prejoin.png","busy-prejoin.png"):
                validate_png(archive.extractfile(prefix + "-evidence/" + name).read())
with talk_archive("talk-chat") as archive:
    for video in (False,True):
        value = json.loads(archive.extractfile("talk-chat-evidence/chat-" + str(video).lower() + ".json").read())
        assert value == {"video":video, "realRemotePcm":True, "speakerSelected":True, "nativeStartEnd":True, "draftRetained":True}
        for name in ("connected", "draft-return"):
            validate_png(archive.extractfile(f"talk-chat-evidence/{name}-{str(video).lower()}.png").read())
direct = required_fields("talk-direct", "direct.txt", {"legacyUnsignedInvitation":"true", "authenticatedBeforeRinging":"true", "noCaptureBeforeConsent":"true", "nativeCamera":"false", "ended":"true"})
assert int(direct["actualRemoteVideoFrames"]) >= 3
media = required_fields("talk-media", "media.txt", {"redPeerPixels":"true", "remotePcm":"true", "threePartyDecodedMesh":"true", "syntheticBrowserDeviceInputs":"true", "controlledNativePcmAfterAudioRecord":"true", "publicWrites":"0"})
assert int(media["realNativeVideoFrames"]) >= 5 and int(media["actualAudioRecordReads"]) > 10
with talk_archive("talk-media") as archive:
    background = json.loads(archive.extractfile("talk-media-evidence/background-audio.json").read())
    assert background["screenOff"] and background["notificationReturn"] and background["afterBytes"] > background["beforeBytes"] + 1000 and background["decodedRms"] > .01
    mesh = json.loads(archive.extractfile("talk-media-evidence/mesh.json").read())
    assert set(mesh) == {"alice","carol"}
    for peers in mesh.values():
        assert len(peers) == 2
        assert all(v["width"] >= 320 and v["nonBlack"] > 500 and v["rms"] > .01 for v in peers.values())
    validate_png(archive.extractfile("talk-media-evidence/three-party-native.png").read())
for roster in (False,True):
    required_fields("talk-controls", "controls-" + str(roster).lower() + ".txt", {"initialMutedMetadata":"true", "actualSilentRtp":"true", "nativeUnmuteReceivedPcm":"true", "relayActuallyBlocked":"true", "nativeMuteSilencesWithin3000ms":"true"})
required_fields("talk-lifecycle", "focus.txt", {"actualFocusTakeover":"true", "phase":"INTERRUPTED", "modeRestored":"true", "foregroundRemoved":"true"})
camera = required_fields("talk-camera-lifecycle", "camera.txt", {"startedAsVoice":"true", "videoEnabled":"true", "actualNativeCameraSwitch":"true", "backgroundCameraPaused":"true", "screenOffCallRetained":"true", "exactNotificationReturn":"true", "resumedCameraFrames":"true"})
assert int(camera["actualFrames"]) >= 6
with talk_archive("talk-camera-lifecycle") as archive:
    switch = json.loads(archive.extractfile("talk-camera-lifecycle-evidence/camera-switch.json").read())
    assert switch["cameraCount"] >= 2 and switch["switched"] and switch["beforeFront"] != switch["afterFront"] and switch["afterFrames"] >= switch["beforeFrames"] + 3
    if sdk == "17":
        service = archive.extractfile("talk-camera-lifecycle-evidence/video-services.txt").read().decode()
        assert any(int(m,0) & 0xc0 == 0xc0 for m in re.findall(r"(?:foregroundServiceType|types)=(0x[0-9a-fA-F]+|[0-9]+)",service))
required_fields("talk-ownership", "notifications.txt", {"distinctCallTokens":"true", "staleHangupIgnored":"true", "currentHangupReleasesCapture":"true"})
required_fields("talk-ownership", "authority.txt", {"walletWriteSilencesCapture":"true", "nodeWriteSilencesCapture":"true", "blockWriteSilencesCapture":"true", "obsoleteControlsIgnored":"true"})
required_fields("talk-dial", "outcomes.txt", {"nativeDecline":"true", "retiredRequestIgnored":"true", "remoteCancel":"true", "busyRejectsOtherCall":"true", "localCancel":"true", "remoteReject":"true", "realNoAnswerDeadline":"true", "audioCaptureReads":"0"})
with talk_archive("talk-fileshare") as archive:
    coexist = json.loads(archive.extractfile("talk-fileshare-evidence/coexistence.json").read())
    assert coexist == {"actualThreePartyTracks":True,"actualNativeFileSession":True,"fileBytes":150007,"exactFileBytes":True,"blockedDifferentPeer":True,"privateChatRetainedAfterCall":True,"wireInputs":"verified-native-codecs","transport":"pinned-upstream-Stun"}
for prefix in ("talk-chat","talk-direct","talk-media","talk-controls","talk-fileshare"):
    browser = talk_root / (prefix + "-browser")
    assert (browser / "chromium-version.txt").read_text().startswith("Google Chrome ")
    assert json.loads((browser / "devtools-version.json").read_text())["Browser"].startswith("Chrome/")
call_process = talk_root / "talk-process"
call_pending = json.loads((call_process / "pending-checkpoint.json").read_text())
call_recovered = json.loads((call_process / "recovered-checkpoint.json").read_text())
assert call_pending["phase"] == "pending" and call_pending["pid"] > 0 and call_pending["cameraFrames"] >= 3 and call_pending["token"]
assert call_recovered == dict(call_pending,phase="recovered",recoveredPid=call_recovered["recoveredPid"])
assert call_recovered["recoveredPid"] > 0 and call_recovered["recoveredPid"] != call_pending["pid"]
assert "test=terminateDuringActualCameraCaptureAndForegroundCall" in (call_process / "interrupted.txt").read_text()
assert "FAILURES!!!" not in (call_process / "interrupted.txt").read_text()
assert (call_process / "continuity.txt").read_text().startswith("PASS: actual process death during camera capture and foreground call")
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
target = packet / output_label
target.mkdir(exist_ok=False)
(target / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
(target / "identities.json").write_text(json.dumps(ids.rows(actual), indent=2) + "\n")
(target / "original-SHA256.json").write_text(json.dumps(originals, indent=2) + "\n")
(target / "archives.json").write_text(json.dumps(archives, indent=2) + "\n")
summary = {"result": "PASS", "sdk": sdk, "source": source, "run": run,
           "completedIdentities": actual.total(), "recoveryTotals": recovery_totals, "classes": len({key[1] for key in actual}),
           "assumptions": assumptions, "inheritedMissingFailureCaptures": qualified_missing, "originalArchives": len(archives), "rawDirectory": str(raw),
           "runtimePreflight": runtime_preflight,
           "separateProvisioningCompletions": sum(len(records) for records in runtime_preflight.values()),
           "qualification": "Complete primary run, all3partitions. Original raw artifacts retained externally. No partial assembly or physical/provider/store acceptance."}
(target / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary))
