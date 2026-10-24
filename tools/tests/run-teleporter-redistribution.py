"""Run targeted Forge server checks in an isolated world using cached dev dependencies."""
from pathlib import Path
import json, os, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h

scratch = ROOT / 'build/teleporter-redistribution-tests'
scratch.mkdir(parents=True, exist_ok=True)
artifact = Path(sys.argv[sys.argv.index('--artifact') + 1]).resolve() if '--artifact' in sys.argv else ROOT / 'build/libs/zombierool-26.7-dev.jar'
if '--fixtures-only' not in sys.argv:
    h.remap(artifact, scratch / 'mapped.jar', ROOT / 'build/createSrgToMcp/output.srg',
            [h.SRG_FORGE, ROOT / 'dev-libs/tacz-1.20.1-1.1.8-hotfix2.jar'], 'transfer-remap')
    args = (h.SCRATCH / 'checks-javac.args').read_text().splitlines()
    cp = args[args.index('"-classpath"') + 1].strip('"')
    h.compile_sources([ROOT / 'tools/tests/DevelopmentMixinNames.java'], scratch / 'patch-tools', cp, 'transfer-mixin-javac')
    h.run([h.JAVA, '-cp', str(scratch / 'patch-tools') + ';' + cp, 'DevelopmentMixinNames', scratch / 'mapped.jar',
           scratch / 'test-mapped.jar', ROOT / 'build/createSrgToMcp/output.srg'], 'transfer-mixin-patch.log')
args = (h.SCRATCH / 'test-javac.args').read_text().splitlines()
cp = args[args.index('"-classpath"') + 1].strip('"')
for name in ['hotfix-mapped.jar', 'hotfix-test-mapped.jar']:
    cp = cp.replace(str(h.SCRATCH / name).replace('\\', '/'), str(scratch / 'test-mapped.jar').replace('\\', '/'))
h.compile_sources([ROOT / 'tools/tests/TeleporterPlayerLockTest.java', ROOT / 'tools/tests/TeleporterCooldownTest.java', ROOT / 'tools/tests/TeleporterRedistributionTest.java', ROOT / 'tools/tests/TeleporterRedistributionRuntime.java'],
                  scratch / 'test-mod', cp, 'transfer-tests-javac')
(scratch / 'test-mod/META-INF').mkdir(exist_ok=True)
shutil.copy2(h.SCRATCH / 'test-mod/META-INF/mods.toml', scratch / 'test-mod/META-INF/mods.toml')
env = os.environ.copy()
env.update(json.loads((h.SCRATCH / 'test-env.json').read_text()))
for old, new in [(h.SCRATCH / 'hotfix-test-mapped.jar', scratch / 'test-mapped.jar'), (h.SCRATCH / 'test-mod', scratch / 'test-mod')]:
    env['MOD_CLASSES'] = env['MOD_CLASSES'].replace(str(old), str(new)).replace(str(old).replace('\\', '/'), str(new).replace('\\', '/'))
import time
world = scratch / ('world-' + str(time.time_ns()))
world.mkdir(exist_ok=True)
shutil.copy2(h.SCRATCH / 'test-world/eula.txt', world / 'eula.txt')
properties = (h.SCRATCH / 'test-world/server.properties').read_text()
import re
properties = re.sub(r'^server-port=.*$', 'server-port=0', properties, flags=re.M)
(world / 'server.properties').write_text(properties)
log = world / 'server.log'
print('Starting isolated teleporter/redistribution checks.', flush=True)
with log.open('w', encoding='utf-8') as output:
    process = subprocess.Popen([str(h.JAVA), '@' + str(h.SCRATCH / 'server.args')], cwd=world, env=env,
                               stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait()
        raise RuntimeError("Isolated server test timed out; see " + str(log))
text = log.read_text(errors='replace')
shutil.copy2(log, scratch / 'server.log')
for line in text.splitlines():
    if 'TRANSFER CHECK:' in line or 'TRANSFER TEST PASS' in line or 'COOLDOWN CHECK:' in line or 'COOLDOWN TEST PASS' in line or 'LOCK CHECK:' in line or 'LOCK TEST PASS' in line:
        print(line)
if 'TRANSFER TEST PASS' not in text or 'COOLDOWN TEST PASS' not in text or 'LOCK TEST PASS' not in text:
    print('\n'.join(text.splitlines()[-60:]))
    raise SystemExit(1)
print('Teleporter redistribution integration passed.')
