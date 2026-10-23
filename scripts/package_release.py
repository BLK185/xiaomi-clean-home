"""Build a distribution from an explicit allowlist; never include local backups or keys."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    manifest = json.loads((ROOT/'release-manifest.json').read_text(encoding='utf-8-sig'))
    apk = ROOT/'dist'/manifest['apk']
    if hashlib.sha256(apk.read_bytes()).hexdigest() != manifest['sha256']:
        raise RuntimeError('APK checksum mismatch')
    fixed = ['README.md', 'NOTICE', 'LICENSE', 'Install.cmd', 'Install.ps1', 'Restore.cmd',
             'Restore.ps1', 'install.sh', 'restore.sh', 'release-manifest.json',
             'scripts/bootstrap.ps1', 'launcher/FONT-LICENSE-OFL.txt']
    sources = [ROOT/p for p in fixed]
    for directory, pattern in [('installer', '*.py'), ('profiles', '*.json'), ('docs', '*.md')]:
        sources.extend(sorted((ROOT/directory).glob(pattern)))
    sources.append(apk)
    destination = ROOT/'dist'/('xiaomi-clean-home-'+manifest['version']+'-windows.zip')
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for source in sources:
            archive.write(source, 'xiaomi-clean-home/'+source.relative_to(ROOT).as_posix())
    checksums = ''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in (apk, destination))
    (ROOT/'dist'/'SHA256SUMS.txt').write_text(checksums, encoding='utf-8')
    print(destination.name, destination.stat().st_size, 'bytes')


if __name__ == '__main__': main()
