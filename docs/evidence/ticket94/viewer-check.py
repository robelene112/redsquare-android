"""Validate native viewer artifacts in a complete run before accepting issue69."""
import json
import re
import xml.etree.ElementTree as ET


def validate_viewer_evidence(target, validate_png):
    social, chat = target / 'social', target / 'chat'
    for part, prefix in [(social, 'image-viewer'), (social, 'image-viewer-viewport'),
                         (social, 'image-viewer-motion'), (chat, 'image-viewer-talkback')]:
        assert (part / (prefix + '-ui-exit.txt')).read_text().strip() == '0'
        assert 'OK (1 test)' in (part / (prefix + '-ui.txt')).read_text()

    def captures(folder, names):
        for name in names:
            validate_png((folder / (name + '.png')).read_bytes())
            ET.fromstring((folder / (name + '.xml')).read_bytes())

    originals = social / 'status-originals'
    captures(originals / 'image-viewer-evidence', ['keyboard-zoom', 'keyboard-return'])
    folder = originals / 'image-viewer-viewport-evidence'
    def targets(label):
        record = json.loads((folder / (label + '-targets.json')).read_text())
        assert record['disjoint'] is True and record['minimumPixels'] >= 48
        assert set(record['targets']) == {'Close image', 'Previous image', 'Next image', 'Zoom in', 'Zoom out', 'Reset zoom', 'Move image'}
        rectangles = [list(map(int, value.split())) for value in record['targets'].values()]
        for index, (left, top, right, bottom) in enumerate(rectangles):
            assert right - left >= record['minimumPixels'] and bottom - top >= record['minimumPixels']
            assert all(right <= other[0] or other[2] <= left or bottom <= other[1] or other[3] <= top for other in rectangles[index + 1:])
    for scale in [100, 130, 200]:
        for theme in ['light', 'dark']:
            label = f'viewer-320-font{scale}-{theme}'
            config = json.loads((folder / (label + '-configuration.json')).read_text())
            assert config['widthDp'] == 320 and abs(config['fontScale'] - scale / 100) < .01
            captures(folder, [label + '-fit', label + '-zoom'])
            targets(label)
            if scale == 200:
                config = json.loads((folder / (label + '-landscape-configuration.json')).read_text())
                assert config['orientation'] == 2 and config['widthDp'] > 320 and abs(config['fontScale'] - 2) < .01
                captures(folder, [label + '-landscape-fit', label + '-landscape-zoom'])
                targets(label + '-landscape')
    restored = json.loads((folder / 'viewer-font-restored.json').read_text())
    assert abs(restored['expected'] - restored['observed']) < .01 and restored['expected'] == restored['originalScale']
    folder = originals / 'image-viewer-motion-evidence'
    captures(folder, ['pinch-zoom', 'pan-menu-result', 'image-change-0-final', 'image-change-1-final'])
    for scale in [0, 1]:
        result = json.loads((folder / f'image-change-{scale}.json').read_text())
        assert result['scale'] == scale and result['actualMotionScale'] == scale and result['finalBlueFrames'] > 0
        assert (result['intermediateBlueFadeFrames'] > 0 if scale else result['intermediateBlueFadeFrames'] == 0)
        assert result['frameCount'] == len(result['decodedOriginalFrames']) and result['frameCount'] >= 2
        for frame in result['decodedOriginalFrames']:
            validate_png((folder / frame['file']).read_bytes())
    folder = chat / 'status-originals/image-viewer-talkback-evidence'
    labels = ['origin', 'zoom-in', 'next', 'close', 'zoomed-image', 'reset', 'second-image']
    captures(folder, labels + ['returned-focus'])
    result = json.loads((folder / 'result.json').read_text())
    assert result['returnedToOrigin'] is True and result['physicalAudibility'] is False
    pid = str(result['serviceProcess'])
    assert pid.isdigit() and int(pid) > 0
    navigation = (folder / 'origin-navigation-speech.txt').read_text()
    assert completed(navigation, 'Image 1 of 2', pid)
    assert keyboard_reached_image(navigation, (folder / 'origin.xml').read_bytes(), pid)
    keyboard_steps = list(folder.glob('keyboard-step-*.txt'))
    assert 1 <= len(keyboard_steps) <= 16
    assert all(step.read_text().strip() == 'sent' for step in keyboard_steps)
    assert ET.fromstring((folder / 'keyboard-tool-preferences.xml').read_bytes()).tag == 'map'
    for label in labels:
        data = json.loads((folder / (label + '-result.json')).read_text())
        assert data['actualAccessibilityFocus'] is True and str(data['serviceProcess']) == pid
        assert data['physicalAudibility'] is False
        log = (folder / (label + '-speech.txt')).read_text()
        assert completed(log, data['expectedContext'], pid), (label, 'missing completed engine speech')
        if label == 'zoomed-image':
            assert completed(log, 'Zoom 150 percent', pid)
        if label == 'second-image':
            assert completed(log, 'Zoom 100 percent', pid)


