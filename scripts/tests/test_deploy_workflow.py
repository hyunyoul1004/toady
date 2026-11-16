"""배포 워크플로의 서버 스크립트를 docker-compose·docker 스텁으로 실행해 블루/그린 전환·되돌리기·성공 기록을 검사한다."""
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


def front(suffix):
    return 'map $host $toady_suffix { default "%s"; }\n' % suffix


# docker 스텁: 앞단 nginx의 색 파일(conf.d/toady-color.inc)은 FRONT_FILE로 흉내 낸다.
DOCKER_STUB = r'''#!/bin/sh
echo "docker $*" >> "$CALL_LOG"
case "$1 $2 $3" in
  "exec nginx cat") cat "$FRONT_FILE" 2>/dev/null ;;
  "exec nginx sh") sh -c "$5" sh "$7" "$FRONT_FILE" ;;
  "exec nginx nginx")
    case "$4" in
      -T) [ -n "$FRONT_UNPREPARED" ] || echo 'map $host $toady_suffix { default ""; }' ;;
      -t) [ -z "$FAIL_NGINX_T_FOR" ] || ! grep -q "\"$FAIL_NGINX_T_FOR\"" "$FRONT_FILE" ;;
    esac ;;
esac
'''

# 확인 스크립트 스텁: "컨테이너:앞단 확인 생략 여부"가 CHECK_FAILS_FOR와 같으면 실패한다.
CHECK_STUB = '''#!/bin/sh
echo "check ${API_CONTAINER:-toadyWebApi}:${SKIP_FRONT_CHECKS:-} port=${API_PORT:-8080}" >> "$CALL_LOG"
[ "${API_CONTAINER:-toadyWebApi}:${SKIP_FRONT_CHECKS:-}" != "$CHECK_FAILS_FOR" ]
'''


