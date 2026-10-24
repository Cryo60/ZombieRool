"""Verify the remapped release in an isolated normal Forge server, without TaCZ."""
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import os,json,subprocess,tempfile,shutil,sys
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe')
OUT=ROOT/'build/map-actors';BASE=Path.home()/'AppData/Local/ZombieRool/MultiplayerTest/server'
lines=(ROOT/'build/zombie-presets/lunar-corrections-compile.args').read_text().splitlines();cp=lines[lines.index('"-classpath"')+1].strip('"')
classes=OUT/'production-test-classes';classes.mkdir(exist_ok=True)
subprocess.run([JAVA.with_name('javac.exe'),'-proc:none','-encoding','UTF-8','-classpath',str(ROOT/'build/portable-equipment/runtime-official.jar')+';'+cp,'-d',str(classes),str(ROOT/'tools/tests/MapActorsProductionTest.java')],check=True)
official=OUT/'production-test-official.jar'
with ZipFile(official,'w',ZIP_DEFLATED) as archive:
 for path in classes.rglob('*.class'):archive.write(path,path.relative_to(classes).as_posix())
 archive.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="actorproduction"\nversion="1"\ndisplayName="Map actor production verification"\n')
args=json.loads((ROOT/'build/final-export/reobf-command.json').read_text());args[args.index('--input')+1]=str(official);args[args.index('--output')+1]=str(OUT/'production-test-srg.jar');args+=['--lib',str(ROOT/'build/portable-equipment/runtime-official.jar')]
remapper=Path.home()/'.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/ForgeAutoRenamingTool/1.0.2/ForgeAutoRenamingTool-1.0.2-all.jar'
with (OUT/'production-test-remap.log').open('w') as log:subprocess.run([JAVA,'-jar',remapper,*args],stdout=log,stderr=subprocess.STDOUT,check=True)
cache=Path(os.environ['LOCALAPPDATA'])/'ZombieRool/test-servers';cache.mkdir(parents=True,exist_ok=True);server=Path(tempfile.mkdtemp(prefix='map-actors-',dir=cache))
(server/'mods').mkdir();shutil.copy2(OUT/'zombierool-26.7-map-actors.jar',server/'mods/zombierool-26.7.jar');shutil.copy2(OUT/'production-test-srg.jar',server/'mods/actorproduction.jar')
animation=next((BASE/'mods').glob('*player*anim*.jar'));shutil.copy2(animation,server/'mods'/animation.name)
shutil.copytree(ROOT/'examples/stratholme',server/'world',dirs_exist_ok=True)
(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('online-mode=false\nserver-port=0\nlevel-type=minecraft:flat\nmax-tick-time=-1\n')
args=(BASE/'launch.args').read_text().replace('libraries/',str(BASE/'libraries').replace('\\','/')+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+str(BASE/'libraries').replace('\\','/'))
(server/'launch.args').write_text(args)
log=OUT/'production-server.log'
with log.open('w',encoding='utf-8') as stream:
 process=subprocess.Popen([JAVA,'@'+str(server/'launch.args')],cwd=server,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 print('Isolated production Forge server started.',flush=True)
 try:process.wait(timeout=180)
 except subprocess.TimeoutExpired:process.terminate();process.wait()
text=log.read_text(encoding='utf-8',errors='replace')
for line in text.splitlines():
 if 'ACTOR PRODUCTION' in line:print(line)
if 'ACTOR PRODUCTION PASS:' not in text:print('\n'.join(text.splitlines()[-45:]));sys.exit(1)
# Retain the log and JAR, remove only the verified disposable server path.
if server.parent.resolve()==cache.resolve() and server.name.startswith('map-actors-'):shutil.rmtree(server)
