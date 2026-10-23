"""Scan only Git's staged file list. Does not print matched private values."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = re.compile(rb'192\.168\.1\.(?:17|31)\b|carfr|codex-clipboard|BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}', re.I)


def main():
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    errors = []
    for name in filter(None, paths):
        path = Path(name)
        if any(part in {'.private', '.cache', 'backups', 'build', '.gradle'} for part in path.parts) or path.suffix.lower() in {'.apk', '.jks', '.keystore', '.p12', '.mp4', '.jpg', '.jpeg', '.png'}:
            # Launcher artwork is shipped upstream, not a personal photo.
            if name not in {'launcher/app/src/main/res/drawable-nodpi/banner.jpg', 'launcher/app/src/main/res/mipmap-xhdpi/ic_launcher.jpg'}:
                errors.append(name + ': excluded artifact')
        data = (ROOT/path).read_bytes()
        if name != 'scripts/audit_public.py' and FORBIDDEN.search(data):
            errors.append(name + ': possible private data')
    if errors: raise SystemExit('\n'.join(errors))
    print('Public-source audit passed:', len(list(filter(None, paths))), 'files')


if __name__ == '__main__': main()
