"""Run recent patches against the actual release JAR in an isolated normal Forge server."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import os,subprocess,shutil,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'));import build_hotfix as h
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe')
OUT=ROOT/'build/recent-production';OUT.mkdir(exist_ok=True)
BASE=OUT/'forge-base'
lines=(ROOT/'build/motion-fix/all-compile.args').read_text().splitlines();cp=lines[lines.index('"-classpath"')+1].strip('"')
classes=OUT/'test-classes';classes.mkdir(exist_ok=True)
args=['-proc:none','-encoding','UTF-8','-classpath',str(ROOT/'build/motion-fix/all-classes')+';'+cp,'-d',str(classes),str(ROOT/'tools/tests/RecentFixesProductionTest.java')]
f=OUT/'compile.args';f.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
subprocess.run([JAVA.with_name('javac.exe'),'@'+str(f)],check=True)
official=OUT/'test-official.jar'
with ZipFile(official,'w',ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="actorproduction"\nversion="1"\ndisplayName="Recent patch production verification"\n')
forge=next(Path(p) for p in cp.split(';') if 'minecraft_user_repo' in p and 'mapped_official' in p and p.endswith('.jar'))
h.remap(official,OUT/'test-srg.jar',ROOT/'build/reobfShadowJar/mappings.tsrg',[ROOT/'build/motion-fix/production-official-classes.jar',forge],'recent-test-production-reobf')
server=Path.home()/'AppData/Local/ZombieRool/test-servers/recent-fixes-production'
server.mkdir(parents=True,exist_ok=True);(server/'mods').mkdir(exist_ok=True)
shutil.copy2(ROOT/'build/libs/zombierool-26.7.jar',server/'mods/zombierool-26.7.jar')
shutil.copy2(OUT/'test-srg.jar',server/'mods/actorproduction.jar')
animation=next(p for p in (Path.home()/'curseforge/minecraft/Instances/ZombieRool (no mods)/mods').glob('*.jar') if 'anim' in p.name.lower());shutil.copy2(animation,server/'mods'/animation.name)
(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('online-mode=false\nserver-port=0\nlevel-type=minecraft:flat\nmax-tick-time=-1\nview-distance=3\nsimulation-distance=3\n')
args=(BASE/'launch.args').read_text().replace('libraries/',str(BASE/'libraries').replace('\\','/')+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+str(BASE/'libraries').replace('\\','/'))
(server/'launch.args').write_text(args)
with (OUT/'server.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen([JAVA,'@'+str(server/'launch.args')],cwd=server,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Normal production Forge server started with the final release JAR.',flush=True)
    try:process.wait(timeout=150)
    except subprocess.TimeoutExpired:process.terminate();process.wait()
s=(OUT/'server.log').read_text(encoding='utf-8',errors='replace')
print('\n'.join(line for line in s.splitlines() if 'RECENT PRODUCTION' in line))
if 'RECENT PRODUCTION PASS:' not in s:print(s[-6500:]);raise SystemExit(1)
