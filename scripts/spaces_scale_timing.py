"""Collect raw OS frames, committed reaction frames and owned effects."""
import csv
import io
import json
import math
import re
import shlex
import subprocess
import time
from xml.etree import ElementTree

PROFILE = '---PROFILEDATA---'
TAG = 'SpacesScaleProof'


def frame_statistics(raw):
    since = int(re.search(r'Stats since: (\d+)ns', raw)[1])
    frames = []
    unavailable = []
    for section in raw.split(PROFILE)[1::2]:
        for row in csv.DictReader(io.StringIO(section.strip())):
            if row.get('Flags') != '0' or not row.get('FrameCompleted'):
                continue
            intended, deadline, completed = (int(row[k]) for k in ('IntendedVsync', 'FrameDeadline', 'FrameCompleted'))
            if intended < since:
                continue
            if not (0 < intended <= completed < 2**63 - 1 and intended < deadline < 2**63 - 1):
                unavailable.append(row)
                continue
            frames.append({
                'durationMs': (completed - intended) / 1e6,
                'budgetMs': (deadline - intended) / 1e6,
                'missedBudget': completed > deadline,
            })
    if not frames:
        raise RuntimeError('No completed framework UI frames were observed')
    total = int(re.search(r'Total frames rendered:\s*(\d+)', raw)[1])
    return frames, unavailable, max(0, total - len(frames) - len(unavailable))


def percentile(values, points):
    ordered = sorted(values)
    return {p: ordered[math.ceil(len(ordered) * p / 100) - 1] for p in points}


