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
    def run_deploy(self, previous_tag='old', fail_up_for='', check_fails_for='', env_text=None, fail_config='', admin_env=None,
                   same_apps='', new_compose='new compose\n', redis_password='r' * 32, recorded_sums=None, fail_pull=''):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            work = home / 'toady'
            (work / 'scripts').mkdir(parents=True)
            bin_dir = home / 'bin'
            bin_dir.mkdir()
            log = home / 'calls.log'
            stubs = {
                'docker-compose': '#!/bin/sh\necho "compose $IMAGE_TAG $*" >> "$CALL_LOG"\n'
                                  'case "$*" in *" up "*redis*) [ -z "$FAIL_REDIS" ] ;; *" up "*) [ "$IMAGE_TAG" != "$FAIL_UP_FOR" ] ;; *" config "*) [ -z "$FAIL_CONFIG" ] ;; *" pull "*) [ -z "$FAIL_PULL" ] ;; esac\n',
                # 이미지 ID 스텁: 새 태그 이미지는 id-<저장소>, SAME_APPS에 든 컨테이너만 같은 ID로 돌고 있다.
                'docker': '#!/bin/bash\necho "docker $*" >> "$CALL_LOG"\n'
                          'if [ "$1 $2" = "image inspect" ]; then n="${@: -1}"; n="${n##*/}"; echo "id-${n%%:*}"; exit 0; fi\n'
                          'if [ "$1" = inspect ]; then app="${@: -1}"\n'
                          '  case "$3" in *Running*) echo true ;;\n'
                          '    *) case " $SAME_APPS " in *" $app "*) case "$app" in toadyWebApi) echo id-toady-web-api ;; toadyScheduler) echo id-toady-scheduler ;;'
                          ' toadyReactWeb) echo id-toady-react-web ;; toadyAdmin) echo id-toady-admin ;; esac ;; *) echo id-old ;; esac ;; esac; fi\n',
            }
            for name, source in stubs.items():
                (bin_dir / name).write_text(source)
                (bin_dir / name).chmod(0o755)
            # CHECK_FAILS_FOR에 든 태그(공백으로 여러 개)는 확인에 실패한다.
            check = '#!/bin/sh\necho "check $IMAGE_TAG" >> "$CALL_LOG"\ncase " $CHECK_FAILS_FOR " in *" $IMAGE_TAG "*) exit 1 ;; esac\n'
            (work / 'scripts' / 'check-deployment.sh').write_text(check)
            (work / 'docker-compose.yml').write_text('old compose\n')
            # scp가 받아 둔 새 파일(배포 스크립트가 이전 파일을 보관한 뒤 교체한다).
            (work / '.incoming' / 'scripts').mkdir(parents=True)
            (work / '.incoming' / 'docker-compose.yml').write_text(new_compose)
            (work / '.incoming' / 'scripts' / 'check-deployment.sh').write_text(check)
            if previous_tag:
                (work / '.deployed-image-tag').write_text(previous_tag + '\n')
            (work / 'toadyWebApi').mkdir()
            (work / 'toadyWebApi' / '.env.prod').write_text('DB_URL=x\n' + (f'REDIS_PASSWORD={redis_password}\n' if redis_password else ''))
            if admin_env is not None:
                (work / 'toadyAdmin').mkdir()
                (work / 'toadyAdmin' / '.env.prod').write_text(admin_env)
            if recorded_sums is not None:
                # 'current'면 지금 파일 그대로의 기록(바뀐 것 없음), 그 밖에는 주어진 값을 그대로 쓴다.
                if recorded_sums == 'current':
                    snippet = server_script().split('\nselect_restart() {')[0].split('\nenv_sum() {')[1]
                    defs = 'env_sum() {' + snippet
                    script = defs + '\nfor app in toadyWebApi toadyScheduler toadyReactWeb; do echo "$app $(env_sum "$app")"; done; echo "redis $(redis_sum)"\n'
                    (work / 'docker-compose.yml').write_text(new_compose)
                    recorded = subprocess.run(['bash', '-c', script], cwd=work, capture_output=True, text=True).stdout
                    (work / 'docker-compose.yml').write_text('old compose\n')
                else:
                    recorded = recorded_sums
                (work / '.deployed-env-sums').write_text(recorded)
            if env_text is not None:
                (work / '.env').write_text(env_text)
                (work / '.env').chmod(0o640)
            result = subprocess.run(['bash', '-c', server_script()], capture_output=True, text=True, timeout=20,
                                    env={**os.environ, 'HOME': str(home),
                                         'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
                                         'IMAGE_TAG': 'new', 'DOCKER_USERNAME': 'tester', 'CALL_LOG': str(log),
                                         'FAIL_UP_FOR': fail_up_for, 'SAME_APPS': same_apps, 'FAIL_PULL': fail_pull, 'FAIL_REDIS': '', 'CHECK_FAILS_FOR': check_fails_for, 'FAIL_CONFIG': fail_config})
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
        self.assertEqual(['.deployed-env-sums', '.deployed-image-tag', '.env', '.incoming', '.rollback', 'docker-compose.yml', 'scripts', 'toadyAdmin', 'toadyWebApi'], files)
        self.assertIn('up -d --force-recreate redis', calls, 'Redis 설정 기록이 없으면 한 번 다시 만든다')
        self.assertEqual('new compose\n', self.compose)

    def test_admin_site_joins_the_deploy_only_after_its_env_file_is_filled(self):
        # 설정 파일이 없으면 빈 파일만 만들어 compose 검사를 통과시키고, 관리자 컨테이너는 띄우지 않는다.
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn('toadyAdmin', calls)
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', admin_env='ADMIN_PASSWORD=x\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi toadyScheduler toadyReactWeb toadyAdmin', calls)

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

    def test_only_apps_with_a_new_image_are_restarted(self):
        result, calls, tag, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n',
                                                 same_apps='toadyWebApi toadyScheduler')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyReactWeb\n', calls)
        self.assertIn('apps to restart: toadyReactWeb', result.stdout)
        self.assertEqual('new\n', tag)

    def test_nothing_is_restarted_when_every_image_is_unchanged(self):
        result, calls, tag, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n',
                                                 same_apps='toadyWebApi toadyScheduler toadyReactWeb')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn('up -d --no-deps', calls)
        self.assertNotIn('nginx -s reload', calls)
        self.assertIn('check new', calls, '재시작이 없어도 배포 확인은 한다')
        self.assertEqual('new\n', tag)

    def test_changed_compose_restarts_every_app(self):
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', same_apps='toadyWebApi toadyScheduler toadyReactWeb')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi toadyScheduler toadyReactWeb', calls)

    def test_rollback_restarts_only_the_apps_that_were_replaced(self):
        result, calls, tag, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n',
                                                 same_apps='toadyWebApi toadyScheduler', check_fails_for='new')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('compose old -p toady up -d --no-deps toadyReactWeb\n', calls)
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual('old\n', tag)

    def test_same_tag_redeploy_failure_restarts_apps_with_the_restored_compose(self):
        # 같은 커밋을 다시 배포했는데 compose 변경으로 다시 만든 컨테이너가 확인에 실패하면, 이전 compose로 다시 띄운다.
        result, calls, tag, *_ = self.run_deploy(previous_tag='new', env_text='IMAGE_TAG=new\n',
                                                 same_apps='toadyWebApi toadyScheduler toadyReactWeb', check_fails_for='new')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Rolling back', result.stderr)
        self.assertEqual(2, calls.count('compose new -p toady up -d --no-deps toadyWebApi toadyScheduler toadyReactWeb'), calls)
        self.assertEqual('old compose\n', self.compose)
        self.assertEqual('new\n', tag)

    def test_rollback_recreates_redis_only_when_its_settings_were_changed(self):
        redis_compose = 'services:\n  redis:\n    image: redis:new\n'
        result, calls, *_ = self.run_deploy(previous_tag='new', env_text='IMAGE_TAG=new\n', new_compose=redis_compose,
                                            same_apps='toadyWebApi toadyScheduler toadyReactWeb', check_fails_for='new')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(2, calls.count('up -d --force-recreate redis'), '새 설정으로 다시 만든 Redis를 이전 설정으로 되돌린다')
        # Redis 설정이 그대로면(기록이 없어 한 번 다시 만든 경우) 롤백 때 다시 만들지 않는다.
        result, calls, *_ = self.run_deploy(check_fails_for='new')
        self.assertIn('ROLLBACK OK', result.stderr)
        self.assertEqual(1, calls.count('up -d --force-recreate redis'))

    def test_unchanged_settings_keep_redis_and_record_sums(self):
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n', recorded_sums='current',
                                            same_apps='toadyWebApi toadyScheduler toadyReactWeb')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-recreate redis', calls)
        self.assertNotIn('up -d --no-deps', calls)

    def test_changed_env_file_restarts_only_that_app(self):
        # 기록된 API 설정 합계가 지금 파일과 다르면(운영자가 .env.prod를 고침) 이미지가 같아도 API만 다시 만든다.
        result, calls, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n', recorded_sums='toadyWebApi 1-1\n',
                                            same_apps='toadyWebApi toadyScheduler toadyReactWeb')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('up -d --no-deps toadyWebApi\n', calls)

    def test_same_tag_env_change_failure_explains_that_settings_are_not_rolled_back(self):
        # 같은 태그·같은 compose에서 .env.prod만 바뀌어 실패하면 되돌릴 것이 없다. 같은 컨테이너를 다시 띄우지 않고 안내한다.
        result, calls, tag, *_ = self.run_deploy(previous_tag='new', env_text='IMAGE_TAG=new\n', new_compose='old compose\n',
                                                 recorded_sums='toadyWebApi 1-1\n', check_fails_for='new',
                                                 same_apps='toadyWebApi toadyScheduler toadyReactWeb')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ROLLBACK SKIPPED', result.stderr)
        self.assertIn('설정 파일(.env.prod) 변경은 자동으로 되돌리지 않습니다', result.stderr)
        self.assertIn('toadyWebApi', result.stderr)
        self.assertNotIn('ROLLBACK CHECK FAILED', result.stderr)
        self.assertEqual(1, calls.count('up -d --no-deps toadyWebApi'), calls)
        self.assertEqual('new\n', tag)

    def test_failed_rollback_check_after_env_change_mentions_the_settings_file(self):
        # 이미지를 되돌려도 바뀐 .env.prod 때문에 이전 태그도 실패하면, 설정 파일은 직접 되돌려야 한다고 함께 알린다.
        result, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', new_compose='old compose\n', recorded_sums='toadyWebApi 1-1\n',
                                     check_fails_for='new old', same_apps='toadyScheduler toadyReactWeb')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ROLLBACK CHECK FAILED', result.stderr)
        self.assertIn('설정 파일(.env.prod) 변경은 자동으로 되돌리지 않습니다', result.stderr)

    def test_pull_failure_reports_unchanged_containers(self):
        result, calls, tag, *_ = self.run_deploy(env_text='IMAGE_TAG=old\n', fail_pull='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('image pull failed', result.stderr)
        self.assertIn('app containers unchanged', result.stderr)
        self.assertNotIn('ROLLBACK OK', result.stderr)
        self.assertNotIn('up -d --no-deps', calls)
        self.assertEqual('old\n', tag)

    def test_missing_redis_password_stops_before_touching_containers(self):
        for password in ('', 'short'):
            result, calls, tag, *_ = self.run_deploy(redis_password=password)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('REDIS_PASSWORD', result.stderr)
            self.assertNotIn(' up ', calls)
            self.assertNotIn(' pull ', calls)
            self.assertEqual('old\n', tag)
            self.assertEqual('old compose\n', self.compose)

    def test_first_success_creates_private_env(self):
        result, _, tag, env, mode, _ = self.run_deploy(previous_tag='')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('new\n', tag)
        self.assertEqual('IMAGE_TAG=new\n', env)
        self.assertEqual('0o600', mode)


if __name__ == '__main__':
    unittest.main()
