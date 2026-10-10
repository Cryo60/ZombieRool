"""Install without rewriting a JAR held open by a running Minecraft process."""
from pathlib import Path
from datetime import datetime
from zipfile import ZipFile
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
INSTANCE = Path('C:/Users/ecorange1/curseforge/minecraft/Instances/ZombieRool (no mods)')
BUILT = ROOT / 'build/libs/zombierool-1.6.6-hotfix1.jar'

def running_game(instance):
    script = """
$instancePath = $env:ZR_HOTFIX_INSTANCE.Replace('/', '\\')
@(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
  Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($instancePath, [StringComparison]::OrdinalIgnoreCase) -ge 0 } |
  ForEach-Object { $_.ProcessId }) | ConvertTo-Json -Compress
"""
    env = dict(os.environ, ZR_HOTFIX_INSTANCE=str(instance))
    result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', script],
                            env=env, capture_output=True, text=True, check=True,
                            creationflags=subprocess.CREATE_NO_WINDOW)
    text = result.stdout.strip()
    return json.loads(text) if text else []

def checked_install(check_only=False):
    report = json.loads((ROOT / 'build/hotfix-1.6.6-hotfix1/content-diff.json').read_text())
    digest = hashlib.sha256(BUILT.read_bytes()).hexdigest()
    if digest != report['hotfixSha256']:
        raise RuntimeError('Build hash differs from the verified content report.')
    with ZipFile(BUILT) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('JAR integrity check failed.')
        for name in archive.namelist():
            if name.endswith('.class'):
                data = archive.read(name)
                if data[:4] != bytes.fromhex('cafebabe') or int.from_bytes(data[6:8], 'big') > 61:
                    raise RuntimeError('Invalid Java 17 class: ' + name)
    if running_game(INSTANCE):
        raise RuntimeError('Minecraft is running in this instance. Close it before installing; the installed JAR has not been changed.')
    if check_only:
        print('PASS: verified JAR; selected Minecraft instance is closed. No files changed.')
        return
    target = INSTANCE / 'mods' / BUILT.name
    backup = INSTANCE / 'zombierool-backups' / (datetime.now().astimezone().strftime('%Y%m%d-%H%M%S') + '-hotfix-install')
    backup.mkdir(parents=True, exist_ok=False)
    if target.exists():
        shutil.copy2(target, backup / target.name)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix='.zr-hotfix-', suffix='.tmp', delete=False) as stream:
            temporary = Path(stream.name)
            with BUILT.open('rb') as source:
                shutil.copyfileobj(source, stream)
            stream.flush()
            os.fsync(stream.fileno())
        if running_game(INSTANCE):
            raise RuntimeError('Minecraft started during preparation. Installation canceled; original JAR unchanged.')
        # Never truncate the installed file. Rename atomically or fail if Windows locks it.
        os.replace(temporary, target)
        temporary = None
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Installed JAR hash mismatch.')
        print(json.dumps({'installed': str(target), 'sha256': digest, 'backup': str(backup)}, indent=2))
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check-only', action='store_true')
    checked_install(parser.parse_args().check_only)