def measure(adb, evidence, package, endpoint, endpoints, manifest, contract, request):
    expected = contract['measurement']
    thresholds = contract['thresholds']
    if manifest['acceptedMessages'] != 100000 or manifest['acceptedFiles'] != 1000:
        raise RuntimeError('The full timing path requires the frozen dataset')

    def shell(*parts, timeout=30):
        return adb('shell', shlex.join([str(p) for p in parts]), timeout=timeout).decode()

    def reset_frames():
        shell('dumpsys', 'gfxinfo', package, 'reset')

    def frames(name):
        output = shell('dumpsys', 'gfxinfo', package, 'framestats')
        (evidence / f'{name}-framestats.txt').write_text(output)
        return frame_statistics(output)

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
    shell('am', 'force-stop', package)
    open_channel('hotLink', 'cold', hot)
    cold, cold_missing, cold_unretained = frames('cold')

    def trace_text():
        return adb('logcat', '-d', '-s', f'{TAG}:I', '*:S', timeout=15).decode()

    frame_measurements = []
    missing_warm = 0
    feedback = []
    anchor_failures = []
    trace_process = None
    trace_target = '/data/misc/perfetto-traces/spaces-scale.pftrace'
    config = '''
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 0 } }
data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
  atrace_categories: "gfx"
  atrace_categories: "view"
  atrace_apps: "nl.davidengelen.redsquare.performance"
} } }
data_sources { config { name: "android.heapprofd" target_buffer: 1 heapprofd_config {
  sampling_interval_bytes: 4096
  process_cmdline: "nl.davidengelen.redsquare.performance"
  continuous_dump_config { dump_interval_ms: 15000 }
} } }
duration_ms: 1500000
'''
    trace_config = evidence / 'perfetto-config.pbtxt'
    trace_config.write_text(config.strip() + '\n')
    adb('push', str(trace_config), '/data/local/tmp/spaces-scale.pbtxt')
    try:
        trace_pid = shell('perfetto', '--background', '--txt', '-c', '/data/local/tmp/spaces-scale.pbtxt',
                          '-o', trace_target).strip()
        if not trace_pid.isdecimal():
            raise RuntimeError('Perfetto did not expose its owned daemon PID')
        trace_process = int(trace_pid)
        open_channel('libraryLink', 'mixed-media-library', 'Reference image 9.png')
        mixed_media = view('mixed-media-rows', lambda nodes: [
            label(node) for node in nodes if re.search(r'Reference (image|audio|document) ', label(node))
        ])
        if len({next(part for part in ('image', 'audio', 'document') if f'Reference {part} ' in name)
                for name in mixed_media}) != 3:
            raise RuntimeError('The actual library viewport did not display all three signed media formats')
        (evidence / 'mixed-media-library.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        for trial in range(expected['expectedWarmups'] + expected['expectedWarmIterations']):
            name = 'warm-up' if trial == 0 else f'warm-{trial}'
            open_channel('hotLink', f'{name}-initial', hot)
            observed, unavailable, unretained = [], [], 0
            for swipe in range(1, 5):
                reset_frames()
                shell('input', 'swipe', 410, 620, 410, 1310, 350)
                time.sleep(.15)
                sample, invalid, unobserved = frames(f'{name}-swipe-{swipe}')
                observed.extend(sample)
                unavailable.extend(invalid)
                unretained += unobserved
            if trial:
                frame_measurements.extend(observed)
                missing_warm += len(unavailable) + unretained
            body = re.compile(r'General • Benchmark Space 001 • record \d+\.')
            anchor = view(f'{name}-anchor', lambda nodes: next((n for n in nodes if body.search(label(n))), None))
            identity, before = label(anchor), bounds(anchor)
            preceding_read_count = request('status')['readRequests']
            arrival = request('arrival')
            deadline = time.monotonic() + 35
            while request('status')['readRequests'] <= preceding_read_count:
                if time.monotonic() >= deadline:
                    raise RuntimeError(f'No live refresh after the independent arrival in {name}')
                time.sleep(.2)
            after = view(f'{name}-arrival', lambda nodes: next((n for n in nodes if label(n) == identity), None))
            (evidence / f'{name}-anchor.json').write_text(json.dumps({
                'text': identity, 'before': before, 'after': bounds(after), 'accepted': arrival,
            }, indent=2))
            if abs(before[1] - bounds(after)[1]) > 1:
                anchor_failures.append(name)
            reaction_button = view(f'{name}-reaction', lambda nodes: next(
                (n for n in nodes if label(n) == 'Add reaction' and 400 < bounds(n)[1] < 1550), None))
            tap(reaction_button)
            choice = view(f'{name}-emoji', lambda nodes: next((n for n in nodes if label(n) == '👍'), None))
            previous = len(re.findall(r'reaction-frame \d+ \d+ \d+', trace_text()))
            accepted_before = request('status')['acceptedReactions']
            tap(choice)
            deadline = time.monotonic() + 35
            while True:
                log = trace_text()
                observed_reactions = re.findall(r'reaction-frame (\d+) (\d+) (\d+)', log)
                (evidence / 'application-observations.txt').write_text(log)
                if len(observed_reactions) == previous + 1 and request('status')['acceptedReactions'] == accepted_before + 1:
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError(f'Signed native reaction or committed UI frame was absent in {name}')
                time.sleep(.2)
            _, start, end = map(int, observed_reactions[-1])
            if end < start:
                raise RuntimeError(f'Reaction frame preceded its action in {name}')
            if trial:
                feedback.append((end - start) / 1e6)
            (evidence / f'{name}-frames.json').write_text(json.dumps({
                'frames': observed, 'unavailable': unavailable, 'unretained': unretained,
                'reactionToCommittedFrameMs': (end - start) / 1e6,
            }, indent=2))
            open_channel('unrelatedLink', f'{name}-switch', arrival['text'])

        def lifecycle_samples():
            return [(int(at), visible == 'true', ticker == 'true', int(account), int(jobs))
                    for at, visible, ticker, account, jobs in
                    re.findall(r'foreground (\d+) (true|false) (true|false) (\d+) (\d+)', trace_text())]

        initial_samples = lifecycle_samples()
        mark = initial_samples[-1][0] if initial_samples else 0
        shell('input', 'keyevent', 'KEYCODE_HOME')

        def wait_stopped(after):
            deadline = time.monotonic() + 12
            while True:
                stopped = [row for row in lifecycle_samples() if row[0] > after and not row[1] and not row[2]]
                if stopped:
                    return stopped[-1]
                if time.monotonic() >= deadline:
                    raise RuntimeError('Android backgrounding did not stop the actual Spaces polling job')
                time.sleep(.15)

        baseline = wait_stopped(mark)
        profile = [baseline]
        (evidence / 'heap-before-lifecycle.txt').write_text(shell('dumpsys', 'meminfo', '-d', package))
        for cycle in range(expected['idleReturnSwitchCycles']):
            key = 'hotLink' if cycle % 2 == 0 else 'unrelatedLink'
            channel = 'Benchmark Space 001' if cycle % 2 == 0 else 'Benchmark Space 002'
            open_channel(key, f'lifecycle-{cycle + 1:03d}', channel)
            shell('input', 'keyevent', 'KEYCODE_HOME')
            profile.append(wait_stopped(profile[-1][0]))
            if cycle % 10 == 0 or cycle == expected['idleReturnSwitchCycles'] - 1:
                time.sleep(2)
                (evidence / f'heap-after-{cycle + 1:03d}.txt').write_text(shell('dumpsys', 'meminfo', '-d', package))
        (evidence / 'lifecycle.txt').write_text(trace_text())
        samples = [row for row in lifecycle_samples() if row[0] > baseline[0]]
        running = [row for row in samples if row[1] and row[2]]
        lifecycle_settled = profile[-1][3] == baseline[3] and profile[-1][4] <= baseline[4]
        (evidence / 'lifecycle-idle-jobs.json').write_text(json.dumps({
            'fields': ['elapsedRealtimeNanos', 'visible', 'polling', 'accountInstance', 'activeChildren'],
            'profiles': profile, 'sameAccountAtIdleBound': lifecycle_settled,
        }, indent=2))
        if len(running) < expected['idleReturnSwitchCycles'] or not lifecycle_settled:
            raise RuntimeError('Lifecycle did not return to the same account and initial idle job bound')

        durations = [frame['durationMs'] for frame in frame_measurements]
        missed = sum(frame['missedBudget'] for frame in frame_measurements) / len(frame_measurements)
        taps = percentile(feedback, thresholds['reportPercentiles'])
        result = {
            'reactionCommittedFrameMs': taps,
            'warmFrameDurationMs': percentile(durations, thresholds['reportPercentiles']),
            'actualBudgetMs': percentile([frame['budgetMs'] for frame in frame_measurements], thresholds['reportPercentiles']),
            'coldFrameDurationMs': percentile([frame['durationMs'] for frame in cold], thresholds['reportPercentiles']),
            'coldMissingFrames': len(cold_missing) + cold_unretained,
            'warmMissingFrames': missing_warm,
            'warmBudgetMissShare': missed,
            'anchorFailures': anchor_failures,
            'observedForegroundTransitions': len(running),
            'observedStoppedPollingTransitions': len(profile) - 1,
            'sameAccountAtIdleJobBound': lifecycle_settled,
            'thresholdsPassed': taps[95] <= thresholds['tapFeedbackP95MsMaximum']
                and max(durations + [frame['durationMs'] for frame in cold]) < thresholds['noFrameAtOrAboveMs']
                and missed <= thresholds['scriptedWarmScrollBudgetMissShareMaximum']
                and not anchor_failures and not cold_missing and not cold_unretained and not missing_warm,
        }
        (evidence / 'timing.json').write_text(json.dumps(result, indent=2) + '\n')
        if not result['thresholdsPassed']:
            raise RuntimeError('Observed native timings exceeded the frozen thresholds')
        return result
    finally:
        if trace_process is not None:
            subprocess.run(['adb', 'shell', 'kill', '-INT', str(trace_process)], check=False, timeout=10)
            time.sleep(2)
            subprocess.run(['adb', 'pull', trace_target, str(evidence / 'native-scale.pftrace')], check=True, timeout=60)
