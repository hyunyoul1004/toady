"""배포 검증은 실제 Docker/네트워크 대신 격리된 명령 스텁으로 검사한다."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-deployment.sh'


class DeploymentReadinessTest(unittest.TestCase):
    def run_check(self, scheduler_fails):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stubs = {
                'curl': '''#!/bin/sh
case "$*" in
  *8082*)
    count=0
    [ ! -f "$REVIEW_STATE" ] || count=$(cat "$REVIEW_STATE")
    count=$((count+1))
    echo "$count" > "$REVIEW_STATE"
    [ "$FAIL_SCHEDULER" = 0 ] && [ "$count" -ge 3 ]
    ;;
  *) exit 0 ;;
esac
''',
                'sleep': '#!/bin/sh\nexit 0\n',
                'docker': '''#!/bin/sh
case "$*" in
  *State.Running*) echo true ;;
  *State.Restarting*) echo false ;;
esac
''',
            }
            for name, source in stubs.items():
                command = root / name
                command.write_text(source)
                command.chmod(0o755)
            state = root / 'attempts'
            result = subprocess.run(['bash', str(SCRIPT)], capture_output=True, text=True, timeout=10,
                                    env={**os.environ, 'PATH': str(root) + os.pathsep + os.environ['PATH'],
                                         'REVIEW_STATE': str(state), 'FAIL_SCHEDULER': str(int(scheduler_fails))})
            return result, int(state.read_text())

    def test_waits_until_scheduler_is_ready(self):
        result, attempts = self.run_check(False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(3, attempts)

    def test_fails_deployment_when_scheduler_never_becomes_ready(self):
        result, attempts = self.run_check(True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Scheduler readiness check failed', result.stderr)
        self.assertEqual(30, attempts)


if __name__ == '__main__':
    unittest.main()
