"""Run the built mod in an isolated Forge world with the buildable multiplayer fixture."""
from pathlib import Path
import json, os, subprocess, sys, time

sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h
OUT=ROOT/'build/buildables-tests';OUT.mkdir(exist_ok=True)
args=(h.SCRATCH/'267-test-javac.args').read_text().splitlines()
cp=args[args.index('"-classpath"')+1].strip('"')
mapped=OUT/'mapped.jar';fixed=OUT/'test-mapped.jar'
if '--reuse-mapped' not in sys.argv:
    h.remap(ROOT/'build/libs/zombierool-26.7-dev.jar',mapped,ROOT/'build/createSrgToMcp/output.srg',[h.SRG_FORGE,ROOT/'dev-libs/tacz-1.20.1-1.1.8-hotfix2.jar'],'buildable-remap')
    h.compile_sources([ROOT/'tools/tests/DevelopmentMixinNames.java'],OUT/'patch-tools',cp,'buildable-patch-javac')
    h.run([h.JAVA,'-cp',str(OUT/'patch-tools')+';'+cp,'DevelopmentMixinNames',mapped,fixed,ROOT/'build/createSrgToMcp/output.srg'],'buildable-dev-mixin.log')
cp=cp.replace(str(ROOT/'build/dev-26.7/test-mapped.jar').replace('\\','/'),str(fixed).replace('\\','/'))
h.compile_sources([ROOT/'tools/tests/BuildableGameplayTest.java'],OUT/'test-mod',cp,'buildable-test-javac')
meta=OUT/'test-mod/META-INF';meta.mkdir(exist_ok=True)
(meta/'mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="buildabletests"\nversion="1"\ndisplayName="Isolated buildable tests"\n')
world=OUT/'world';world.mkdir(exist_ok=True)
(world/'eula.txt').write_text('eula=true\n')
(world/'server.properties').write_text('online-mode=false\nserver-port=0\nlevel-type=minecraft:flat\nspawn-protection=0\nmax-tick-time=-1\nallow-flight=true\n')
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(fixed)+';buildabletests%%'+str(OUT/'test-mod');env['MCP_MAPPINGS']='official_1.20.1'
log=OUT/'server.log'
with log.open('w',encoding='utf8') as stream:
    proc=subprocess.Popen([str(h.JAVA),'@'+str(h.SCRATCH/'server.args')],cwd=world,env=env,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Buildable Forge integration server started.',flush=True)
    start=time.monotonic()
    while proc.poll() is None and time.monotonic()-start<300:time.sleep(1)
    if proc.poll() is None:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try:proc.wait(timeout=20)
        except subprocess.TimeoutExpired:proc.terminate();proc.wait()
text=log.read_text(encoding='utf8',errors='replace')
for line in text.splitlines():
    if 'BUILDABLE CHECK:' in line or 'BUILDABLE RUNTIME PASS:' in line:print(line)
if 'BUILDABLE RUNTIME PASS:' not in text or any('/ERROR' in line and '[minecraft/Display]' in line for line in text.splitlines()):
    print('\n'.join(text.splitlines()[-65:]));sys.exit(1)
print('Isolated buildable integration passed.')
