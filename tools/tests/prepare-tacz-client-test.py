"""Launch a disposable client for real Mixin/GL checks; never opens a user world."""
from pathlib import Path
import sys,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h
scratch=ROOT/'build/dev-26.7'
args=(h.SCRATCH/'267-test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
tacz=next(Path('C:/Users/ecorange1/.gradle/caches/forge_gradle/deobf_dependencies/blank/tacz/1.20.1-1.1.8-hotfix2_mapped_official_1.20.1').glob('*.jar'))
original_tacz=tacz
tacz=scratch/'client-tacz-mapped.jar'
h.remap(ROOT/'dev-libs/tacz-1.20.1-1.1.8-hotfix2.jar',tacz,ROOT/'build/createSrgToMcp/output.srg',[h.SRG_FORGE],'267-client-tacz-remap')
embedded=[]
with zipfile.ZipFile(tacz) as archive:
 for name in archive.namelist():
  if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
   destination=scratch/Path(name).name;destination.write_bytes(archive.read(name));embedded.append(str(destination))
h.compile_sources([ROOT/'tools/tests/DevelopmentMixinNames.java'],scratch/'patch-tools',cp,'267-client-mixin-names')
patched_tacz=scratch/'client-tacz-test-mapped.jar'
h.run([h.JAVA,'-cp',str(scratch/'patch-tools')+';'+cp,'DevelopmentMixinNames',tacz,patched_tacz,ROOT/'build/createSrgToMcp/output.srg','com/tacz/guns/'],'267-client-tacz-mixins.log')
tacz=patched_tacz
bedrock=scratch/'simplebedrockmodel-2.2.2-forge+mc1.20.1.jar'
bedrock_mapped=scratch/'client-bedrock-mapped.jar'
bedrock_test=scratch/'client-bedrock-test.jar'
h.remap(bedrock,bedrock_mapped,ROOT/'build/createSrgToMcp/output.srg',[h.SRG_FORGE],'267-client-bedrock-remap')
h.run([h.JAVA,'-cp',str(scratch/'patch-tools')+';'+cp,'DevelopmentMixinNames',bedrock_mapped,bedrock_test,ROOT/'build/createSrgToMcp/output.srg',''],'267-client-bedrock-mixins.log')
rewritten=scratch/'client-tacz-with-bedrock.jar'
with zipfile.ZipFile(tacz) as source,zipfile.ZipFile(rewritten,'w',zipfile.ZIP_DEFLATED) as target:
 for entry in source.infolist():
  target.writestr(entry,bedrock_test.read_bytes() if entry.filename=='META-INF/jarjar/'+bedrock.name else source.read(entry))
tacz=rewritten
cp=';'.join(part for part in cp.split(';') if 'tacz' not in Path(part).name.lower())+';'+str(tacz)+';'+';'.join(embedded);out=scratch/'client-test-mod'
h.compile_sources([ROOT/'tools/tests/GunModeSwitchClientTest.java',ROOT/'tools/tests/SpecialEnemyClientTest.java',ROOT/'tools/tests/TacZClientRenderTest.java',ROOT/'tools/tests/FeedbackShaderScene.java',ROOT/'tools/tests/CareerPersistenceTest.java',ROOT/'tools/tests/PlayerAnimationRenderTest.java',ROOT/'tools/tests/KnuckleClientTest.java',ROOT/'tools/tests/Oct14ClientTest.java',ROOT/'tools/tests/EquipmentClientRenderTest.java',ROOT/'tools/tests/SoulObjectiveClientTest.java',ROOT/'tools/tests/Oct12ClientTest.java'],out,cp,'267-client-javac')
(out/'META-INF').mkdir(exist_ok=True);shutil.copy2(scratch/'test-mod/META-INF/mods.toml',out/'META-INF/mods.toml')
client=scratch/'client-render-test';client.mkdir(exist_ok=True)
world=client/'saves/AnimationFixture';world.mkdir(parents=True,exist_ok=True)
if not (world/'level.dat').exists():shutil.copy2(scratch/'test-world/world/level.dat',world/'level.dat')
if not (client/'tacz').exists():shutil.copytree(scratch/'tacz-server/tacz',client/'tacz')
(client/'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\noverrideWidth:640\noverrideHeight:480\nguiScale:2\n')
env=json.loads((scratch/'tacz-env.json').read_text());env['MOD_CLASSES']=env['MOD_CLASSES'].replace(str(scratch/'tacz-test-mod'),str(out)).replace(str(original_tacz),str(tacz))
(scratch/'client-env.json').write_text(json.dumps(env))
lines=(ROOT/'build/verify-fixes/runClient1-hotfix1.args').read_text().splitlines()
i=lines.index('"--quickPlayMultiplayer"');del lines[i:i+2]
lines=[l.replace('ignoreRequired=true','ignoreRequired=false').replace('disableRefMap=true','disableRefMap=false') for l in lines]
test_home=client/'test-home';test_home.mkdir(exist_ok=True)
lines.insert(0,'"-Duser.home='+str(test_home).replace('\\','/')+'"')
(scratch/'client-test.args').write_text('\n'.join(lines)+'\n')
(scratch/'run-client-test.py').write_text('''import os,json,subprocess,time
from pathlib import Path
root=Path('D:/Dev/ZombieRool');scratch=root/'build/dev-26.7'
env=os.environ.copy();env.update(json.loads((scratch/'client-env.json').read_text()))
log=scratch/'client-test.log'
with log.open('w') as output:
 p=subprocess.Popen(['C:/Java/jdk-17.0.20.1+1/bin/java.exe','@'+str(scratch/'client-test.args')],cwd=scratch/'client-render-test',env=env,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 start=time.monotonic()
 while p.poll() is None and time.monotonic()-start<180:time.sleep(1)
 if p.poll() is None:p.terminate();p.wait()
text=log.read_text(errors='replace')
for line in text.splitlines():
 if 'TACZ CLIENT' in line or 'CAREER ' in line or 'ANIMATION ' in line or 'EQUIPMENT ' in line:print(line)
if 'TACZ CLIENT RENDER PASS' not in text:
 print('\\n'.join(text.splitlines()[-35:]));raise SystemExit(1)
''')
print('Prepared isolated real client render check.')
