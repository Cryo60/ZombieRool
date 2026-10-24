"""Load a separate copy of Les Ateliers; never writes to the player's save or process."""
from pathlib import Path
import os,subprocess,sys,shutil,time
sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/loading-diagnosis';OUT.mkdir(exist_ok=True)
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe')
before='--before' in sys.argv
runtime=OUT/'before.jar' if before else ROOT/'build/portable-equipment/runtime-official.jar'
if before and not runtime.exists():shutil.copy2(ROOT/'build/portable-equipment/runtime-official.jar',runtime)
args=(ROOT/'build/zombie-presets/lunar-corrections-compile.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
mod=OUT/'test-mod';mod.mkdir(exist_ok=True)
f=OUT/'compile.args';a=['-proc:none','-encoding','UTF-8','-classpath',str(runtime)+';'+cp,'-d',str(mod),str(ROOT/'tools/tests/WorldLoadingRegressionTest.java')];f.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in a));subprocess.run([JAVA.with_name('javac.exe'),'@'+str(f)],check=True)
meta=mod/'META-INF';meta.mkdir(exist_ok=True);(meta/'mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="worldloadingtests"\nversion="1"\ndisplayName="World loading regression"\n')
work=OUT/('before-run' if before else 'fixed-run');work.mkdir(exist_ok=True);world=work/'world'
if not world.exists():shutil.copytree(ROOT/'run/saves/ZombieRool - Les Ateliers v2',world,ignore=shutil.ignore_patterns('session.lock'))
(work/'eula.txt').write_text('eula=true\n');(work/'server.properties').write_text('online-mode=false\nserver-port=0\nlevel-name=world\nspawn-protection=0\nmax-tick-time=-1\n')
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(runtime)+';worldloadingtests%%'+str(mod);env['MCP_MAPPINGS']='official_1.20.1'
logfile=OUT/('before.log' if before else 'fixed.log')
with logfile.open('w',encoding='utf8') as log:
    p=subprocess.Popen([JAVA,'@'+str(ROOT/'build/hotfix-1.6.6-hotfix1/server.args')],cwd=work,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Loading a copied user world: '+('baseline' if before else 'fixed code'),flush=True)
    try:p.wait(timeout=70)
    except subprocess.TimeoutExpired:
        dump=subprocess.run([JAVA.with_name('jcmd.exe'),str(p.pid),'Thread.print','-l'],capture_output=True,text=True,errors='replace',timeout=15)
        (OUT/('before-threads.txt' if before else 'fixed-threads.txt')).write_text(dump.stdout+dump.stderr,encoding='utf8');p.terminate();p.wait();print('World load timed out; isolated test server stopped.',flush=True)
text=logfile.read_text(encoding='utf8',errors='replace')
for line in text.splitlines():
    if 'WORLD LOADING' in line:print(line)
if 'WORLD LOADING PASS:' not in text:print('\n'.join(text.splitlines()[-12:]));sys.exit(1)
