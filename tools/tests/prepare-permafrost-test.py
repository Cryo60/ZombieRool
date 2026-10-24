"""Prepare the supplied map as an isolated server fixture. Never changes a user world."""
from pathlib import Path
import json,sys,shutil,zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h
scratch=ROOT/'build/dev-26.7'
args=(h.SCRATCH/'test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
for name in ['hotfix-mapped.jar','hotfix-test-mapped.jar']:cp=cp.replace(str(h.SCRATCH/name).replace('\\','/'),str(scratch/'test-mapped.jar').replace('\\','/'))
out=scratch/'permafrost-test-mod'
h.compile_sources([ROOT/'tools/tests/PermafrostNavigationTest.java'],out,cp,'267-permafrost-javac')
(out/'META-INF').mkdir(exist_ok=True);shutil.copy2(scratch/'test-mod/META-INF/mods.toml',out/'META-INF/mods.toml')
new_world='--new-world' in sys.argv
second_zipline='--second-zipline' in sys.argv
server=scratch/('new-world-second-zipline-server' if second_zipline else 'new-world-choice-server' if new_world else 'permafrost-server');world=(server/'world').resolve();world.mkdir(parents=True,exist_ok=True)
if not (world/'level.dat').exists():
    if new_world:shutil.copytree(ROOT/'run/saves/New World',world,dirs_exist_ok=True)
    else:
        with zipfile.ZipFile('C:/Users/ecorange1/Desktop/Nouveau dossier/Permafrost 1.0.0 Release - By Diegeau.zip') as z:
            if not all((world/info.filename).resolve().is_relative_to(world) for info in z.infolist()):raise ValueError('Unsafe archive paths')
            z.extractall(world)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('level-name=world\nserver-port=25588\nonline-mode=false\nallow-nether=false\nenable-command-block=false\nspawn-protection=0\nview-distance=6\nsimulation-distance=6\nmax-tick-time=180000\n')
env=json.loads((scratch/'test-env.json').read_text());env['MOD_CLASSES']=env['MOD_CLASSES'].replace(str(scratch/'test-mod'),str(out))
if new_world:env['ZR_NAV_NEW_WORLD']='1'
if second_zipline:env['ZR_NAV_SECOND_ZIPLINE']='1'
(scratch/'permafrost-env.json').write_text(json.dumps(env))
runner=(scratch/'run-server-test.py').read_text().replace('test-env.json','permafrost-env.json').replace('server-test.log','permafrost-test.log').replace('test-world','permafrost-server').replace("'26.7 CHECK:' in line or '26.7 FEATURE PASS' in line or 'HOTFIX RUNTIME PASS' in line","'PERMAFROST' in line").replace('HOTFIX RUNTIME PASS','PERMAFROST PROBE COMPLETE').replace('start<150','start<240')
if new_world:runner=runner.replace('permafrost-server','new-world-second-zipline-server' if second_zipline else 'new-world-choice-server')
if new_world:runner=runner.replace('permafrost-test.log','second-zipline-test.log' if second_zipline else 'new-world-test.log')
(scratch/'run-permafrost-test.py').write_text(runner)
print('Prepared map navigation probe against an isolated world copy.')
