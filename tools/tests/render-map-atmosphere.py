"""Verify compositing against a real client, optionally with the user's existing shader pack."""
from pathlib import Path
import os,subprocess,sys,shutil,time,tempfile
from zipfile import ZipFile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ROOT=Path(__file__).resolve().parents[2];shader=next((arg for arg in sys.argv[1:] if not arg.startswith('--')),'');name=('scenery-photon' if shader else 'scenery') if '--scenery' in sys.argv else ('ambient-particles-photon' if shader else 'ambient-particles') if '--ambient-particles' in sys.argv else 'turret' if '--turret' in sys.argv else ('sky-objects-photon' if shader else 'sky-objects') if '--sky-objects' in sys.argv else ('hand-revision-photon' if shader else 'hand-revision') if '--hand-revision' in sys.argv else 'hand-actions' if '--hand-actions' in sys.argv else ('origins-photon' if shader else 'origins') if '--origins' in sys.argv else ('hellhound-fog-photon' if shader else 'hellhound-fog') if '--hellhound-fog' in sys.argv else 'delivery' if '--delivery' in sys.argv else ('blood-death-photon' if shader else 'blood-death') if '--blood-death' in sys.argv else 'suits' if '--suits' in sys.argv else 'bo3' if '--bo3' in sys.argv else ('cross-photon' if shader else 'cross') if '--cross' in sys.argv else 'imports' if '--imports' in sys.argv else ('authoring-photon' if shader else 'authoring') if '--authoring' in sys.argv else 'photon' if shader else 'plain';name='vehicle-movement' if '--vehicle-movement' in sys.argv else name;name='combat-effects' if '--combat-effects' in sys.argv else name;name='vehicles-photon' if '--vehicles' in sys.argv and shader else 'vehicles' if '--vehicles' in sys.argv else name;name='halo' if '--halo' in sys.argv else name;name='actual-map-profile' if '--actual-profile' in sys.argv else name;name='enemy-encounters' if '--enemies' in sys.argv else name;name='cars-photon' if '--cars' in sys.argv and shader else 'cars' if '--cars' in sys.argv else name;name='cerberus-scourge' if '--cerberus-scourge' in sys.argv else name;name='furniture-transports' if '--furniture-transports' in sys.argv else name;name='furniture-revision' if '--furniture-revision' in sys.argv else name;name='furniture-followup' if '--furniture-followup' in sys.argv else name;name=('feedback-revision-photon' if shader else 'feedback-revision') if '--feedback-revision' in sys.argv else 'revision-menu' if '--revision-menu' in sys.argv else name;name=('combat-followup-photon' if shader else 'combat-followup') if '--combat-followup' in sys.argv else 'career-no-tacz' if '--career-no-tacz' in sys.argv else name;name='repair-audio' if '--repair-audio' in sys.argv else name;name='map-actors' if '--actors' in sys.argv else name;name='map-actors-cleanup' if '--cleanup-only' in sys.argv else 'map-actors-grip' if '--grip-only' in sys.argv else name
name='archer-grip' if '--archer-grip' in sys.argv else 'grip-melee' if '--grip-melee' in sys.argv else 'grip-feedback' if '--grip-feedback' in sys.argv else name
name='spawner-editor-fix' if '--balance-editor' in sys.argv else 'dwarven-artillery' if '--siege' in sys.argv else name
OUT=ROOT/'build/map-atmosphere'/name;OUT.mkdir(parents=True,exist_ok=True)
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe');runtime=ROOT/'build/portable-equipment/runtime-official.jar'
if '--siege' in sys.argv or '--balance-editor' in sys.argv:runtime=ROOT/'build/stratholme-artillery/runtime-official.jar'
mod=OUT/'mod';(mod/'META-INF').mkdir(parents=True,exist_ok=True);(mod/'META-INF/mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="atmospherevisual"\nversion="1"\ndisplayName="Atmosphere visual checks"\n')
lines=(ROOT/'build/zombie-presets/lunar-client-compile.args').read_text().splitlines();cp=str(runtime)+';'+lines[lines.index('"-classpath"')+1].strip('"');args=['-proc:none','-encoding','UTF-8','-classpath',cp,'-d',str(mod),str(ROOT/('tools/tests/SpawnerEditorVisualTest.java' if '--balance-editor' in sys.argv else 'tools/tests/DwarvenArtilleryVisualTest.java' if '--siege' in sys.argv else 'tools/tests/ArcherGripVisualTest.java' if '--archer-grip' in sys.argv else 'tools/tests/GripFeedbackVisualTest.java' if '--grip-feedback' in sys.argv else 'tools/tests/MapActorsVisualTest.java' if '--actors' in sys.argv else 'tools/tests/RepairAudioVisualTest.java' if '--repair-audio' in sys.argv else 'tools/tests/CombatFollowupVisualTest.java' if '--combat-followup' in sys.argv else 'tools/tests/NoTacZCareerVisualTest.java' if '--career-no-tacz' in sys.argv else 'tools/tests/FeedbackRevisionVisualTest.java' if '--feedback-revision' in sys.argv else 'tools/tests/RevisionMenuVisualTest.java' if '--revision-menu' in sys.argv else 'tools/tests/FurnitureFollowupTest.java' if '--furniture-followup' in sys.argv else 'tools/tests/FurnitureRevisionVisualTest.java' if '--furniture-revision' in sys.argv else 'tools/tests/FurnitureTransportVisualTest.java' if '--furniture-transports' in sys.argv else 'tools/tests/CerberusScourgeVisualTest.java' if '--cerberus-scourge' in sys.argv else 'tools/tests/CarDecorVisualTest.java' if '--cars' in sys.argv else 'tools/tests/EnemyEncounterVisualTest.java' if '--enemies' in sys.argv else 'tools/tests/VehicleMovementVisualTest.java' if '--vehicle-movement' in sys.argv else 'tools/tests/CombatEffectsVisualTest.java' if '--combat-effects' in sys.argv else 'tools/tests/VehicleDecorVisualTest.java' if '--vehicles' in sys.argv else 'tools/tests/HaloPresetsVisualTest.java' if '--halo' in sys.argv else 'tools/tests/ActualMapProfileTest.java' if '--actual-profile' in sys.argv else 'tools/tests/AmbientSceneryVisualTest.java' if '--scenery' in sys.argv else 'tools/tests/AmbientParticlesVisualTest.java' if '--ambient-particles' in sys.argv else 'tools/tests/PortableTurretVisualTest.java' if '--turret' in sys.argv else 'tools/tests/SkyObjectsVisualTest.java' if '--sky-objects' in sys.argv else 'tools/tests/HandActionRevisionTest.java' if '--hand-revision' in sys.argv else 'tools/tests/HandActionsVisualTest.java' if '--hand-actions' in sys.argv else 'tools/tests/OriginsVisualTest.java' if '--origins' in sys.argv else 'tools/tests/HellhoundFogVisualTest.java' if '--hellhound-fog' in sys.argv else 'tools/tests/MachineDeliveryVisualTest.java' if '--delivery' in sys.argv else 'tools/tests/BloodDeathSkyboxTest.java' if '--blood-death' in sys.argv else 'tools/tests/EnvironmentalSuitVisualTest.java' if '--suits' in sys.argv else 'tools/tests/Bo3PresetsVisualTest.java' if '--bo3' in sys.argv else 'tools/tests/MapAtmosphereImportsTest.java' if '--imports' in sys.argv else 'tools/tests/MapAtmosphereAuthoringTest.java' if '--authoring' in sys.argv else 'tools/tests/MapAtmosphereVisualTest.java'))];f=OUT/'compile.args';f.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args));subprocess.run([JAVA.with_name('javac.exe'),'@'+str(f)],cwd=ROOT,check=True)
clientCache=Path(os.environ.get('LOCALAPPDATA',tempfile.gettempdir()))/'ZombieRool/test-clients';clientCache.mkdir(parents=True,exist_ok=True)
client=Path(tempfile.mkdtemp(prefix=name+'-',dir=clientCache));save=client/'saves/AtmosphereFixture';source=ROOT/'run/saves/Atelier - Landing' if '--actual-profile' in sys.argv else Path((ROOT/'build/map-atmosphere/demo/installed-map.txt').read_text(encoding='utf-8'));
archive=ROOT/'build/map-atmosphere/demo/atmosphere-fixture.zip'
if '--actual-profile' in sys.argv and not source.exists():raise FileNotFoundError(source)
if not source.exists() and archive.exists():
 with ZipFile(archive) as fixture:fixture.extractall(save)
