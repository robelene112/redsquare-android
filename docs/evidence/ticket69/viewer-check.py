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
