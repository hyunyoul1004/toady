"""배포 워크플로의 서버 스크립트를 docker-compose·docker 스텁으로 실행해 롤백과 성공 기록을 검사한다."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

WORKFLOW = Path(__file__).resolve().parents[2] / '.github' / 'workflows' / 'deploy.yml'


def server_script():
    text = WORKFLOW.read_text()
    match = re.search(r'\n          script: \|\n((?:            .*\n|\n)+)', text)
    return ''.join(line[12:] + '\n' for line in match.group(1).splitlines())


class DeployWorkflowTest(unittest.TestCase):
    def run_deploy(self, previous_tag='old', fail_up_for='', check_fails_for='', env_text=None, fail_config=''):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            work = home / 'toady'
            (work / 'scripts').mkdir(parents=True)
            bin_dir = home / 'bin'
            bin_dir.mkdir()
            log = home / 'calls.log'
            stubs = {
                'docker-compose': '#!/bin/sh\necho "compose $IMAGE_TAG $*" >> "$CALL_LOG"\n'
                                  'case "$*" in *" up "*) [ "$IMAGE_TAG" != "$FAIL_UP_FOR" ] ;; *" config "*) [ -z "$FAIL_CONFIG" ] ;; esac\n',
                'docker': '#!/bin/sh\necho "docker $*" >> "$CALL_LOG"\n',
            }
            for name, source in stubs.items():
                (bin_dir / name).write_text(source)
                (bin_dir / name).chmod(0o755)
            check = '#!/bin/sh\necho "check $IMAGE_TAG" >> "$CALL_LOG"\n[ "$IMAGE_TAG" != "$CHECK_FAILS_FOR" ]\n'
            (work / 'scripts' / 'check-deployment.sh').write_text(check)
            (work / 'docker-compose.yml').write_text('old compose\n')
            # scp가 받아 둔 새 파일(배포 스크립트가 이전 파일을 보관한 뒤 교체한다).
            (work / '.incoming' / 'scripts').mkdir(parents=True)
            (work / '.incoming' / 'docker-compose.yml').write_text('new compose\n')
            (work / '.incoming' / 'scripts' / 'check-deployment.sh').write_text(check)
            if previous_tag:
                (work / '.deployed-image-tag').write_text(previous_tag + '\n')
            if env_text is not None:
                (work / '.env').write_text(env_text)
                (work / '.env').chmod(0o640)
            result = subprocess.run(['bash', '-c', server_script()], capture_output=True, text=True, timeout=20,
                                    env={**os.environ, 'HOME': str(home),
                                         'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
                                         'IMAGE_TAG': 'new', 'DOCKER_USERNAME': 'tester', 'CALL_LOG': str(log),
                                         'FAIL_UP_FOR': fail_up_for, 'CHECK_FAILS_FOR': check_fails_for, 'FAIL_CONFIG': fail_config})
            self.compose = (work / 'docker-compose.yml').read_text()
            env_file = work / '.env'
            return (result, log.read_text() if log.exists() else '',
                    (work / '.deployed-image-tag').read_text() if (work / '.deployed-image-tag').exists() else '',
                    env_file.read_text() if env_file.exists() else None,
                    oct(env_file.stat().st_mode & 0o777) if env_file.exists() else None,
                    sorted(p.name for p in work.iterdir()))

    def test_success_records_tag_atomically_and_keeps_env_mode(self):
        result, calls, tag, env, mode, files = self.run_deploy(env_text='OTHER=1\nIMAGE_TAG=old\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi toadyScheduler toadyReactWeb', calls)
        self.assertEqual('new\n', tag)
        self.assertEqual('OTHER=1\nIMAGE_TAG=new\n', env)
        self.assertEqual('0o640', mode)
        self.assertEqual(['.deployed-image-tag', '.env', '.incoming', '.rollback', 'docker-compose.yml', 'scripts'], files)
        self.assertEqual('new compose\n', self.compose)

    def test_up_failure_rolls_back_to_previous_tag(self):
        result, calls, tag, env, _, _ = self.run_deploy(fail_up_for='new', env_text='IMAGE_TAG=old\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('docker-compose up failed', result.stderr)
        self.assertIn('compose old -p toady up -d --no-deps', calls)
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual('old\n', tag)
        self.assertEqual('IMAGE_TAG=old\n', env)
        self.assertEqual('old compose\n', self.compose, '롤백하면 compose 파일도 이전 것으로 되돌린다')

    def test_invalid_compose_is_restored_without_touching_containers(self):
        result, calls, tag, _, _, _ = self.run_deploy(fail_config='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('docker-compose.yml is invalid', result.stderr)
        self.assertNotIn(' up ', calls)
        self.assertNotIn(' pull ', calls)
        self.assertEqual('old\n', tag)
        self.assertEqual('old compose\n', self.compose)

    def test_rollback_up_failure_is_reported(self):
        result, _, _, _, _, _ = self.run_deploy(fail_up_for='', check_fails_for='new')
        self.assertIn('ROLLBACK OK', result.stderr)
        result, _, _, _, _, _ = self.run_deploy(check_fails_for='new', fail_up_for='old')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ROLLBACK FAILED', result.stderr)

    def test_first_deploy_failure_is_not_rolled_back_and_records_nothing(self):
        result, _, tag, env, _, _ = self.run_deploy(previous_tag='', check_fails_for='new')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('no previous successful tag', result.stderr)
        self.assertEqual('', tag)
        self.assertIsNone(env)

    def test_first_success_creates_private_env(self):
        result, _, tag, env, mode, _ = self.run_deploy(previous_tag='')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('new\n', tag)
        self.assertEqual('IMAGE_TAG=new\n', env)
        self.assertEqual('0o600', mode)


if __name__ == '__main__':
    unittest.main()
