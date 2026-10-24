"""Prepare a remapped development JAR and isolated Forge behavior tests, without touching an instance."""
from pathlib import Path
import json,sys,shutil

ROOT=Path(__file__).resolve().parents[2]
folder=ROOT/'hotfixes/1.6.6-hotfix1'
sys.path.insert(0,str(folder))
import build_hotfix as h

scratch=ROOT/'build/dev-26.7'
scratch.mkdir(exist_ok=True)
if '--fixtures-only' not in sys.argv:
    h.remap(ROOT/'build/libs/zombierool-26.7-dev.jar',scratch/'mapped.jar',ROOT/'build/createSrgToMcp/output.srg',[h.SRG_FORGE,ROOT/'dev-libs/tacz-1.20.1-1.1.8-hotfix2.jar'],'remap-267')
    args=(h.SCRATCH/'checks-javac.args').read_text().splitlines()
    cp=args[args.index('"-classpath"')+1].strip('"')
    h.compile_sources([ROOT/'tools/tests/DevelopmentMixinNames.java'],scratch/'patch-tools',cp,'267-test-mixin-javac')
    h.run([h.JAVA,'-cp',str(scratch/'patch-tools')+';'+cp,'DevelopmentMixinNames',scratch/'mapped.jar',scratch/'test-mapped.jar',ROOT/'build/createSrgToMcp/output.srg'],'267-dev-mixin.log')
names=('HotfixRuntimeTest','HotfixExtendedTests','PrototypeSequenceTest','MatchConfigRestartTest','TextureSpawnerRuntimeTest')
sources=[]
for name in names:
    text=(folder/(name+'.java')).read_text()
    text=text.replace('check(!ForgeRegistries.BLOCKS.containsKey','check(ForgeRegistries.BLOCKS.containsKey').replace('New teleporter registry is absent','Development teleporter registry is preserved').replace('New traversal registry is absent','Development traversal registry is preserved')
    text=text.replace('me.cryo.zombierool.hotfix.HotfixGameplay','me.cryo.zombierool.gameplay.CompatibilityFixes').replace('HotfixGameplay.','CompatibilityFixes.')
    text=text.replace('me.cryo.zombierool.hotfix.HotfixMatchConfig','me.cryo.zombierool.gameplay.MatchConfigSnapshot').replace('HotfixMatchConfig','MatchConfigSnapshot')
    text=text.replace('me.cryo.zombierool.shadow.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(probe)','me.cryo.zombierool.scripting.LegacySoundBindings.wrap(probe)')
    text=text.replace('System.out.println("HOTFIX RUNTIME PASS");','Feature267Test.run(level,a,b);\n            System.out.println("HOTFIX RUNTIME PASS");')
    if name=='TextureSpawnerRuntimeTest':
        text=text.replace('original.setConfig(type,', 'if(type.boss()){var selection=new net.minecraft.nbt.CompoundTag();selection.putString("type",type.getSerializedName());me.cryo.zombierool.config.SpecialEnemyConfig.update(level,selection);} original.setConfig(type,')
    source=scratch/(name+'.java');source.write_text(text,encoding='utf-8');sources.append(source)
sources.append(ROOT/'tools/tests/Feature267Test.java')
sources.append(ROOT/'tools/tests/Oct13RegressionTest.java')
sources.append(ROOT/'tools/tests/Oct14GameplayTest.java')
sources.append(ROOT/'tools/tests/Oct14DebugGameplayTest.java')
sources.append(ROOT/'tools/tests/Oct14RedistributionTest.java')
sources.append(ROOT/'tools/tests/Oct14FollowupGameplayTest.java')
sources.append(ROOT/'tools/tests/MiniBossMapConfigTest.java')
sources.append(ROOT/'tools/tests/BossBehaviorTest.java')
sources.append(ROOT/'tools/tests/BossPursuitMatrixTest.java')
sources.append(ROOT/'tools/tests/SoulObjectiveGameplayTest.java')
sources.append(ROOT/'tools/tests/EquipmentGameplayTest.java')
sources.append(ROOT/'tools/tests/WonderWeaponGameplayTest.java')
sources.append(ROOT/'tools/tests/NavigationReviewTest.java')
sources.append(ROOT/'tools/tests/Oct12GameplayTest.java')
sources.append(ROOT/'tools/tests/SpecialEnemyGameplayTest.java')
args=(h.SCRATCH/'test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
for name in ['hotfix-mapped.jar','hotfix-test-mapped.jar']:
    cp=cp.replace(str(h.SCRATCH/name).replace('\\','/'),str(scratch/'test-mapped.jar').replace('\\','/'))
h.compile_sources(sources,scratch/'test-mod',cp,'267-test-javac')
(scratch/'test-mod/META-INF').mkdir(exist_ok=True)
shutil.copy2(h.SCRATCH/'test-mod/META-INF/mods.toml',scratch/'test-mod/META-INF/mods.toml')
(scratch/'test-mod/pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'Isolated gameplay test loot fixtures'}}))
loot=scratch/'test-mod/data/zombierool_test/loot_tables';loot.mkdir(parents=True,exist_ok=True)
for name,item,count in [('boss_first','minecraft:apple',1),('boss_recurring','minecraft:bone',2)]:
    (loot/(name+'.json')).write_text(json.dumps({'type':'minecraft:entity','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':item,'functions':[{'function':'minecraft:set_count','count':count}]}]}]}))
definitions=scratch/'test-mod/data/zombierool_test/mini_bosses';definitions.mkdir(parents=True,exist_ok=True)
(definitions/'warden.json').write_text(json.dumps({'template':'brutus','name':'Warden test','health':320,'speed':.42,'damage':3,'texture':'zombierool:textures/entities/brutus_minecraft_3.png'}))
env=json.loads((h.SCRATCH/'test-env.json').read_text())
for old,new in [(h.SCRATCH/'hotfix-test-mapped.jar',scratch/'test-mapped.jar'),(h.SCRATCH/'test-mod',scratch/'test-mod')]:
    env['MOD_CLASSES']=env['MOD_CLASSES'].replace(str(old),str(new)).replace(str(old).replace('\\','/'),str(new).replace('\\','/'))
(scratch/'test-env.json').write_text(json.dumps(env))
world=scratch/'test-world';world.mkdir(exist_ok=True)
for name in ['eula.txt','server.properties']:
    shutil.copy2(h.SCRATCH/'test-world'/name,world/name)
runner=(h.SCRATCH/'run-server-test.py').read_text().replace('build/hotfix-1.6.6-hotfix1/test-env.json','build/dev-26.7/test-env.json').replace('build/hotfix-1.6.6-hotfix1/server-test.log','build/dev-26.7/server-test.log').replace('build/hotfix-1.6.6-hotfix1/test-world','build/dev-26.7/test-world')
runner=runner.replace("'HOTFIX CHECK:' in line or 'HOTFIX RUNTIME PASS' in line","'26.7 CHECK:' in line or '26.7 FEATURE PASS' in line or 'HOTFIX RUNTIME PASS' in line")
(scratch/'run-server-test.py').write_text(runner.replace('start<150','start<600'))
print('Prepared 26.7 tests in an isolated world.')
