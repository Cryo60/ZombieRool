"""Validate the actual saved Les Ateliers map in a separate copy with a full Zombies match."""
from pathlib import Path
import os,shutil,subprocess,sys,time

sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/buildable-map-playtest-v2'
SOURCE=ROOT/'run/saves/ZombieRool - Les Ateliers v2'
JAVA='C:/Java/jdk-17.0.20.1+1/bin/'
OUT.mkdir(exist_ok=True);mod=OUT/'mod';(mod/'META-INF').mkdir(parents=True,exist_ok=True)
(mod/'META-INF/mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="buildablemaptest"\nversion="1"\ndisplayName="Buildable saved-map playtest"\n')
args=(ROOT/'build/hotfix-1.6.6-hotfix1/buildable-test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
args=['-proc:none','-encoding','UTF-8','-classpath',cp,'-d',str(mod),str(ROOT/'tools/tests/BuildableMapPlaytest.java')]
argfile=OUT/'compile.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args));subprocess.run([JAVA+'javac.exe','@'+str(argfile)],cwd=ROOT,check=True)
work=OUT/'server';work.mkdir(exist_ok=True);assert not (work/'world').exists(),'Preserve existing playtest copy.'
shutil.copytree(SOURCE,work/'world',ignore=shutil.ignore_patterns('session.lock','playerdata','stats','advancements'))
(work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('online-mode=false\nserver-port=0\ngamemode=adventure\nview-distance=3\nsimulation-distance=3\nmax-tick-time=-1\nallow-flight=true\n')
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(ROOT/'build/buildables-tests/test-mapped.jar')+';buildablemaptest%%'+str(mod);env['MCP_MAPPINGS']='official_1.20.1'
log=OUT/'playtest.log'
with log.open('w',encoding='utf8') as stream:
    p=subprocess.Popen([JAVA+'java.exe','@'+str(ROOT/'build/hotfix-1.6.6-hotfix1/server.args')],cwd=work,env=env,stdout=stream,stderr=subprocess.STDOUT,stdin=subprocess.PIPE,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Playtesting the saved map in an isolated copy...',flush=True);start=time.monotonic()
    while p.poll() is None and time.monotonic()-start<300:time.sleep(1)
    if p.poll() is None:
        p.stdin.write('stop\n');p.stdin.flush()
        try:p.wait(timeout=30)
        except subprocess.TimeoutExpired:p.terminate();p.wait()
text=log.read_text(encoding='utf8',errors='replace')
for line in text.splitlines():
    if 'MAP PLAYTEST CHECK:' in line or 'ATELIERS PLAYTEST PASS:' in line:print(line)
if 'ATELIERS PLAYTEST PASS:' not in text:print(text[-10000:]);sys.exit(1)
print('Saved map playtest passed; original save preserved.')
