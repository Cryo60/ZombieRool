from pathlib import Path
import json,sys,shutil
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h
scratch=ROOT/'build/dev-26.7'
args=(h.SCRATCH/'267-test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
out=scratch/'pursuit-test-mod';h.compile_sources([ROOT/'tools/tests/PursuitRegressionTest.java'],out,cp,'267-pursuit-javac')
(out/'META-INF').mkdir(exist_ok=True);shutil.copy2(scratch/'test-mod/META-INF/mods.toml',out/'META-INF/mods.toml')
env=json.loads((scratch/'test-env.json').read_text());env['MOD_CLASSES']=env['MOD_CLASSES'].replace(str(scratch/'test-mod'),str(out));(scratch/'pursuit-env.json').write_text(json.dumps(env))
server=scratch/'pursuit-server';server.mkdir(exist_ok=True);(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('level-name=world\nserver-port=25590\nonline-mode=false\nallow-nether=false\nenable-command-block=false\nmax-tick-time=180000\n')
runner=(scratch/'run-server-test.py').read_text().replace('test-env.json','pursuit-env.json').replace('server-test.log','pursuit-test.log').replace('test-world','pursuit-server').replace("'26.7 CHECK:' in line or '26.7 FEATURE PASS' in line or 'HOTFIX RUNTIME PASS' in line","'PURSUIT' in line").replace('HOTFIX RUNTIME PASS','PURSUIT REGRESSION PASS')
(scratch/'run-pursuit-test.py').write_text(runner)
