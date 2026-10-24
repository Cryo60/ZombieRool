"""Exercise the installer in a disposable instance and smoke-test the dedicated server."""
import hashlib
import json
import os
import shutil
import sys
import subprocess
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'distribution'
SERVER = OUT / 'server'
CLIENT = OUT / 'client-installer'
PS = r'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'


def run_ps(script, *args):
    result = subprocess.run([PS, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(script), *args],
                            capture_output=True, text=True, timeout=240)
    print(result.stdout, result.stderr, flush=True)
    if result.returncode:
        raise RuntimeError(f'PowerShell failed: {result.returncode}')


def main():
    report = {}
    if '--server-only' in sys.argv:
        report = json.loads((OUT / 'validation.json').read_text())
        smoke_server(report)
        return
    manifest = json.loads((CLIENT / 'manifest.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(CLIENT / 'client-payload.zip') as z:
        for f in manifest['files']:
            assert hashlib.sha256(z.read(f['path'])).hexdigest() == f['sha256'], f['path']
    report['payloadVerifiedFiles'] = len(manifest['files'])
    instance = OUT / ('validation-instance-' + str(time.time_ns()))
    instance.mkdir(exist_ok=False)
    (instance / 'minecraftinstance.json').write_text(json.dumps({'gameVersion': '1.20.1', 'baseModLoader': {'name': 'forge-47.4.10'}}))
    (instance / 'mods').mkdir()
    (instance / 'mods/obsolete.jar').write_text('backup sentinel')
    (instance / 'config').mkdir()
    (instance / 'config/personal.txt').write_text('keep me')
    (instance / 'saves').mkdir()
    (instance / 'saves/sentinel.txt').write_text('keep world')
    run_ps(CLIENT / 'Install-ZombieRool.ps1', '-InstancePath', str(instance), '-Offline')
    assert not (instance / 'mods/obsolete.jar').exists()
    backups = list(instance.glob('.zombierool-backup-*'))
    assert len(backups) == 1
    assert (backups[0] / 'mods/obsolete.jar').read_text() == 'backup sentinel'
    assert (instance / 'config/personal.txt').read_text() == 'keep me'
    assert (instance / 'saves/sentinel.txt').read_text() == 'keep world'
    for f in manifest['files']:
        assert hashlib.file_digest((instance / f['path']).open('rb'), 'sha256').hexdigest() == f['sha256']
    report['installerOfflineAndBackup'] = 'passed'
    print('Installer verified', flush=True)
    local_maps = json.loads((SERVER / 'maps-local.json').read_text(encoding='utf-8'))
    for m in local_maps:
        run_ps(SERVER / 'Start-ZombieRool.ps1', '-Map', m['id'], '-Check')
    report['mapPreflight'] = f'{len(local_maps)} local maps passed'
    smoke_server(report)


def smoke_server(report, map_id='nacht-der-untoten'):
    # Reuse this user's already accepted EULA only during the local validation.
    accepted = ROOT / 'run-server/eula.txt'
    assert 'eula=true' in accepted.read_text()
    shutil.copy2(accepted, SERVER / 'eula.txt')
    try:
        with (OUT / 'server-validation.log').open('w', encoding='utf-8') as log:
            proc = subprocess.Popen([PS, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                                     str(SERVER / 'Start-ZombieRool.ps1'), '-Map', map_id, '-MemoryGB', '4'],
                                    cwd=SERVER, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 240
                while time.monotonic() < deadline:
                    content = (OUT / 'server-validation.log').read_text(encoding='utf-8', errors='replace')
                    if 'Done (' in content:
                        report['dedicatedServerNacht' if map_id == 'nacht-der-untoten' else 'dedicatedServer:' + map_id] = 'started successfully'
                        proc.stdin.write('list\nstop\n')
                        proc.stdin.flush()
                        proc.wait(timeout=90)
                        assert proc.returncode == 0
                        report['dedicatedServerShutdown'] = 'clean'
                        break
                    if proc.poll() is not None:
                        raise RuntimeError('Server exited before ready. See server-validation.log')
                    time.sleep(2)
                else:
                    raise TimeoutError('Server startup exceeded 240 seconds')
            finally:
                if proc.poll() is None:
                    subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True)
    finally:
        (SERVER / 'eula.txt').write_text('eula=false\n', encoding='ascii')
        (OUT / 'validation.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report), flush=True)


if __name__ == '__main__':
    main()
