"""Collect raw OS frames, committed reaction frames and owned effects."""
import csv
import hashlib
import io
import json
import math
import os
import re
import shlex
import subprocess
import sys
import tempfile
import time
import urllib.request
from xml.etree import ElementTree
from zipfile import ZipFile

PROFILE = '---PROFILEDATA---'
TAG = 'SpacesScaleProof'
PERFETTO_RELEASE = 'https://github.com/google/perfetto/releases/download/v59.1/linux-amd64.zip'
PERFETTO_SHA256 = '7deb8c2572c6ae7ec80cd719d388041079827b0c44a1659694d5d88faa4f501e'


def frame_statistics(raw, allow_empty=False):
    since = int(re.search(r'Stats since: (\d+)ns', raw)[1])
    frames = []
    unavailable = []
    for section in raw.split(PROFILE)[1::2]:
        for row in csv.DictReader(io.StringIO(section.strip())):
            if row.get('Flags') != '0' or not row.get('FrameCompleted'):
                continue
            intended, deadline, completed = (int(row[k]) for k in ('IntendedVsync', 'FrameDeadline', 'FrameCompleted'))
            # A queued vsync can precede reset even when the work is started and
            # counted by HWUI after reset. Its original deadline still applies.
            started = int(row.get('FrameStartTime') or intended)
            if max(intended, started) < since:
                continue
            if not (0 < intended <= completed < 2**63 - 1 and intended < deadline < 2**63 - 1):
                unavailable.append(row)
                continue
            frames.append({
                'timelineId': int(row.get('FrameTimelineVsyncId') or -1),
                'durationMs': (completed - intended) / 1e6,
                'budgetMs': (deadline - intended) / 1e6,
                'missedBudget': completed > deadline,
            })
    total = int(re.search(r'Total frames rendered:\s*(\d+)', raw)[1])
    if not frames and (not allow_empty or total != 0):
        raise RuntimeError('No completed framework UI frames were observed')
    return frames, unavailable, max(0, total - len(frames) - len(unavailable))


def framework_coverage(raw, frames, limit):
    total = int(re.search(r'Total frames rendered:\s*(\d+)', raw)[1])
    samples = re.search(r'^HISTOGRAM:(.*)$', raw, re.M)[1]
    histogram = [(int(lower), int(count)) for lower, count in re.findall(r'(\d+)ms=(\d+)', samples)]
    if len(histogram) < 100 or sum(count for _, count in histogram) != total:
        raise RuntimeError('HWUI did not account for every completed frame in its cumulative histogram')
    if limit not in {lower for lower, _ in histogram}:
        raise RuntimeError('HWUI lacks an exact lower bucket boundary for the frozen ceiling')
    return {
        'startNs': int(re.search(r'Stats since: (\d+)ns', raw)[1]),
        'sampleUptimeNs': int(re.search(r'Uptime: (\d+)', raw)[1]) * 1_000_000,
        'completedFrames': total,
        'unmatchedFrameworkFrames': max(0, total - len(frames)),
        'framesAtOrAboveCeiling': sum(count for lower, count in histogram if lower >= limit),
        'timelineIds': sorted({frame['timelineId'] for frame in frames if frame['timelineId'] > 0}),
    }


def percentile(values, points):
    ordered = sorted(values)
    return {p: ordered[math.ceil(len(ordered) * p / 100) - 1] for p in points}