else:
 if not source.exists():
  fixtures=[p.parent for p in (ROOT/'build/map-atmosphere').glob('*/client-*/saves/AtmosphereFixture/level.dat') if p.parent!=save]
  if not fixtures:raise FileNotFoundError('No isolated atmosphere fixture available')
  source=max(fixtures,key=lambda p:(p/'level.dat').stat().st_mtime)
 shutil.copytree(source,save,ignore=shutil.ignore_patterns('session.lock','playerdata','stats','advancements'))
if '--balance-editor' in sys.argv:
 shutil.copytree(ROOT/'examples/stratholme/zombierool/gameplay/entities',save/'zombierool/gameplay/entities',dirs_exist_ok=True)
if '--actors' in sys.argv:
 shutil.copytree(ROOT/'examples/stratholme/zombierool',save/'zombierool',dirs_exist_ok=True)
 music=Path('C:/Users/ecorange1/curseforge/minecraft/Instances/ZombieRool (no mods)/saves/Stratholme/zombierool/assets/audio/music/default')
 shutil.copytree(music,save/'zombierool/assets/audio/music/default',dirs_exist_ok=True)
 shutil.copytree(music,save/'zombierool/assets/audio/music/tavern',dirs_exist_ok=True)
# Dynamic map-local fixtures deliberately avoid resources.zip.
assert '--actual-profile' in sys.argv or not (save/'resources.zip').exists()
folder=save/'zombierool/assets/skyboxes';folder.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/'src/main/resources/assets/zombierool/textures/environment/ashfall_sky.png',folder/'fixture.png')
folder=save/'zombierool/audio/custom';folder.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/'src/main/resources/assets/zombierool/sounds/environment/ambient_distant_alarm_0.ogg',folder/'fixture.ogg')
if '--combat-followup' in sys.argv:
 pack=Path('C:/Users/ecorange1/curseforge/minecraft/Instances/ZombieRool/tacz/elitex_quality_guns')
 if pack.exists():
  (client/'tacz').mkdir(exist_ok=True)
  subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(client/'tacz/elitex_quality_guns')+"' -Target '"+str(pack)+"' | Out-Null"],check=True)
  enlisted=pack.parent/'[TaCZ] Enlisted Gun Pack v1.2.1.3.zip'
  if enlisted.exists():shutil.copy2(enlisted,client/'tacz'/enlisted.name)
  powder=pack.parent/'GunpowderRevolution_gunpack v1.2.7.zip'
  if powder.exists():shutil.copy2(powder,client/'tacz'/powder.name)
  cirrus=pack.parent/'lradd_default_gun'
  if cirrus.exists():subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(client/'tacz/lradd_default_gun')+"' -Target '"+str(cirrus)+"' | Out-Null"],check=True)

