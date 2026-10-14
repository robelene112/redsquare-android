"""Shared isolated-emulator instrumentation, PID proof and failure-preserving cleanup."""
import re
import subprocess


class ProcessInstrumentation:
    def __init__(self, adb, evidence, test_class, runner):
        self.adb, self.evidence, self.test_class, self.runner = adb, evidence, test_class, runner

    def run(self, method, filename, check=True):
        try:
            result = self.adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                              self.test_class + '#' + method, self.runner, check=check, timeout=120)
            (self.evidence / filename).write_text(result.stdout + result.stderr)
            return result
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
            def decoded(value):
                return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else (value or '')
            (self.evidence / filename).write_text(decoded(error.stdout) + decoded(error.stderr))
            raise


def assert_process_gone(adb, package, pid):
    if package != 'nl.davidengelen.redsquare.dev' or not isinstance(pid, int) or pid <= 0:
        raise AssertionError('An isolated debug process is required')
    probe = adb('shell', 'command -v pidof >/dev/null 2>&1 || exit 2; '
                'pids=$(pidof ' + package + ' 2>/dev/null); rc=$?; '
                'if [ "$rc" -gt 1 ]; then exit 2; fi; '
                'case " $pids " in *" ' + str(pid) + ' "*) echo alive;; *) echo gone;; esac')
    if probe.stdout.strip() != 'gone':
        raise AssertionError('Original app process was not actually terminated')


def finish_process_fixture(instrument, evidence, package, capture_directory, primary):
    if package != 'nl.davidengelen.redsquare.dev' or not re.fullmatch(r'[a-z0-9-]+-evidence', capture_directory):
        raise AssertionError('Only public debug-fixture captures are allowed')
    try:
        with (evidence / 'images.tar').open('wb') as output:
            result = subprocess.run(['adb', 'exec-out', 'run-as', package, 'tar', '-C', 'cache', '-cf', '-',
                                     capture_directory], stdout=output, stderr=subprocess.PIPE, timeout=10)
        (evidence / 'capture-status.txt').write_text(str(result.returncode) + '\n' +
                                                   result.stderr.decode('utf-8', errors='replace'))
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        result = instrument('restoreOriginalEncryptedInstallationFiles', 'restored.txt')
        if 'OK (1 test)' not in result.stdout:
            raise AssertionError('Original installation restoration failed')
    except BaseException as restoration:
        if primary is None:
            raise
        primary.add_note('Installation restoration also failed: ' + type(restoration).__name__)
