"""Exercise real retained keyboard traces and reject broken causal correlations."""
import importlib.util
import json
from pathlib import Path
import re
import unittest

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('viewer', root / 'viewer-check.py')
viewer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(viewer)


class KeyboardNavigationProofTest(unittest.TestCase):
    def test_real_service_navigation_and_unrelated_evidence(self):
        for sdk in ['10', '17']:
            folder = root / f'focused-viewerQ-{sdk}/image-viewer-talkback-evidence'
            log = (folder / 'origin-navigation-speech.txt').read_text()
            xml = (folder / 'origin.xml').read_text()
            pid = str(json.loads((folder / 'result.json').read_text())['serviceProcess'])
            with self.subTest(sdk=sdk, case='original'):
                self.assertTrue(viewer.keyboard_reached_image(log, xml, pid))
            mutations = {
                'different service': (log, xml, str(int(pid) + 1)),
                'not keyboard navigation': (log.replace('INPUT_MODE_KEYBOARD', 'INPUT_MODE_TOUCH'), xml, pid),
                'not logical navigation': (log.replace('sourceAction=LOGICAL_NAVIGATION', 'sourceAction=MANUAL_SCROLL'), xml, pid),
                'different focused parent': (re.sub(r'eventSourceNode=(\S+)@[0-9a-f]+;', r'eventSourceNode=\1@0;', log), xml, pid),
                'different aggregate label': (log.replace('treeDescription={Image 1 of 2}', 'treeDescription={Image 2 of 2}'), xml, pid),
                'different speech event': (re.sub(r'(Speaking fragment text=.*?event=type:EVENT_TYPE_ACCESSIBILITY subtype:TYPE_VIEW_ACCESSIBILITY_FOCUSED displayId:\d+ time:)\d+', r'\g<1>0', log), xml, pid),
                'speech timestamp prefix': (re.sub(r'(Speaking fragment text=.*?event=type:EVENT_TYPE_ACCESSIBILITY subtype:TYPE_VIEW_ACCESSIBILITY_FOCUSED displayId:\d+ time:\d+)', r'\g<1>0', log), xml, pid),
                'speech label prefix': (log.replace('Speaking fragment text="Image 1 of 2"', 'Speaking fragment text="Image 1 of 20"'), xml, pid),
                'missing start': ('\n'.join(line for line in log.splitlines() if 'REDSQUARE_ENGINE_START' not in line), xml, pid),
                'missing real audio': ('\n'.join(line for line in log.splitlines() if 'REDSQUARE_ENGINE_AUDIO' not in line), xml, pid),
                'missing completion': ('\n'.join(line for line in log.splitlines() if 'REDSQUARE_ENGINE_DONE' not in line), xml, pid),
                'different engine utterance': (re.sub(r'(REDSQUARE_ENGINE_\w+ utteranceId=)[^\s]+', r'\g<1>unrelated', log), xml, pid),
                'failed speech': ('\n'.join(line + ('\n' + line.replace('REDSQUARE_ENGINE_DONE', 'REDSQUARE_ENGINE_ERROR') if 'REDSQUARE_ENGINE_DONE' in line else '') for line in log.splitlines()), xml, pid),
                'stopped speech': ('\n'.join(line + ('\n' + line.replace('REDSQUARE_ENGINE_DONE', 'REDSQUARE_ENGINE_STOP') if 'REDSQUARE_ENGINE_DONE' in line else '') for line in log.splitlines()), xml, pid),
                'empty audio': (re.sub(r'(REDSQUARE_ENGINE_AUDIO utteranceId=\S+ bytes=)\d+', r'\g<1>0', log), xml, pid),
                'different subtree label': (log, xml.replace('content-desc="Image 1 of 2"', 'content-desc="Image 2 of 2"'), pid),
                'different native bounds': (log, re.sub(r'bounds="[^"]+"', 'bounds="[0,0][1,1]"', xml), pid),
            }
            for case, arguments in mutations.items():
                with self.subTest(sdk=sdk, case=case):
                    self.assertFalse(viewer.keyboard_reached_image(*arguments))


if __name__ == '__main__':
    unittest.main()