(client/'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\noverrideWidth:1280\noverrideHeight:720\nguiScale:2\nrenderDistance:6\nsimulationDistance:5\nlang:fr_fr\ntutorialStep:none\n')
if '--actual-profile' in sys.argv:
 current=(ROOT/'run/options.txt').read_text(encoding='utf-8');keys=('renderDistance:','simulationDistance:','enableVsync:','maxFps:','mipmapLevels:','fov:','biomeBlendRadius:','graphicsMode:','renderClouds:','entityDistanceScaling:')
 selected=[line for line in current.splitlines() if line.startswith(keys)]
 (client/'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\noverrideWidth:1920\noverrideHeight:1080\nguiScale:2\nlang:fr_fr\ntutorialStep:none\n'+'\n'.join(selected)+'\n')
(client/'config').mkdir();(client/'config/oculus.properties').write_text('enableShaders='+('true' if shader else 'false')+'\nshaderPack='+shader+'\n')
if shader:
 (client/'shaderpacks').mkdir();shutil.copy2(ROOT/'run/shaderpacks'/shader,client/'shaderpacks'/shader)
 settingsFile=ROOT/'run/shaderpacks'/(shader+'.txt')
 if settingsFile.exists():shutil.copy2(settingsFile,client/'shaderpacks'/(shader+'.txt'))
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(runtime)+';atmospherevisual%%'+str(mod)+';tacz%%'+str(ROOT/'build/dev-26.7/client-tacz-with-bedrock.jar');env['MCP_MAPPINGS']='official_1.20.1'
if '--career-no-tacz' in sys.argv:env['MOD_CLASSES']='zombierool%%'+str(runtime)+';atmospherevisual%%'+str(mod)
args=(ROOT/'build/dev-26.7/client-test.args').read_text().replace('D:/Dev/ZombieRool/build/dev-26.7/client-render-test/test-home',str(OUT/'test-home').replace('\\','/'));args=('"-Datmosphere.actualWorld='+str(save).replace('\\','/')+'"\n')+('"-Datmosphere.presetEditingOnly='+str('--preset-editing' in sys.argv).lower()+'"\n')+('"-Datmosphere.skyInput=C:/Users/ecorange1/Downloads/skybox_lune_croix.png"\n' if '--cross' in sys.argv else '')+'"-Datmosphere.listOnly='+str('--sound-list' in sys.argv).lower()+'"\n'+ '"-Datmosphere.output='+str(OUT).replace('\\','/')+'"\n"-Datmosphere.shaders='+str(bool(shader)).lower()+'"\n'+args+'\n"--width"\n"1280"\n"--height"\n"720"\n';
if '--career-no-tacz' in sys.argv:
 import re
 args=re.sub(r'[^;"\n]*tacz[^;"\n]*\.jar;?', '', args,flags=re.IGNORECASE)
