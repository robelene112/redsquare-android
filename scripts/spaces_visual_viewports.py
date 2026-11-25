"""Apply the frozen design configurations to an already-owned Android emulator."""
from itertools import combinations, product
import json
import re
import struct


def verify_pairwise(cases):
    axes = ('android', 'width', 'font', 'orientation', 'theme', 'motion', 'ime', 'navigation')
    domains = ((10, 17), (320, 360, 412, 600), (1.0, 1.3, 2.0), ('portrait', 'landscape'),
               ('light', 'dark'), ('normal', 'reduced'), ('closed', 'open'), ('gesture', 'buttons'))
    for left, right in combinations(range(len(axes)), 2):
        actual = {(case[axes[left]], case[axes[right]]) for case in cases}
        if actual != set(product(domains[left], domains[right])):
            raise RuntimeError(f'Missing viewport pairs for {axes[left]} and {axes[right]}')


def recording_size(case):
    """Scale the encoded video independently of the actual Android viewport."""
    return '544x1216' if case['orientation'] == 'portrait' else '1216x544'


def portrait_size(case):
    width = case['width'] * 2
    return width, round(width * 2.225)


def verify_native_screenshot(case, screenshot):
    portrait = portrait_size(case)
    expected = portrait if case['orientation'] == 'portrait' else portrait[::-1]
    if (len(screenshot) < 24 or not screenshot.startswith(b'\x89PNG\r\n\x1a\n') or
            struct.unpack_from('>II', screenshot, 16) != expected):
        raise RuntimeError(f'Actual app screenshot differs from {expected} for {case["id"]}')
    return expected


class VisualViewports:
    motion = ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')
    settings = (('system', 'font_scale'), ('system', 'accelerometer_rotation'), ('system', 'user_rotation'),
                *(('global', setting) for setting in motion))
    overlays = {'gesture': 'com.android.internal.systemui.navbar.gestural',
                'buttons': 'com.android.internal.systemui.navbar.threebutton'}

    def __init__(self, adb, sdk, viewport_override, folder):
        self.adb = adb
        self.sdk = sdk
        self.viewport_override = viewport_override
        self.folder = folder
        self.original = self.observe('original')

    def shell(self, *parts):
        return self.adb('shell', *map(str, parts)).stdout.decode().strip()

    def night(self, value=None):
        command = ['su', '0'] if self.sdk == 10 else []
        result = self.shell(*command, 'cmd', 'uimode', 'night', *([value] if value else []))
        match = re.fullmatch(r'Night mode: (yes|no|auto|custom_schedule|custom_bedtime)', result)
        if not match or value and match[1] != value:
            raise RuntimeError('Requested Android night mode was unavailable')
        return match[1]

    def observe(self, name):
        overlay = self.shell('cmd', 'overlay', 'list', '--user', '0')
        (self.folder / f'{name}-overlays.txt').write_text(overlay)
        selected = [line[4:] for line in overlay.splitlines() if line.startswith('[x] com.android.internal.systemui.navbar.')]
        if len(selected) > 1 or any(not re.fullmatch(r'com\.android\.internal\.systemui\.navbar\.[a-z0-9_]+', item)
                                    for item in selected):
            raise RuntimeError('Could not establish the active Android navigation overlay')
        values = {key: self.shell('settings', 'get', namespace, key) for namespace, key in self.settings}
        if any(not re.fullmatch(r'null|[0-9]+(?:\.[0-9]+)?', value) for value in values.values()):
            raise RuntimeError('Unexpected original Android emulator settings')
        result = dict(values, size=self.viewport_override('size'), density=self.viewport_override('density'),
                      night=self.night(), overlay=next(iter(selected), None))
        if self.sdk == 17:
            fixed_rotation = self.shell('wm', 'fixed-to-user-rotation')
            if fixed_rotation not in ('default', 'disabled', 'enabled', 'enabled_if_no_auto_rotation'):
                raise RuntimeError('Unexpected Android display rotation mode')
            result['fixedRotation'] = fixed_rotation
        (self.folder / f'{name}-settings.json').write_text(json.dumps(result, indent=2))
        return result

    def set_overlay(self, operation, name):
        command = ['su', '0'] if self.sdk == 10 else []
        self.shell(*command, 'cmd', 'overlay', *operation, '--user', '0', name)

    def prepare(self, case):
        prefix = case['id']
        width, height = portrait_size(case)
        size = f'{width}x{height}'
        self.shell('wm', 'size', size)
        self.shell('wm', 'density', '320')
        self.shell('settings', 'put', 'system', 'font_scale', case['font'])
        rotation = '0' if case['orientation'] == 'portrait' else '1'
        if self.sdk == 17:
            # Force the disposable display itself. A portrait launcher can
            # otherwise override a locked preference between two phases.
            self.shell('wm', 'fixed-to-user-rotation', 'enabled')
            self.shell('wm', 'user-rotation', 'lock', rotation)
        else:
            self.shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
            self.shell('settings', 'put', 'system', 'user_rotation', rotation)
        motion = '1' if case['motion'] == 'normal' else '0'
        for name in self.motion:
            self.shell('settings', 'put', 'global', name, motion)
        night = 'yes' if case['theme'] == 'dark' else 'no'
        self.night(night)
        overlay = self.overlays[case['navigation']]
        self.set_overlay(('enable-exclusive', '--category'), overlay)
        actual = self.observe(prefix)
        if (actual['size']['override'] != size or actual['density']['override'] != '320' or
                float(actual['font_scale']) != case['font'] or actual['user_rotation'] != rotation or actual['accelerometer_rotation'] != '0' or
                (self.sdk == 17 and actual['fixedRotation'] != 'enabled') or
                any(float(actual[name]) != float(motion) for name in self.motion) or
                actual['overlay'] != overlay or actual['night'] != night):
            raise RuntimeError(f'Applied Android viewport differs from the frozen case {prefix}')

    def restore(self):
        self.shell('wm', 'size', self.original['size']['override'] or 'reset')
        self.shell('wm', 'density', self.original['density']['override'] or 'reset')
        for namespace, key in self.settings:
            if key == 'font_scale':
                continue
            value = self.original[key]
            if value == 'null':
                self.shell('settings', 'delete', namespace, key)
            else:
                self.shell('settings', 'put', namespace, key, value)
        if self.sdk == 17:
            self.shell('wm', 'fixed-to-user-rotation', self.original['fixedRotation'])
        self.night(self.original['night'])
        if self.original['overlay']:
            self.set_overlay(('enable-exclusive', '--category'), self.original['overlay'])
        elif active := self.observe('before-navigation-reset')['overlay']:
            self.set_overlay(('disable',), active)
        # Android 10 can materialize its default font value during configuration updates.
        # Restore the original setting after changing display density, rotation and overlays.
        font = self.original['font_scale']
        if font == 'null':
            self.shell('settings', 'delete', 'system', 'font_scale')
        else:
            self.shell('settings', 'put', 'system', 'font_scale', font)
        restored = self.observe('restored')
        if self.sdk == 10 and font == 'null' and restored == dict(self.original, font_scale='1.0'):
            self.shell('settings', 'delete', 'system', 'font_scale')
            restored = self.observe('restored')
        if restored != self.original:
            raise RuntimeError('The original Android viewport settings were not restored')