def analyse_trace(evidence, stages):
    values = ',\n'.join(f"('{name}',{row['startNs']},{row['sampleUptimeNs']},{row['completedFrames']},'{json.dumps(row['timelineIds'])}')"
                       for name, row in stages.items())
    sql = f"""
WITH stages(name, start_ns, end_ns, framework_count, known_tokens) AS (VALUES {values}),
app AS (
  SELECT upid FROM process WHERE name = 'nl.davidengelen.redsquare.performance'
  UNION SELECT upid FROM actual_frame_timeline_slice
    WHERE layer_name GLOB '*nl.davidengelen.redsquare.performance/*'
), frames AS (
  SELECT a.ts,a.dur,a.surface_frame_token,(SELECT min(e.ts) FROM expected_frame_timeline_slice e
      WHERE e.upid=a.upid AND e.surface_frame_token=a.surface_frame_token
  ) AS expected
  FROM actual_frame_timeline_slice a WHERE a.upid IN app AND a.dur >= 0
), coverage AS (
  SELECT s.name, s.framework_count,
    (SELECT count(*) FROM frames f WHERE f.ts <= s.end_ns AND f.ts+f.dur >= s.start_ns) AS trace_count,
    json_array_length(s.known_tokens) AS known,
    (SELECT count(*) FROM json_each(s.known_tokens) k
      WHERE EXISTS(SELECT 1 FROM frames f WHERE f.surface_frame_token=k.value)) AS matched
  FROM stages s
)
SELECT hex(CAST(json_object(
  'completedFrames', (SELECT count(*) FROM frames),
  'lastFrameNs', (SELECT max(ts+dur) FROM frames),
  'maximumFrameMs', (SELECT max(max(dur,ts+dur-coalesce(expected,ts)))/1000000.0 FROM frames),
  'unmatchedExpectedOutsideFramework', (SELECT count(*) FROM frames f WHERE f.expected IS NULL AND NOT EXISTS(
    SELECT 1 FROM stages s WHERE s.framework_count>0 AND f.ts <= s.end_ns AND f.ts+f.dur >= s.start_ns)),
  'stageCoverage', (SELECT json_group_array(json_object('stage',name,'frameworkCount',framework_count,
     'traceCount',trace_count,'knownTokens',known,'matchedTokens',matched)) FROM coverage),
  'allocationProfiles', (SELECT count(DISTINCT ts) FROM heap_profile_allocation WHERE upid IN app),
  'diagnostics', (SELECT json_group_array(json_object('name',name,'index',idx,'value',value))
    FROM stats WHERE value > 0 AND (name GLOB 'heapprofd_*' OR name GLOB 'frame_timeline_*'
      OR name GLOB 'ftrace_*overrun*' OR name = 'traced_buf_chunks_overwritten'))
) AS BLOB)) AS result
"""
    (evidence / 'perfetto-validation.sql').write_text(sql.strip() + '\n')
    with urllib.request.urlopen(PERFETTO_RELEASE, timeout=60) as response:
        payload = response.read(16 * 1024 * 1024 + 1)
    if hashlib.sha256(payload).hexdigest() != PERFETTO_SHA256:
        raise RuntimeError('Perfetto release archive differs from its pinned checksum')
    with tempfile.TemporaryDirectory(prefix='spaces-perfetto-') as directory, ZipFile(io.BytesIO(payload)) as archive:
        member = next(name for name in archive.namelist() if name.endswith('/trace_processor_shell'))
        binary = archive.extract(member, directory)
        os.chmod(binary, 0o700)
        query = subprocess.run([binary, '--quiet', '--no-agent', 'query', '-f', str(evidence / 'perfetto-validation.sql'),
                                str(evidence / 'native-scale.pftrace')], capture_output=True, text=True, timeout=120)
    (evidence / 'perfetto-validation.log').write_text(query.stdout + query.stderr)
    query.check_returncode()
    (row,) = csv.DictReader(io.StringIO(query.stdout))
    return json.loads(bytes.fromhex(row['result']))


