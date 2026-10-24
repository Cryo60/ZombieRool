"""Read-only diagnosis of the real map using an isolated Forge server copy."""
from pathlib import Path
import os, shutil, subprocess, time, sys, importlib.util
from zipfile import ZipFile, ZIP_DEFLATED
sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2]
stress='--stress' in sys.argv
box='--box' in sys.argv
balance='--balance' in sys.argv
variants='--variants' in sys.argv or balance
OUT=ROOT/('build/stratholme-balance' if balance else 'build/risen-appearances' if variants else 'build/mystery-box-start' if box else 'build/spawner-load' if stress else 'build/stratholme-probe');OUT.mkdir(exist_ok=True)
WORLD=Path.home()/'curseforge/minecraft/Instances/ZombieRool (no mods)/saves/Stratholme'
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe')
args=(ROOT/'build/zombie-presets/lunar-corrections-compile.args').read_text().splitlines()
cp=args[args.index('"-classpath"')+1].strip('"')
runtime=ROOT/'build/stratholme-fixes/zombierool-26.7.jar'
if variants:runtime=ROOT/'build/stratholme-artillery/zombierool-26.7.jar'
if '--baseline' in sys.argv:runtime=ROOT/'build/libs/zombierool-26.7.jar'
classes=OUT/'classes';classes.mkdir(exist_ok=True)
compile_args=['-proc:none','-encoding','UTF-8','-classpath',str(ROOT/'build/stratholme-artillery/classes')+';'+str(ROOT/'build/stratholme-fixes/classes')+';'+str(ROOT/'build/motion-fix/all-classes')+';'+cp,'-d',str(classes),str(ROOT/('tools/tests/StratholmeBalanceTest.java' if balance else 'tools/tests/RisenAppearanceTest.java' if variants else 'tools/tests/MysteryBoxStartTest.java' if box else 'tools/tests/SpawnerLoadTest.java' if stress else 'tools/tests/StratholmeMapProbe.java'))]
af=OUT/'compile.args';af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in compile_args))
subprocess.run([JAVA.with_name('javac.exe'),'@'+str(af)],check=True)
meta=classes/'META-INF';meta.mkdir(exist_ok=True)
(meta/'mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="strathprobe"\nversion="1"\ndisplayName="Stratholme isolated probe"\n')
official=OUT/'probe-official.jar'
spec=importlib.util.spec_from_file_location('lunar',ROOT/'tools/build-lunar-development.py');helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
with ZipFile(official,'w',ZIP_DEFLATED) as z:
 for p in classes.rglob('*'):
  if p.is_file():z.writestr(p.relative_to(classes).as_posix(),helper.relocate(p.read_bytes()) if p.suffix=='.class' else p.read_bytes())
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'));import build_hotfix as h
forge=next(Path(p) for p in cp.split(';') if 'minecraft_user_repo' in p and 'mapped_official' in p and p.endswith('.jar'))
h.remap(official,OUT/'probe-srg.jar',ROOT/'build/reobfShadowJar/mappings.tsrg',[ROOT/'build/motion-fix/production-official-classes.jar',forge],'stratholme-probe-reobf')
work=OUT/('run-'+str(int(time.time())));save=work/'world';save.mkdir(parents=True)
for name in ([] if stress else ['level.dat','region','data']):
 src=WORLD/name;dst=save/name
 if src.is_dir():shutil.copytree(src,dst)
 else:shutil.copy2(src,dst)
for name in ([] if stress else ['scripts','gameplay/entities','config']):
 shutil.copytree(WORLD/'zombierool'/name,save/'zombierool'/name)
for name in ([] if stress else ['stratholme_risen_ambush.lua','stratholme_scourge_ambush.lua']):
 shutil.copy2(ROOT/'examples/stratholme/zombierool/scripts'/name,save/'zombierool/scripts'/name)
if variants:
 for old in (save/'zombierool/gameplay/entities').glob('scarlet_zombie*.json'):old.unlink()
 for source in (ROOT/'examples/stratholme/zombierool/gameplay/entities').glob('scarlet_zombie*.json'):shutil.copy2(source,save/'zombierool/gameplay/entities'/source.name)
if balance:shutil.copy2(ROOT/'examples/stratholme/zombierool/scripts/stratholme_balance.lua',save/'zombierool/scripts/stratholme_balance.lua')
if box:shutil.copy2(ROOT/'examples/stratholme/zombierool/scripts/stratholme_mystery_box.lua',save/'zombierool/scripts/stratholme_mystery_box.lua')
(work/'eula.txt').write_text('eula=true\n')
(work/'server.properties').write_text('online-mode=false\nserver-port=0\nmax-tick-time=-1\nview-distance=3\nsimulation-distance=3\n'+('level-type=minecraft:flat\n' if stress else ''))
base=ROOT/'build/recent-production/forge-base'
(work/'mods').mkdir()
shutil.copy2(runtime,work/'mods/zombierool-26.7.jar')
shutil.copy2(OUT/'probe-srg.jar',work/'mods/strathprobe.jar')
instance=WORLD.parent.parent
animation=next(p for p in (instance/'mods').glob('*.jar') if 'anim' in p.name.lower())
shutil.copy2(animation,work/'mods'/animation.name)
launch=(base/'launch.args').read_text().replace('libraries/',str(base/'libraries').replace('\\','/')+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+str(base/'libraries').replace('\\','/'))
(work/'launch.args').write_text(launch)
log=OUT/('baseline.log' if '--baseline' in sys.argv else 'probe.log')
with log.open('w',encoding='utf8') as f:
 p=subprocess.Popen([JAVA,'@'+str(work/'launch.args')],cwd=work,stdout=f,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 print('Isolated map probe started; log:',log,flush=True)
 try:p.wait(timeout=300 if stress else 180)
 except subprocess.TimeoutExpired:p.terminate();p.wait()
result=log.read_text(encoding='utf8',errors='replace')
print('\n'.join(line for line in result.splitlines() if 'STRATH PROBE' in line or 'STRATH CHECK' in line or 'LOAD ' in line or 'BOX CHECK:' in line or 'BOX PASS:' in line or 'RISEN CHECK:' in line or 'RISEN PASS:' in line or 'BALANCE CHECK:' in line or 'BALANCE PASS:' in line))
if ('BALANCE PASS:' if balance else 'RISEN PASS:' if variants else 'BOX PASS:' if box else 'LOAD PASS:' if stress else 'STRATH PROBE COMPLETE') not in result:
 print('\n'.join(result.splitlines()[-45:]));raise SystemExit(1)
