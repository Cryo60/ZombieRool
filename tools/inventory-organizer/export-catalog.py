"""Capture real client frames from a disposable copy of the new demonstration map."""
from pathlib import Path
import os,subprocess,sys,shutil,time
sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/inventory-catalog';OUT.mkdir(exist_ok=True)
JAVA='C:/Java/jdk-17.0.20.1+1/bin/';mod=OUT/'mod';(mod/'META-INF').mkdir(parents=True,exist_ok=True)
(mod/'META-INF/mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="inventorycatalog"\nversion="1"\ndisplayName="Inventory catalog export"\n')
args=(ROOT/'build/zombie-presets/lunar-client-compile.args').read_text().splitlines();cp=str(ROOT/'build/buildables-tests/test-mapped.jar')+';'+args[args.index('"-classpath"')+1].strip('"')
args=['-proc:none','-encoding','UTF-8','-classpath',cp,'-d',str(mod),str(ROOT/'tools/inventory-organizer/InventoryCatalogExport.java')]
argfile=OUT/'compile.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args));subprocess.run([JAVA+'javac.exe','@'+str(argfile)],cwd=ROOT,check=True)
client=OUT/('client' if len(sys.argv)<2 else sys.argv[1]);save=client/'saves/InventoryCatalogFixture';assert not save.exists(),'Preserve the previous visual fixture.'
shutil.copytree(ROOT/'run/saves/ZombieRool - Les Ateliers v2',save,ignore=shutil.ignore_patterns('session.lock','playerdata','stats','advancements'))
(client/'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\noverrideWidth:1024\noverrideHeight:1024\nguiScale:1\nrenderDistance:5\nsimulationDistance:3\nlang:en_us\ntutorialStep:none\n')
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(ROOT/'build/buildables-tests/test-mapped.jar')+';inventorycatalog%%'+str(mod)+';tacz%%'+str(ROOT/'build/dev-26.7/client-tacz-with-bedrock.jar');env['MCP_MAPPINGS']='official_1.20.1'
args=(ROOT/'build/dev-26.7/client-test.args').read_text();args=args.replace('D:/Dev/ZombieRool/build/dev-26.7/client-render-test/test-home',str(OUT/'test-home').replace('\\','/'));args+='\n"--width"\n"1024"\n"--height"\n"1024"\n';argfile=OUT/'client.args';argfile.write_text(args)
log=OUT/'client.log'
with log.open('w',encoding='utf8') as stream:
    p=subprocess.Popen([JAVA+'java.exe','@'+str(argfile)],cwd=client,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Isolated inventory catalog export started.',flush=True);start=time.monotonic()
    while p.poll() is None and time.monotonic()-start<300:time.sleep(1)
    if p.poll() is None:p.terminate();p.wait()
text=log.read_text(encoding='utf8',errors='replace')
for line in text.splitlines():
    if 'INVENTORY ' in line:print(line)
if 'INVENTORY EXPORT PASS' not in text:print(text[-9000:]);sys.exit(1)
