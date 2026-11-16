"""배포 검증은 실제 Docker/네트워크 대신 격리된 명령 스텁으로 검사한다."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-deployment.sh'


class DeploymentReadinessTest(unittest.TestCase):
    def run_check(self, scheduler_fails, capabilities='{"tradesEnabled":true}', schema='',
                  public_code='200', public_exit=0, api_code='200', api_exit=0, restarting_service='', server_pages=None, extra_env=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stubs = {
                'curl': '''#!/bin/sh
case "$*" in
  *research/capabilities*) printf '%s' "$CAPABILITIES" ;;
  *internal/schema*) [ -n "$SCHEMA" ] || exit 22; printf '%s' "$SCHEMA" ;;
  *planner/settings*) printf '%s' "$API_CODE"; exit "$API_EXIT" ;;
  *X-Toady-Check*)
    case "$*" in
      *sitemap.xml*) printf '%s' "${SITEMAP_BODY-<sitemapindex>}" ;;
      *) printf '%s' "${WEEKLY_BODY-<div class='stats'>}" ;;
    esac ;;
  *--resolve*) printf '%s' "$PUBLIC_CODE"; exit "$PUBLIC_EXIT" ;;
  *8082*)
    count=0
    [ ! -f "$REVIEW_STATE" ] || count=$(cat "$REVIEW_STATE")
    count=$((count+1))
    echo "$count" > "$REVIEW_STATE"
    [ "$FAIL_SCHEDULER" = 0 ] && [ "$count" -ge 3 ]
    ;;
  *) echo "$*" >> "$CURL_LOG"; exit 0 ;;
esac
''',
                'sleep': '#!/bin/sh\nexit 0\n',
                'docker': '''#!/bin/sh
case "$*" in
  *State.Running*) echo true ;;
  *State.Restarting*)
    for last; do :; done
    if [ "$last" = "$RESTARTING_SERVICE" ]; then echo true; else echo false; fi ;;
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
                                         'REVIEW_STATE': str(state), 'CURL_LOG': str(root / 'curl.log'), 'FAIL_SCHEDULER': str(int(scheduler_fails)),
                                         'CAPABILITIES': capabilities, 'SCHEMA': schema,
                                         'PUBLIC_CODE': public_code, 'PUBLIC_EXIT': str(public_exit),
                                         'API_CODE': api_code, 'API_EXIT': str(api_exit),
                                         'RESTARTING_SERVICE': restarting_service, **(server_pages or {}), **(extra_env or {})})
            self.curl_calls = (root / 'curl.log').read_text() if (root / 'curl.log').exists() else ''
            return result, int(state.read_text())

    def test_waits_until_scheduler_is_ready(self):
        result, attempts = self.run_check(False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(3, attempts)
        self.assertNotIn('WARNING', result.stderr)

    def test_fails_deployment_when_scheduler_never_becomes_ready(self):
        result, attempts = self.run_check(True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Scheduler readiness check failed', result.stderr)
        self.assertEqual(30, attempts)

    def test_warns_without_failing_when_trade_key_is_missing(self):
        result, _ = self.run_check(False, '{"tradesEnabled":false}')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('MOLIT_TRADE_SERVICE_KEY', result.stderr)

    def test_warns_about_missing_tables_without_failing(self):
        result, _ = self.run_check(False, schema='{"missing":["TB_PUSH_SUBSCRIPTION","TB_RATE_POLL"]}')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('TB_PUSH_SUBSCRIPTION,TB_RATE_POLL', result.stderr)

    def test_fails_when_a_core_table_is_missing(self):
        result, _ = self.run_check(False, schema='{"missing":["TB_RATE_POLL","TB_MAIL_ATTEMPT"]}')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('TB_MAIL_ATTEMPT', result.stderr)

    def test_tolerates_old_api_without_schema_endpoint_and_complete_schema(self):
        for schema in ('', '{"missing":[]}'):
            result, _ = self.run_check(False, schema=schema)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertNotIn('WARNING', result.stderr)

    def test_fails_when_front_nginx_refuses_connection(self):
        result, _ = self.run_check(False, public_code='000', public_exit=7)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Front nginx check failed', result.stderr)

    def test_fails_when_front_nginx_returns_gateway_errors(self):
        for code in ('502', '503', '504'):
            result, _ = self.run_check(False, public_code=code)
            self.assertNotEqual(0, result.returncode, code)
            self.assertIn('HTTP ' + code, result.stderr)

    def test_fails_when_api_through_front_nginx_returns_bad_gateway(self):
        result, _ = self.run_check(False, api_code='502')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('/api/v1/planner/settings returned HTTP 502', result.stderr)

    def test_only_warns_for_tls_or_dns_errors_through_front_nginx(self):
        for exit_code in (6, 35, 51, 60):
            result, _ = self.run_check(False, public_code='000', public_exit=exit_code, api_code='000', api_exit=exit_code)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('WARNING', result.stderr)
            self.assertIn('curl exit %d' % exit_code, result.stderr)

    def test_names_the_container_that_is_restarting(self):
        result, _ = self.run_check(False, restarting_service='toadyScheduler')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Container check failed: toadyScheduler', result.stderr)


    def test_warns_when_server_pages_fall_back_to_the_spa(self):
        spa = '<div id="root"></div>'
        result, _ = self.run_check(False, server_pages={'SITEMAP_BODY': spa, 'WEEKLY_BODY': spa})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('/sitemap.xml did not return the server-rendered page', result.stderr)
        self.assertIn('/weekly did not return the server-rendered page', result.stderr)

    GREEN = {'API_CONTAINER': 'toadyWebApi-green', 'API_PORT': '28080', 'WEB_CONTAINER': 'toadyReactWeb-green',
             'ADMIN_CONTAINER': 'toadyAdmin-green', 'ADMIN_PORT': '28083'}

    def test_checks_the_given_color_stack(self):
        result, _ = self.run_check(False, extra_env=self.GREEN)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('127.0.0.1:28080/internal/health', self.curl_calls)
        self.assertNotIn('127.0.0.1:8080/', self.curl_calls)
        result, _ = self.run_check(False, restarting_service='toadyReactWeb-green', extra_env=self.GREEN)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Container check failed: toadyReactWeb-green', result.stderr)

    def test_skips_front_nginx_checks_before_switching(self):
        # 전환 전에는 앞단이 이전 색을 가리키므로 앞단 확인을 하지 않는다(502여도 통과).
        result, _ = self.run_check(False, public_code='502', extra_env={**self.GREEN, 'SKIP_FRONT_CHECKS': '1'})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('front nginx checks skipped', result.stdout)

if __name__ == '__main__':
    unittest.main()
