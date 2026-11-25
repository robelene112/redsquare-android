"""Apply the frozen design configurations to an already-owned Android emulator."""
from itertools import combinations, product
import json
import re


def verify_pairwise(cases):
    axes = ('android', 'width', 'font', 'orientation', 'theme', 'motion', 'ime', 'navigation')
    domains = ((10, 17), (320, 360, 412, 600), (1.0, 1.3, 2.0), ('portrait', 'landscape'),
               ('light', 'dark'), ('normal', 'reduced'), ('closed', 'open'), ('gesture', 'buttons'))
    for left, right in combinations(range(len(axes)), 2):
        actual = {(case[axes[left]], case[axes[right]]) for case in cases}
        if actual != set(product(domains[left], domains[right])):
            raise RuntimeError(f'Missing viewport pairs for {axes[left]} and {axes[right]}')


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
        (self.folder / f'{name}-settings.json').write_text(json.dumps(result, indent=2))
        return result

    def set_overlay(self, operation, name):
        command = ['su', '0'] if self.sdk == 10 else []
        self.shell(*command, 'cmd', 'overlay', *operation, '--user', '0', name)

    def prepare(self, case):
        prefix = case['id']
        width = case['width'] * 2
        size = f'{width}x{round(width * 2.225)}'
        self.shell('wm', 'size', size)
        self.shell('wm', 'density', '320')
        self.shell('settings', 'put', 'system', 'font_scale', case['font'])
        self.shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
        rotation = '0' if case['orientation'] == 'portrait' else '1'
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
                float(actual['font_scale']) != case['font'] or actual['user_rotation'] != rotation or
                any(float(actual[name]) != float(motion) for name in self.motion) or
                actual['overlay'] != overlay or actual['night'] != night):
            raise RuntimeError(f'Applied Android viewport differs from the frozen case {prefix}')

    def restore(self):
        self.shell('wm', 'size', self.original['size']['override'] or 'reset')
        self.shell('wm', 'density', self.original['density']['override'] or 'reset')
        for namespace, key in self.settings:
            value = self.original[key]
            if value == 'null':
                self.shell('settings', 'delete', namespace, key)
            else:
                self.shell('settings', 'put', namespace, key, value)
        self.night(self.original['night'])
        if self.original['overlay']:
            self.set_overlay(('enable-exclusive', '--category'), self.original['overlay'])
        elif active := self.observe('before-navigation-reset')['overlay']:
            self.set_overlay(('disable',), active)
        if self.observe('restored') != self.original:
            raise RuntimeError('The original Android viewport settings were not restored')
