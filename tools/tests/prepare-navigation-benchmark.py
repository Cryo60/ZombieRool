from pathlib import Path
import json,sys,shutil
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'hotfixes/1.6.6-hotfix1'))
import build_hotfix as h
scratch=ROOT/'build/dev-26.7';mode=sys.argv[1] if len(sys.argv)>1 else 'after'
args=(h.SCRATCH/'267-test-javac.args').read_text().splitlines();cp=args[args.index('"-classpath"')+1].strip('"')
out=scratch/'navigation-bench-mod';h.compile_sources([ROOT/'tools/tests/NavigationBenchmark.java'],out,cp,'267-navigation-bench-javac')
(out/'META-INF').mkdir(exist_ok=True);shutil.copy2(scratch/'test-mod/META-INF/mods.toml',out/'META-INF/mods.toml')
env=json.loads((scratch/'test-env.json').read_text());env['MOD_CLASSES']=env['MOD_CLASSES'].replace(str(scratch/'test-mod'),str(out))
if mode=='before':
 shutil.copy2(scratch/'test-mapped.jar',scratch/'navigation-before.jar')
 env['MOD_CLASSES']=env['MOD_CLASSES'].replace('test-mapped.jar','navigation-before.jar')
(scratch/f'navigation-{mode}-env.json').write_text(json.dumps(env))
server=scratch/f'navigation-bench-{mode}';server.mkdir(exist_ok=True);(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('level-name=world\nserver-port=25592\nonline-mode=false\nallow-nether=false\nmax-tick-time=180000\n')
runner=(scratch/'run-server-test.py').read_text().replace('test-env.json',f'navigation-{mode}-env.json').replace('server-test.log',f'navigation-{mode}.log').replace('test-world',f'navigation-bench-{mode}').replace("'26.7 CHECK:' in line or '26.7 FEATURE PASS' in line or 'HOTFIX RUNTIME PASS' in line","'NAV BENCH' in line").replace('HOTFIX RUNTIME PASS','NAV BENCH PASS').replace('start<150','start<300')
(scratch/f'run-navigation-{mode}.py').write_text(runner)
