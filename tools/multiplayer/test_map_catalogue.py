"""Offline regression checks for catalogue filtering and safe map installation."""
import hashlib
import json
import subprocess
import tempfile
import zipfile
from pathlib import Path

PS = r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'
HELPER = Path(__file__).with_name('Map-Catalogue.ps1')


def ps(root, code, success=True):
    quote = lambda value: "'" + str(value).replace("'", "''") + "'"
    script = "$ErrorActionPreference='Stop'; . " + quote(HELPER) + '; $root=' + quote(root) + '; ' + code
    result = subprocess.run([PS, '-NoProfile', '-Command', script], capture_output=True, text=True, timeout=30)
    assert (result.returncode == 0) == success, result.stdout + result.stderr
    return result.stdout


def prepare(root, name, entries):
    cache = root / 'map-downloads'
    cache.mkdir(exist_ok=True)
    archive = cache / 'temporary.zip'
    with zipfile.ZipFile(archive, 'w') as z:
        for path, data in entries.items():
            z.writestr(path, data)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    archive.rename(cache / f'{name}-{digest}.zip')
    m = {'id': name, 'name': name, 'version': '1', 'sha256': digest,
         'downloadUrl': 'https://github.com/Cryo60/zombierool-maps/releases/download/test/test.zip',
         'localOverride': False, 'mods': [], 'gunpacks': []}
    (root / 'test-map.json').write_text(json.dumps(m))


def main():
    with tempfile.TemporaryDirectory(prefix='zombierool-catalogue-') as tmp:
        root = Path(tmp)
        (root / 'worlds').mkdir()
        (root / 'map-catalogue').mkdir()
        (root / 'maps-local.json').write_text(json.dumps([{'id': 'nacht-der-untoten', 'name': 'Nacht der Untoten'}]))
        rows = [dict(id='zr_nacht', name='Nacht der Untoten', game_version='1.20.1'),
                dict(id='good', name='Approved map', game_version='1.20.1'),
                dict(id='good', name='Duplicate', game_version='1.20.1'),
                dict(id='pending', name='Pending', game_version='1.20.1', status='pending'),
                dict(id='../escape', name='Bad id', game_version='1.20.1'),
                dict(id='wrong-version', name='Wrong version', game_version='1.21')]
        (root / 'map-catalogue/official.json').write_text(json.dumps({'maps': rows}))
        (root / 'map-catalogue/community.json').write_text(json.dumps({'maps': []}))
        output = ps(root, '$maps=@(Get-ZombieRoolMaps -ServerRoot $root -Offline); if($maps.Count -ne 2 -or $maps[0].id -ne "nacht-der-untoten" -or $maps[1].id -ne "official-good") { throw "Filter failure" }')
        prepare(root, 'valid', {'wrapped-world/level.dat': b'world', 'wrapped-world/region/test.mca': b'region'})
        install = '$map=Get-Content -LiteralPath (Join-Path $root "test-map.json") -Raw | ConvertFrom-Json; Install-ZombieRoolMap -ServerRoot $root -Map $map'
        ps(root, install)
        assert (root / 'worlds/valid/level.dat').read_bytes() == b'world'
        (root / 'worlds/valid/level.dat').write_bytes(b'progress')
        ps(root, install)
        assert (root / 'worlds/valid/level.dat').read_bytes() == b'progress'
        prepare(root, 'traversal', {'../escape.txt': b'escape', 'level.dat': b'world'})
        ps(root, install, success=False)
        assert not (root / 'worlds/traversal').exists()
        assert not (root / 'escape.txt').exists()
        prepare(root, 'multiple', {'a/level.dat': b'a', 'b/level.dat': b'b'})
        ps(root, install, success=False)
        assert not (root / 'worlds/multiple').exists()
        prepare(root, 'corrupt', {'level.dat': b'world'})
        cached = next((root / 'map-downloads').glob('corrupt-*.zip'))
        cached.write_bytes(b'broken archive')
        ps(root, install, success=False)
        assert not (root / 'worlds/corrupt').exists()
    print('PASS: filtering, local Nacht override, nested ZIP, progress preservation, traversal, multiple worlds, bad checksum')


if __name__ == '__main__':
    main()
