"""Shared host protocol for concrete private/group retained-worker recovery."""
import argparse
import json
from pathlib import Path
import subprocess
from process_check_utils import ProcessInstrumentation, assert_process_gone, finish_process_fixture

from uuid import UUID


def main(kind):
    if kind not in ('private', 'group'):
        raise ValueError('A concrete Chat process fixture is required')
    wake = {'private': '49', 'group': '74'}[kind] * 32
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    package = 'nl.davidengelen.redsquare.dev'
    runner = package + '.test/androidx.test.runner.AndroidJUnitRunner'
    checkpoint = f'cache/{kind}-push-process-checkpoint.json'


    def adb(*arguments, check=True, timeout=60):
        return subprocess.run(['adb', *arguments], capture_output=True, text=True, check=check, timeout=timeout)


    def read_checkpoint():
        return json.loads(adb('exec-out', 'run-as', package, 'cat', checkpoint).stdout)


    if not adb('get-serialno').stdout.strip().startswith('emulator-'):
        raise SystemExit('An isolated emulator is required')
    args.evidence.mkdir(parents=True, exist_ok=True)
    instrument = ProcessInstrumentation(adb, args.evidence,
                                        f'nl.davidengelen.redsquare.{kind.title()}PushProcessTest', runner).run
    primary = None
    persisted_work = None
    try:
        for method, phase, pid_field in [
            ('terminateWithAnActuallyRetainedPendingWorkerReceipt', 'pending', 'pid'),
            ('recoverTheSameWorkerDismissItsAlertAndTerminateAgain', 'dismissed', 'recoveredPid'),
        ]:
            result = instrument(method, phase + '-interrupted.txt', check=False)
            if 'FAILURES!!!' in result.stdout or 'Process crashed.' not in result.stdout:
                raise AssertionError('Chat receipt did not reach its deliberate ' + phase + ' interruption')
            row = read_checkpoint()
            if row['phase'] != phase or row['backend'] != 'owner' or row['wake'] != wake:
                raise AssertionError('Unexpected Chat receipt checkpoint')
            work_id = str(UUID(row['workId']))
            if persisted_work is None:
                persisted_work = work_id
            elif work_id != persisted_work:
                raise AssertionError('Recovery replaced the persisted worker UUID')
            if phase == 'dismissed' and row['expiredRequests'] != 0:
                raise AssertionError('Cached recovery contacted the expired gateway')
            pid = row[pid_field]
            if not isinstance(pid, int) or pid <= 0:
                raise AssertionError('Invalid original process ID')
            assert_process_gone(adb, package, pid)
            if phase == 'dismissed' and row['pid'] == pid:
                raise AssertionError('Recovery reused the original process')
            (args.evidence / (phase + '-checkpoint.json')).write_text(json.dumps(row, indent=2) + '\n')
        result = instrument('aDismissedReceiptRemainsSilentAfterAnotherColdRecovery', 'verified.txt')
        if 'OK (1 test)' not in result.stdout:
            raise AssertionError('Cold Chat receipt duplicate did not remain silent')
        final = read_checkpoint()
        if final['phase'] != 'verified' or len({final['pid'], final['recoveredPid'], final['finalPid']}) != 3:
            raise AssertionError('Chat receipt recovery did not use three distinct processes')
        if str(UUID(final['workId'])) != persisted_work or final['expiredRequests'] != 0:
            raise AssertionError('Cold recovery lost the original worker or contacted the expired gateway')
        (args.evidence / 'verified-checkpoint.json').write_text(json.dumps(final, indent=2) + '\n')
    except BaseException as failure:
        primary = failure
        try:
            (args.evidence / 'failure-checkpoint.json').write_text(json.dumps(read_checkpoint(), indent=2) + '\n')
        except BaseException as diagnostic:
            failure.add_note('Fixture checkpoint diagnostic unavailable: ' + type(diagnostic).__name__)
        raise
    finally:
        finish_process_fixture(instrument, args.evidence, package, f'{kind}-push-process-evidence', primary)

    (args.evidence / 'continuity.txt').write_text(
        f'PASS: same persisted worker UUID recovered retained {kind} history after actual process death; '
        'generic Android card physically dismissed; another process and duplicate wake remain silent; '
        'no Activity, exact older/new history and wallet/backend preserved; installation restoration passed. '
        'Recovery launches are host-initiated, not autonomous Google delivery.\n')