if '--cleanup-only' in sys.argv:args='"-Datmosphere.cleanupOnly=true"\n'+args
if '--grip-melee' in sys.argv:args='"-Datmosphere.meleeGrips=true"\n'+args
if '--grip-only' in sys.argv:args='"-Datmosphere.gripOnly=true"\n'+args
f=OUT/'client.args';f.write_text(args)
with (OUT/'client.log').open('w',encoding='utf-8') as log:
 p=subprocess.Popen([JAVA,'@'+str(f)],cwd=client,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);print('Isolated atmosphere client:',name,flush=True)
 try:p.wait(timeout=480)
 except subprocess.TimeoutExpired:p.terminate();p.wait()
# Test worlds are disposable copies; retain evidence in OUT, never accumulate tiny files on exFAT.
if client.parent.resolve()==clientCache.resolve() and client.name.startswith(name+'-'):
 shutil.rmtree(client)
text=(OUT/'client.log').read_text(encoding='utf-8',errors='replace')
for line in text.splitlines():
 if 'ATMOSPHERE VISUAL' in line or 'ATMOSPHERE PIXEL' in line or 'ATMOSPHERE HAND PIXELS' in line or 'SCENERY GPU' in line or 'ACTUAL MAP ' in line:print(line)
if 'Depth formats do not match' in text:print('ATMOSPHERE VISUAL FAIL: incompatible framebuffer depth formats');sys.exit(1)
if 'ATMOSPHERE VISUAL PASS:' not in text:print('\n'.join(text.splitlines()[-70:]));sys.exit(1)