class DeployWorkflowTest(unittest.TestCase):
    def run_deploy(self, previous_tag='old', front_suffix='', fail_up_for='', check_fails_for='', env_text=None,
                   fail_config='', admin_env=None, front_unprepared='', fail_nginx_t_for=''):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            work = home / 'toady'
            (work / 'scripts').mkdir(parents=True)
            bin_dir = home / 'bin'
            bin_dir.mkdir()
            log = home / 'calls.log'
            front_file = home / 'toady-color.inc'
            if front_suffix is not None:
                front_file.write_text('# 배포 스크립트가 관리한다. "-green" 같은 주석은 무시한다.\n' + front(front_suffix))
            stubs = {
                'docker-compose': '#!/bin/sh\necho "compose $IMAGE_TAG $*" >> "$CALL_LOG"\n'
                                  'case "$*" in *" up "*) [ "$IMAGE_TAG" != "$FAIL_UP_FOR" ] ;; *" config "*) [ -z "$FAIL_CONFIG" ] ;; esac\n',
                'docker': DOCKER_STUB,
            }
            for name, source in stubs.items():
                (bin_dir / name).write_text(source)
                (bin_dir / name).chmod(0o755)
            (work / 'scripts' / 'check-deployment.sh').write_text(CHECK_STUB)
            (work / 'docker-compose.yml').write_text('old compose\n')
            # scp가 받아 둔 새 파일(배포 스크립트가 이전 파일을 보관한 뒤 교체한다).
            (work / '.incoming' / 'scripts').mkdir(parents=True)
            (work / '.incoming' / 'docker-compose.yml').write_text('new compose\n')
            (work / '.incoming' / 'scripts' / 'check-deployment.sh').write_text(CHECK_STUB)
            if previous_tag:
                (work / '.deployed-image-tag').write_text(previous_tag + '\n')
            if admin_env is not None:
                (work / 'toadyAdmin').mkdir()
                (work / 'toadyAdmin' / '.env.prod').write_text(admin_env)
            if env_text is not None:
                (work / '.env').write_text(env_text)
                (work / '.env').chmod(0o640)
            result = subprocess.run(['bash', '-c', server_script()], capture_output=True, text=True, timeout=20,
                                    env={**os.environ, 'HOME': str(home),
                                         'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
                                         'IMAGE_TAG': 'new', 'DOCKER_USERNAME': 'tester', 'CALL_LOG': str(log),
                                         'FRONT_FILE': str(front_file), 'DRAIN_SECONDS': '0',
                                         'FAIL_UP_FOR': fail_up_for, 'CHECK_FAILS_FOR': check_fails_for,
                                         'FAIL_CONFIG': fail_config, 'FRONT_UNPREPARED': front_unprepared,
                                         'FAIL_NGINX_T_FOR': fail_nginx_t_for})
            self.compose = (work / 'docker-compose.yml').read_text()
            self.front = front_file.read_text() if front_file.exists() else None
            env_file = work / '.env'
            return (result, log.read_text() if log.exists() else '',
                    (work / '.deployed-image-tag').read_text() if (work / '.deployed-image-tag').exists() else '',
                    env_file.read_text() if env_file.exists() else None,
                    oct(env_file.stat().st_mode & 0o777) if env_file.exists() else None,
                    sorted(p.name for p in work.iterdir()))

    def assert_before(self, calls, first, second):
        self.assertIn(first, calls)
        self.assertIn(second, calls)
        self.assertLess(calls.index(first), calls.index(second), f'{first!r} must come before {second!r}')

    def test_first_blue_green_deploy_switches_from_legacy_names_to_blue(self):
        result, calls, tag, env, mode, files = self.run_deploy(env_text='OTHER=1\nIMAGE_TAG=old\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('pull toadyWebApi-blue toadyReactWeb-blue toadyScheduler', calls)
        self.assertIn('compose new -p toady up -d --no-deps toadyWebApi-blue toadyReactWeb-blue\n', calls)
        self.assertIn('compose new -p toady up -d --no-deps toadyScheduler', calls)
        # 새 색이 준비된 뒤에만 앞단을 바꾸고, 앞단을 거친 확인이 끝난 뒤에야 이전(블루/그린 이전 이름) 컨테이너를 내린다.
        self.assert_before(calls, 'check toadyWebApi-blue:1 port=18080', 'docker exec nginx nginx -s reload')
        self.assert_before(calls, 'docker exec nginx nginx -s reload', 'check toadyWebApi-blue: port=18080')
        self.assert_before(calls, 'check toadyWebApi-blue: port=18080', 'docker stop -t 30 toadyWebApi\n')
        self.assertIn('docker rm -f toadyReactWeb\n', calls)
        self.assertNotIn('stop -t 30 toadyWebApi-blue', calls)
        self.assertEqual(front('-blue'), self.front)
        self.assertEqual('new\n', tag)
        self.assertEqual('OTHER=1\nIMAGE_TAG=new\n', env)
        self.assertEqual('0o640', mode)
        self.assertEqual(['.deployed-image-tag', '.env', '.incoming', '.rollback', 'docker-compose.yml', 'scripts', 'toadyAdmin'], files)
        self.assertEqual('new compose\n', self.compose)

    def test_alternates_between_blue_and_green(self):
        result, calls, *_ = self.run_deploy(front_suffix='-blue')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi-green toadyReactWeb-green\n', calls)
        self.assertIn('check toadyWebApi-green: port=28080', calls)
        self.assertIn('docker stop -t 30 toadyWebApi-blue', calls)
        self.assertNotIn('stop -t 30 toadyWebApi-green', calls)
        self.assertEqual(front('-green'), self.front)
        result, calls, *_ = self.run_deploy(front_suffix='-green')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi-blue toadyReactWeb-blue\n', calls)
        self.assertIn('docker stop -t 30 toadyWebApi-green', calls)
        self.assertEqual(front('-blue'), self.front)

    def test_admin_site_joins_the_deploy_only_after_its_env_file_is_filled(self):
        # 설정 파일이 없으면 빈 파일만 만들어 compose 검사를 통과시키고, 관리자 컨테이너는 띄우지 않는다.
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn('toadyAdmin-blue', calls.replace('stop -t 30 toadyAdmin-blue', '').replace('rm -f toadyAdmin-blue', ''))
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', admin_env='ADMIN_PASSWORD=x\n', front_suffix='-blue')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi-green toadyReactWeb-green toadyAdmin-green', calls)
        self.assertIn('docker stop -t 30 toadyAdmin-blue', calls)

    def test_unprepared_front_nginx_stops_before_touching_containers(self):
        result, calls, tag, _, _, _ = self.run_deploy(front_unprepared='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('front nginx is not prepared for blue/green', result.stderr)
        self.assertNotIn(' up ', calls)
        self.assertNotIn(' pull ', calls)
        self.assertNotIn('docker stop', calls)
        self.assertEqual('old\n', tag)
        self.assertEqual('old compose\n', self.compose)

    def test_invalid_compose_is_restored_without_touching_containers(self):
        result, calls, tag, _, _, _ = self.run_deploy(fail_config='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('docker-compose.yml is invalid', result.stderr)
        self.assertNotIn(' up ', calls)
        self.assertNotIn(' pull ', calls)
        self.assertEqual('old\n', tag)
        self.assertEqual('old compose\n', self.compose)

    def test_readiness_failure_never_switches_front_and_keeps_old_stack(self):
        result, calls, tag, env, _, _ = self.run_deploy(front_suffix='-blue', check_fails_for='toadyWebApi-green:1',
                                                        env_text='IMAGE_TAG=old\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('readiness checks failed before switching', result.stderr)
        self.assertNotIn('nginx -s reload', calls)
        self.assertEqual(front('-blue'), self.front.split('\n', 1)[1])
        self.assertIn('docker rm -f toadyWebApi-green', calls)
        self.assertNotIn('toadyWebApi-blue', calls.replace('check toadyWebApi-blue', ''))
        # 스케줄러는 제자리에서 바꿨으므로 이전 태그로 되돌리고, 이전 색을 다시 확인한다.
        self.assertIn('compose old -p toady up -d --no-deps toadyScheduler', calls)
        self.assertIn('check toadyWebApi-blue: port=18080', calls)
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual('old\n', tag)
        self.assertEqual('IMAGE_TAG=old\n', env)
        self.assertEqual('old compose\n', self.compose, '롤백하면 compose 파일도 이전 것으로 되돌린다')

    def test_check_failure_after_switch_switches_front_back(self):
        result, calls, tag, _, _, _ = self.run_deploy(front_suffix='-green', check_fails_for='toadyWebApi-blue:')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('deployment checks failed', result.stderr)
        self.assertEqual(2, calls.count('nginx -s reload'))
        self.assertEqual(front('-green'), self.front)
        self.assert_before(calls, 'nginx -s reload', 'docker rm -f toadyWebApi-blue')
        self.assertNotIn('stop -t 30 toadyWebApi-green', calls)
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual('old\n', tag)

    def test_first_switch_failure_rolls_back_to_legacy_names(self):
        result, calls, *_ = self.run_deploy(front_suffix='', check_fails_for='toadyWebApi-blue:')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(front(''), self.front)
        self.assertIn('check toadyWebApi: port=8080', calls)
        self.assertNotIn('stop -t 30 toadyWebApi\n', calls)
        self.assertIn('ROLLBACK OK', result.stderr)

    def test_rejected_switch_keeps_old_stack_serving(self):
        # 새 색으로 바꾼 설정이 nginx -t를 통과하지 못하면 reload하지 않고 이전 색으로 파일을 되돌린다.
        result, calls, *_ = self.run_deploy(front_suffix='-blue', fail_nginx_t_for='-green')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('front nginx switch failed', result.stderr)
        self.assertEqual(1, calls.count('nginx -s reload'))
        self.assertEqual(front('-blue'), self.front)
        self.assertIn('docker rm -f toadyWebApi-green', calls)
        self.assertIn('ROLLBACK OK', result.stderr)

    def test_failed_switch_back_leaves_new_stack_running(self):
        result, calls, *_ = self.run_deploy(front_suffix='-blue', check_fails_for='toadyWebApi-green:', fail_nginx_t_for='-blue')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ROLLBACK FAILED: front nginx could not be switched back', result.stderr)
        self.assertNotIn('rm -f toadyWebApi-green', calls)
        self.assertNotIn('rm -f toadyWebApi-blue', calls)

    def test_up_failure_keeps_scheduler_and_old_stack(self):
        result, calls, tag, env, _, _ = self.run_deploy(fail_up_for='new', env_text='IMAGE_TAG=old\n', front_suffix='-blue')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('docker-compose up failed', result.stderr)
        self.assertNotIn('up -d --no-deps toadyScheduler', calls)
        self.assertNotIn('nginx -s reload', calls)
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual('old\n', tag)
        self.assertEqual('IMAGE_TAG=old\n', env)
        self.assertEqual('old compose\n', self.compose)

    def test_scheduler_rollback_failure_is_reported(self):
        result, _, _, _, _, _ = self.run_deploy(front_suffix='-blue', check_fails_for='toadyWebApi-green:1', fail_up_for='old')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ROLLBACK FAILED', result.stderr)

    def test_first_deploy_failure_is_not_rolled_back_and_records_nothing(self):
        result, _, tag, env, _, _ = self.run_deploy(previous_tag='', check_fails_for='toadyWebApi-blue:')
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