def measure(adb, evidence, package, endpoint, endpoints, manifest, contract, reference, request):
    expected = contract['measurement']
    thresholds = contract['thresholds']
    if manifest['acceptedMessages'] != 100000 or manifest['acceptedFiles'] != 1000:
        raise RuntimeError('The full timing path requires the frozen dataset')
    stages = {}

    def shell(*parts, timeout=30):
        return adb('shell', shlex.join([str(p) for p in parts]), timeout=timeout).decode()

    def reset_frames():
        shell('dumpsys', 'gfxinfo', package, 'reset')

    def frames(name, allow_empty=False, verify_complete=False):
        for attempt in range(1, 6 if verify_complete else 2):
            output = shell('dumpsys', 'gfxinfo', package, 'framestats')
            (evidence / f'{name}-framestats.txt').write_text(output)
            sample, invalid, unretained = frame_statistics(output, allow_empty)
            coverage = framework_coverage(output, sample, thresholds['noFrameAtOrAboveMs'])
            if not coverage['unmatchedFrameworkFrames'] or not verify_complete:
                break
            # The currently submitted frame can temporarily contain completion
            # fields from the previous ring entry. Preserve the raw snapshot and
            # sample after the renderer has time to complete it, without resetting.
            (evidence / f'{name}-unsettled-framestats-{attempt}.txt').write_text(output)
            time.sleep(.15)
        stages[name] = coverage
        return sample, invalid, unretained

    def view(name, until, seconds=45):
        deadline = time.monotonic() + seconds
        while True:
            shell('uiautomator', 'dump', '/sdcard/spaces-scale-view.xml', timeout=12)
            raw = adb('exec-out', 'cat', '/sdcard/spaces-scale-view.xml')
            (evidence / f'{name}.xml').write_bytes(raw)
            nodes = [n for n in ElementTree.fromstring(raw).iter('node') if n.get('package') == package]
            result = until(nodes)
            if result is not None:
                return result
            if time.monotonic() >= deadline:
                raise RuntimeError(f'Expected native state was absent in {name}')
            time.sleep(.15)

    def label(node):
        return node.get('text') or node.get('content-desc') or ''

    def bounds(node):
        return [int(n) for n in re.findall(r'\d+', node.get('bounds'))]

    def tap(node):
        left, top, right, bottom = bounds(node)
        if right <= left or bottom <= top:
            raise RuntimeError('Cannot tap a clipped or empty native bounds rectangle')
        shell('input', 'tap', (left + right) // 2, (top + bottom) // 2)

    def open_channel(key, name, expected_text):
        output = shell('am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', endpoints[key], '-p', package,
                       '--es', 'feedPeerUrl', endpoint, timeout=45)
        (evidence / f'{name}-launch.txt').write_text(output)
        return view(name, lambda nodes: next((n for n in nodes if expected_text in label(n)), None))

    hot = f"record {manifest['hotChannelMessages']}."
    (evidence / 'display.txt').write_text(shell('dumpsys', 'display'))
    (evidence / 'device-width.txt').write_text(shell('wm', 'size'))
    (evidence / 'device-density.txt').write_text(shell('wm', 'density'))

    def trace_text():
        return adb('logcat', '-d', '-s', f'{TAG}:I', '*:S', timeout=15).decode()

    def anchor_command(operation, *extras):
        return shell('am', 'start', '-W', '--activity-single-top', '-n',
                     f'{package}/nl.davidengelen.redsquare.MainActivity', '-a',
                     'nl.davidengelen.redsquare.performance.OBSERVE_ANCHOR', '--es', 'operation', operation, *extras)

    def device_elapsed_ns():
        seconds, milliseconds = shell('cat', '/proc/uptime').split()[0].split('.')
        return int(seconds) * 10**9 + int(milliseconds.ljust(9, '0'))

    def heap_sample(name):
        raw = shell('dumpsys', 'meminfo', '-d', package)
        (evidence / f'{name}.txt').write_text(raw)
        values = {label: int(amount.replace(',', '')) for label, amount in
                  re.findall(r'^\s*(Java Heap|Native Heap|TOTAL PSS):\s*([\d,]+)', raw, re.M)}
        if values.keys() != {'Java Heap', 'Native Heap', 'TOTAL PSS'}:
            raise RuntimeError('Android did not report the retained process heap')
        return values

    frame_measurements = []
    missing_warm = 0
    other_frames = []
    missing_other = []
    feedback = []
    anchor_failures = []
    trace_target = '/data/misc/perfetto-traces/spaces-scale.pftrace'
    config = '''
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 0 } }
data_sources { config { name: "linux.process_stats" target_buffer: 1 process_stats_config {
  scan_all_processes_on_start: true
  proc_stats_poll_ms: 5000
} } }
data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
  atrace_categories: "gfx"
  atrace_categories: "view"
  atrace_apps: "nl.davidengelen.redsquare.performance"
} } }
data_sources { config { name: "android.heapprofd" target_buffer: 1 heapprofd_config {
  sampling_interval_bytes: 32768
  shmem_size_bytes: 16777216
  process_cmdline: "nl.davidengelen.redsquare.performance"
  continuous_dump_config { dump_interval_ms: 15000 }
} } }
duration_ms: 1500000
write_into_file: true
file_write_period_ms: 5000
'''
    trace_config = evidence / 'perfetto-config.pbtxt'
    trace_config.write_text(config.strip() + '\n')
    # Read via stdin. Android's perfetto domain need not have access to /data/local/tmp.
    # Wait for the actual producers, and keep the command diagnostics even on failure.
    launch = subprocess.run(['adb', 'shell', 'perfetto', '--background-wait', '--txt',
                             '-c', '-', '-o', trace_target], input=trace_config.read_bytes(),
                            capture_output=True, timeout=45)
    (evidence / 'perfetto-start.txt').write_bytes(launch.stdout + launch.stderr)
    launch.check_returncode()
    trace_pid = launch.stdout.decode().strip()
    if not trace_pid.isdecimal():
        raise RuntimeError('Perfetto did not expose its owned daemon PID')
    trace_process = int(trace_pid)
    try:
        shell('am', 'force-stop', package)
        reset_frames()
        open_channel('hotLink', 'cold', hot)
        cold, cold_missing, cold_unretained = frames('cold')
        def other_sample(name, allow_empty=False):
            observed, unavailable, unretained = frames(name, allow_empty)
            other_frames.extend(observed)
            if unavailable or unretained:
                missing_other.append({'stage': name, 'invalid': len(unavailable), 'unretained': unretained})

        reset_frames()
        open_channel('libraryLink', 'mixed-media-library', 'Reference image 9.png')
        mixed_media = view('mixed-media-rows', lambda nodes: [
            label(node) for node in nodes if re.search(r'Reference (image|audio|document) ', label(node))
        ])
        if len({next(part for part in ('image', 'audio', 'document') if f'Reference {part} ' in name)
                for name in mixed_media}) != 3:
            raise RuntimeError('The actual library viewport did not display all three signed media formats')
        (evidence / 'mixed-media-library.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        other_sample('mixed-media')
        reaction_frames = re.compile(r'reaction-frame (\d+) (\d+) (\d+) (\d+)')
        feedback_frames = re.compile(r'reaction-feedback-frame (\d+) (\d+) (\d+) (\d+) (saving|selected)')
        geometry = re.compile(r'anchor-frame (\d+) (\d+) (\d+) (\d+) (true|false) (missing|-?[\d.]+ -?[\d.]+ -?[\d.]+ -?[\d.]+)')
        def anchor_frames(log, trial):
            return [(int(serial), int(started), int(committed), checkpoint == 'true',
                     None if bounds == 'missing' else tuple(map(float, bounds.split())))
                    for number, serial, started, committed, checkpoint, bounds in geometry.findall(log)
                    if int(number) == trial]
        body = re.compile(r'General • Benchmark Space 001 • record (\d+)\.')
        durable_reactions = []
        for trial in range(expected['expectedWarmups'] + expected['expectedWarmIterations']):
            name = 'warm-up' if trial == 0 else f'warm-{trial}'
            reset_frames()
            open_channel('hotLink', f'{name}-initial', hot)
            other_sample(f'{name}-initial')
            observed, unavailable, unretained = [], [], 0
            for swipe in range(1, 5):
                reset_frames()
                shell('input', 'swipe', 410, 620, 410, 1310, 350)
                time.sleep(.15)
                sample, invalid, unobserved = frames(f'{name}-swipe-{swipe}', verify_complete=True)
                observed.extend(sample)
                unavailable.extend(invalid)
                unretained += unobserved
            if trial:
                frame_measurements.extend(observed)
                missing_warm += sum(stages[f'{name}-swipe-{swipe}']['unmatchedFrameworkFrames'] for swipe in range(1, 5))
            anchor = view(f'{name}-anchor', lambda nodes: next((n for n in nodes
                if body.match(label(n)) and 200 < bounds(n)[1] < 1400 and hot not in label(n)), None))
            identity, before = label(anchor), bounds(anchor)
            record = body.match(identity)[1]
            if any(hot in label(node) for node in ElementTree.parse(evidence / f'{name}-anchor.xml').iter('node')):
                raise RuntimeError('The independently updated thread is still visible before its arrival')
            anchor_command('arm', '--ei', 'trial', trial, '--ei', 'record', record)
            anchor_deadline = time.monotonic() + 10
            while not anchor_frames(trace_text(), trial):
                if time.monotonic() >= anchor_deadline:
                    raise RuntimeError(f'The native anchor probe did not commit before {name}')
                time.sleep(.1)
            reset_frames()
            arrival_started = device_elapsed_ns()
            arrival = request('arrival')
            reply = arrival['offscreenReply']
            anchor_command('receipt', '--es', 'reply', reply)
            deadline = time.monotonic() + 35
            applied = None
            while applied is None:
                read = request('status').get('offscreenReplyRead')
                log = trace_text()
                received = re.search(rf'received (\d+) {re.escape(read)}\b', log) if read else None
                if received:
                    projected = re.search(rf'reply-projected (\d+) {re.escape(reply)}\b', log)
                    if projected and int(projected[1]) >= int(received[1]):
                        applied = int(projected[1])
                if time.monotonic() >= deadline:
                    raise RuntimeError(f'The native client did not apply the independently signed offscreen reply in {name}')
                if applied is None:
                    time.sleep(.2)
            after = view(f'{name}-arrival', lambda nodes: next((n for n in nodes if label(n) == identity), None))
            anchor_command('checkpoint')
            anchor_deadline = time.monotonic() + 10
            while True:
                log = trace_text()
                layouts = sorted(anchor_frames(log, trial))
                checkpoint = next((layout for layout in layouts if layout[3] and layout[2] >= applied), None)
                if checkpoint is not None:
                    break
                if time.monotonic() >= anchor_deadline:
                    raise RuntimeError(f'The native anchor probe did not commit after {name}')
                time.sleep(.1)
            anchor_command('stop')
            prior = [layout for layout in layouts if layout[2] <= arrival_started]
            # /proc/uptime is rounded down to 10ms. Use the already committed
            # probe frame determined before requesting the arrival when needed.
            baseline = prior[-1] if prior else layouts[0]
            commits = [layout for layout in layouts if baseline[0] < layout[0] <= checkpoint[0]]
            (evidence / f'{name}-anchor.json').write_text(json.dumps({
                'text': identity, 'before': before, 'after': bounds(after), 'accepted': arrival,
                'arrivalStartedNs': arrival_started, 'nativeReadRequest': read, 'replyProjectedNs': applied,
                'checkedThroughNs': checkpoint[2], 'baselineFrame': baseline, 'committedFrames': commits,
            }, indent=2))
            baseline_bounds = baseline[4]
            if (before != bounds(after) or baseline_bounds is None or not commits or
                    [frame[0] for frame in commits] != list(range(baseline[0] + 1, checkpoint[0] + 1)) or
                    any(frame[4] is None or any(abs(a - b) > 1 for a, b in zip(baseline_bounds, frame[4])) for frame in commits)):
                anchor_failures.append(name)
            other_sample(f'{name}-arrival', allow_empty=True)
            def visible_reaction(nodes):
                for node in nodes:
                    if label(node) != 'Add reaction' or node.get('clickable') != 'true':
                        continue
                    left, top, right, bottom = bounds(node)
                    # The frozen device is 2px/dp. A clipped row can expose an
                    # accessibility rectangle underneath the Earlier messages control.
                    if top >= before[3] and bottom <= 1550 and right - left >= 96 and bottom - top >= 96:
                        return node
                return None

            reaction_button = view(f'{name}-reaction', visible_reaction)
            reset_frames()
            tap(reaction_button)
            choice = view(f'{name}-emoji', lambda nodes: next((n for n in nodes if label(n) == '👍'), None))
            prior_log = trace_text()
            previous = len(reaction_frames.findall(prior_log))
            previous_feedback = len(feedback_frames.findall(prior_log))
            accepted_before = request('status')['acceptedReactions']
            tap(choice)
            deadline = time.monotonic() + 35
            while True:
                log = trace_text()
                observed_reactions = reaction_frames.findall(log)
                observed_feedback = feedback_frames.findall(log)
                (evidence / 'application-observations.txt').write_text(log)
                if (len(observed_reactions) == previous + 1 and len(observed_feedback) == previous_feedback + 1
                        and request('status')['acceptedReactions'] == accepted_before + 1):
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError(f'Signed native reaction or committed feedback and selected frames were absent in {name}')
                time.sleep(.2)
            sequence, start, handler, end = map(int, observed_reactions[-1])
            feedback_sequence, feedback_input, feedback_handler, feedback_end = map(int, observed_feedback[-1][:4])
            if (feedback_sequence, feedback_input, feedback_handler) != (sequence, start, handler) or not start <= handler <= feedback_end <= end:
                raise RuntimeError(f'Native reaction input, handler and frame clocks are inconsistent in {name}')
            if trial:
                feedback.append((feedback_end - start) / 1e6)
                durable_reactions.append((end - start) / 1e6)
            (evidence / f'{name}-frames.json').write_text(json.dumps({
                'frames': observed, 'unavailable': unavailable, 'unretained': unretained,
                'nativeInputNs': start, 'handlerNs': handler, 'feedbackFrameCommitNs': feedback_end, 'frameCommitNs': end,
                'feedbackState': observed_feedback[-1][4],
                'reactionToFeedbackFrameMs': (feedback_end - start) / 1e6,
                'reactionToCommittedFrameMs': (end - start) / 1e6,
            }, indent=2))
            other_sample(f'{name}-reaction')
            reset_frames()
            open_channel('unrelatedLink', f'{name}-switch', arrival['text'])
            other_sample(f'{name}-switch')

        started_cycles = re.compile(r'foreground (\d+) true true (-?\d+) (\d+) (\d+)')
        idle_samples = re.compile(r'idle (\d+) (-?\d+) (\d+) (\d+)')
        reset_frames()
        mark = device_elapsed_ns()
        shell('input', 'keyevent', 'KEYCODE_HOME')

        def wait_stopped(after):
            deadline = time.monotonic() + 12
            while True:
                stopped = [tuple(map(int, row)) for row in idle_samples.findall(trace_text()) if int(row[0]) > after]
                if stopped:
                    return stopped[-1]
                if time.monotonic() >= deadline:
                    raise RuntimeError('Android backgrounding did not stop the actual Spaces polling job')
                time.sleep(.15)

        baseline = wait_stopped(mark)
        profile = [baseline]
        heap = [{'cycle': 0, 'kilobytes': heap_sample('heap-before-lifecycle')}]
        running = []
        for cycle in range(expected['idleReturnSwitchCycles']):
            key = 'hotLink' if cycle % 2 == 0 else 'unrelatedLink'
            channel = 'Benchmark Space 001' if cycle % 2 == 0 else 'Benchmark Space 002'
            open_channel(key, f'lifecycle-{cycle + 1:03d}', channel)
            opened = [tuple(map(int, row)) for row in started_cycles.findall(trace_text()) if int(row[0]) > profile[-1][0]]
            if len(opened) != 1:
                raise RuntimeError(f'Expected one foreground subscription interval for lifecycle cycle {cycle + 1}')
            running.extend(opened)
            shell('input', 'keyevent', 'KEYCODE_HOME')
            profile.append(wait_stopped(profile[-1][0]))
            if cycle % 10 == 0 or cycle == expected['idleReturnSwitchCycles'] - 1:
                time.sleep(2)
                heap.append({'cycle': cycle + 1, 'kilobytes': heap_sample(f'heap-after-{cycle + 1:03d}')})
        (evidence / 'lifecycle.txt').write_text(trace_text())
        lifecycle_settled = all(account == baseline[1] and jobs <= baseline[2] and subscribers <= baseline[3]
                                for _, account, jobs, subscribers in profile)
        (evidence / 'lifecycle-idle-jobs.json').write_text(json.dumps({
            'fields': ['elapsedRealtimeNanos', 'accountInstance', 'activeChildren', 'stateSubscribers'],
            'profiles': profile, 'foreground': running, 'sameAccountAtIdleBound': lifecycle_settled,
        }, indent=2))
        (evidence / 'retained-heap-trend.json').write_text(json.dumps(heap, indent=2) + '\n')
        if len(running) < expected['idleReturnSwitchCycles'] or not lifecycle_settled:
            raise RuntimeError('Lifecycle did not return to the same account and initial idle job bound')
        other_sample('lifecycle')

        durations = [frame['durationMs'] for frame in frame_measurements]
        missed_frames = sum(frame['missedBudget'] for frame in frame_measurements)
        warm_total = sum(value['completedFrames'] for name, value in stages.items()
                         if re.fullmatch(r'warm-[1-5]-swipe-[1-4]', name))
        missed = missed_frames / len(frame_measurements)
        # Completion counts and the full duration histogram are cumulative HWUI
        # data, even when the individual-row ring is missing samples. Count every
        # such warm frame as a deadline miss for a conservative admission bound.
        missed_upper_bound = min(1, (missed_frames + missing_warm) / warm_total)
        taps = percentile(feedback, thresholds['reportPercentiles'])
        result = {
            'reactionFeedbackFrameMs': taps,
            'reactionCommittedFrameMs': percentile(durable_reactions, thresholds['reportPercentiles']),
            'warmFrameDurationMs': percentile(durations, thresholds['reportPercentiles']),
            'actualBudgetMs': percentile([frame['budgetMs'] for frame in frame_measurements], thresholds['reportPercentiles']),
            'coldFrameDurationMs': percentile([frame['durationMs'] for frame in cold], thresholds['reportPercentiles']),
            'coldMissingFrames': len(cold_missing) + cold_unretained,
            'warmMissingFrames': missing_warm,
            'otherFrameSamples': len(other_frames),
            'otherMissingFrames': missing_other,
            'warmBudgetMissShare': missed,
            'warmBudgetMissShareUpperBound': missed_upper_bound,
            'frameworkFramesByStage': stages,
            'anchorFailures': anchor_failures,
            'observedForegroundTransitions': len(running),
            'observedStoppedPollingTransitions': len(profile) - 1,
            'sameAccountAtIdleJobBound': lifecycle_settled,
            'sampleThresholdsPassed': taps[95] <= thresholds['tapFeedbackP95MsMaximum']
                and max(durations + [frame['durationMs'] for frame in cold + other_frames]) < thresholds['noFrameAtOrAboveMs']
                and missed_upper_bound <= thresholds['scriptedWarmScrollBudgetMissShareMaximum']
                and not any(row['framesAtOrAboveCeiling'] for row in stages.values())
                and not anchor_failures,
        }
        (evidence / 'timing.json').write_text(json.dumps(result, indent=2) + '\n')
    finally:
        original_failure = sys.exception()
        try:
            subprocess.run(['adb', 'shell', 'kill', '-INT', str(trace_process)], check=False, timeout=10)
            await_exit = f'while kill -0 {trace_process} 2>/dev/null; do sleep 0.2; done'
            subprocess.run(['adb', 'shell', await_exit], check=True, timeout=20)
            trace = evidence / 'native-scale.pftrace'
            adb('pull', trace_target, str(trace), timeout=60)
            if trace.stat().st_size == 0:
                raise RuntimeError('Perfetto returned an empty trace')
        except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
            (evidence / 'perfetto-finish-error.txt').write_text(str(failure) + '\n')
            if original_failure is None:
                raise
    summary = analyse_trace(evidence, stages)
    result['nativeTrace'] = summary
    comparable = {
        'reactionCommittedFrameP95Ms': result['reactionCommittedFrameMs'][95],
        'warmFrameP95Ms': result['warmFrameDurationMs'][95],
        'observedWarmBudgetMissShare': result['warmBudgetMissShare'],
        'maximumTraceFrameMs': summary['maximumFrameMs'],
    }
    changes = {name: (value / reference['metrics'][name] - 1) * 100 for name, value in comparable.items()}
    result['referenceComparison'] = {
        'run': reference['run'], 'source': reference['source'], 'timingSha256': reference['timingSha256'],
        'percentChange': changes,
        'investigate': [name for name, delta in changes.items() if delta > thresholds['baselineRegressionPercentInvestigateAbove']],
    }
    lost_profile = any(item['name'] in {'heapprofd_buffer_overran', 'heapprofd_client_error', 'traced_buf_chunks_overwritten'}
                       for item in summary['diagnostics'])
    trace_coverage = all(row['traceCount'] > 0 and row['knownTokens'] > 0 and row['knownTokens'] == row['matchedTokens']
                         for row in summary['stageCoverage'] if row['frameworkCount'] > 0)
    lost_frames = any(item['name'].startswith('ftrace_') and 'overrun' in item['name'] for item in summary['diagnostics'])
    result['thresholdsPassed'] = result['sampleThresholdsPassed'] and summary['completedFrames'] > 0 and (
        summary['maximumFrameMs'] < thresholds['noFrameAtOrAboveMs'] and summary['lastFrameNs'] >= running[-1][0] and
        summary['allocationProfiles'] >= 2 and not lost_profile and trace_coverage and not lost_frames and
        not summary['unmatchedExpectedOutsideFramework'])
    (evidence / 'timing.json').write_text(json.dumps(result, indent=2) + '\n')
    if not result['thresholdsPassed']:
        raise RuntimeError('The native evidence is incomplete or exceeds the frozen thresholds')
    return result
