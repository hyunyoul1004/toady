#!/usr/bin/env python3
"""Check repository files, including tracked files that .gitignore cannot protect."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
paths = subprocess.check_output(
    ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT
).decode().split('\0')

excluded_directories = {
    'node_modules', '.gradle', 'dist', 'coverage', 'logs', '.idea', '.vscode',
    '.run', '.aws', '.ssh', 'backups',
}
credential_suffixes = {'.pem', '.key', '.p12', '.pfx', '.jks', '.keystore'}
legacy_fonts = {
    'toadyReactWeb/src/resources/MaruBuriTTF',
    'toadyReactWeb/src/resources/nanum-barun-pen',
    'toadyReactWeb/src/resources/nanum-gothic',
}
allowed_readmes = {
    'README.md',
    'toadyReactWeb/README.md',
    'toadyWebApi/README.md',
    'toadyScheduler/README.md',
    'toadyAdmin/README.md',
}
local_notes = {'docs/REFACTORING.md', 'docs/REPOSITORY.md'}
# Only recognizable credential formats; never print their values.
credential_patterns = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----'),
    re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    re.compile(r'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
    re.compile(r'appkey=[a-fA-F0-9]{32}\b'),
]

issues = []
for relative in sorted(set(filter(None, paths))):
    file = ROOT / relative
    if not file.is_file():  # Respect pending deletions in the working tree.
        continue
    parts = Path(relative).parts
    name = file.name
    env_example = name == '.env.example' or (name.startswith('.env.') and name.endswith('.example'))
    reasons = []
    if name.lower().startswith('readme') and relative not in allowed_readmes:
        reasons.append('README outside repository/application roots')
    if relative in local_notes:
        reasons.append('local notes consolidated into root README')
    if (name == '.env' or name.startswith('.env.')) and not env_example:
        reasons.append('actual environment configuration')
    if excluded_directories.intersection(parts):
        reasons.append('local files or generated output')
    # Source packages named build/bin/out are valid; root app output is not.
    if len(parts) >= 2 and parts[0] in {'toadyWebApi', 'toadyScheduler', 'toadyReactWeb', 'toadyAdmin'} and parts[1] in {'build', 'bin', 'out'}:
        reasons.append('application build output')
    if file.suffix in credential_suffixes or name.endswith(('.sql.gz', '.sql.zip', '.dump', '.hprof', '.log')):
        reasons.append('credential, backup or runtime artifact')
    if name in {'.DS_Store', 'Thumbs.db', '.deployed-image-tag'}:
        reasons.append('local state')
    if re.fullmatch(r'application-(?:dev|local)\.ya?ml', name):
        reasons.append('local Spring configuration')
    if relative == 'toadyReactWeb/yarn.lock' or any(relative.startswith(p + '/') for p in legacy_fonts):
        reasons.append('unused legacy frontend file')
    if reasons:
        issues.append(f'{relative}: {", ".join(reasons)}')
        continue
    if file.suffix in {'.java', '.js', '.jsx', '.html', '.yml', '.yaml', '.json', '.properties', '.sh', '.py', '.md'} or name.startswith('.env'):
        for line_number, line in enumerate(file.read_text(errors='replace').splitlines(), 1):
            if any(pattern.search(line) for pattern in credential_patterns):
                issues.append(f'{relative}:{line_number}: credential-like literal (value redacted)')

if issues:
    print('Repository check failed:', file=sys.stderr)
    print('\n'.join(issues), file=sys.stderr)
    sys.exit(1)
print('Repository check passed (file policy and recognizable credential formats).')