def keyboard_reached_image(log, hierarchy, pid):
    """Bind real keyboard focus to the label owned by that exact focused subtree."""
    lines = log.splitlines()
    # Preserve the original direct-description observation.
    if any('content="Image 1 of 2"' in line and 'sourceAction=LOGICAL_NAVIGATION' in line
           and 'inputMode=INPUT_MODE_KEYBOARD' in line for line in lines):
        return True
    root = ET.fromstring(hierarchy)
    parents = {child: parent for parent in root.iter() for child in parent}
    bounds = set()
    for node in root.iter('node'):
        if node.get('content-desc') != 'Image 1 of 2':
            continue
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        if (node.get('clickable') == 'true' and node.get('enabled') == 'true'
                and node.get('visible-to-user') == 'true'
                and node.get('package') == 'nl.davidengelen.redsquare.dev'):
            rectangle = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
            if rectangle:
                left, top, right, bottom = rectangle.groups()
                bounds.add(f'Rect({left}, {top} - {right}, {bottom})')
    service = re.compile(r'\(\s*' + re.escape(pid) + r'\)')
    focus_event = re.compile(r'eventId=(type:EVENT_TYPE_ACCESSIBILITY subtype:TYPE_VIEW_ACCESSIBILITY_FOCUSED displayId:\d+ time:\d+)')
    for index, line in enumerate(lines):
        if not (service.search(line) and 'interpretation=AccessibilityFocused{' in line
                and 'sourceAction=LOGICAL_NAVIGATION' in line and 'inputMode=INPUT_MODE_KEYBOARD' in line):
            continue
        event = focus_event.search(line)
        parent = re.search(r'eventSourceNode=\S+@([0-9a-f]+);', line)
        rectangle = re.search(r'boundsInScreen: (Rect\([^)]+\))', line)
        if not (event and parent and rectangle and rectangle[1] in bounds
                and 'packageName: nl.davidengelen.redsquare.dev;' in line
                and 'clickable: true;' in line and 'visible: true;' in line):
            continue
        parent_id = str(int(parent[1], 16))
        described = False
        for following in lines[index + 1:]:
            if not service.search(following):
                continue
            next_event = focus_event.search(following)
            if next_event and next_event[1] != event[1]:
                break
            if (f'getAppendedTreeDescription: ({parent_id})' in following
                    and 'treeDescription={Image 1 of 2}' in following):
                described = True
            speech = re.search(r'Speaking fragment text="([^"]*)"', following)
            speech_event = re.search(r'\bevent=(type:EVENT_TYPE_ACCESSIBILITY subtype:TYPE_VIEW_ACCESSIBILITY_FOCUSED displayId:\d+ time:\d+)(?=\s|,|$)', following)
            if not (described and speech and speech[1] == 'Image 1 of 2'
                    and speech_event and speech_event[1] == event[1]):
                continue
            utterance = re.search(r'utteranceId=([^,\s]+)', following)
            if not utterance:
                continue
            callbacks = [candidate for candidate in lines if service.search(candidate)
                         and re.search(r'REDSQUARE_ENGINE_(?:START|AUDIO|DONE|ERROR|STOP) utteranceId='
                                       + re.escape(utterance[1]) + r'(?:\s|$)', candidate)]
            if completed('\n'.join([following] + callbacks), 'Image 1 of 2', pid):
                return True
    return False


def completed(log, text, pid):
    requests = [line for line in log.splitlines() if 'Speaking fragment text=' in line and text in line]
    for line in requests:
        match = re.search(r'utteranceId=([^,\s]+)', line)
        if not match:
            continue
        assert re.search(r'\(\s*' + re.escape(pid) + r'\)', line)
        events = []
        for callback in log.splitlines():
            event = re.search(r'REDSQUARE_ENGINE_(START|AUDIO|DONE|ERROR|STOP) utteranceId=([^\s]+)(?: bytes=([0-9]+))?', callback)
            if event and event[2] == match[1]:
                assert re.search(r'\(\s*' + re.escape(pid) + r'\)', callback)
                events.append((event[1], int(event[3] or 0)))
        kinds = {kind for kind, _ in events}
        if {'START', 'DONE'} <= kinds and not {'STOP', 'ERROR'} & kinds and any(kind == 'AUDIO' and size > 0 for kind, size in events):
            return True
    return False
