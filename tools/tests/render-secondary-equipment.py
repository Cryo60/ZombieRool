"""Real client verification of secondary equipment in a disposable world copy."""
from pathlib import Path
import os,subprocess,sys,shutil,time
sys.stdout.reconfigure(encoding='utf8',errors='replace')
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/secondary-visuals';OUT.mkdir(exist_ok=True)
JAVA=Path('C:/Java/jdk-17.0.20.1+1/bin/java.exe');runtime=ROOT/'build/portable-equipment/runtime-official.jar'
mod=OUT/'test-mod';(mod/'META-INF').mkdir(parents=True,exist_ok=True);(mod/'META-INF/mods.toml').write_text('modLoader="javafml"\nloaderVersion="[47,)"\nlicense="test"\n[[mods]]\nmodId="secondaryvisual"\nversion="1"\ndisplayName="Secondary equipment visual verification"\n')
lines=(ROOT/'build/zombie-presets/lunar-client-compile.args').read_text().splitlines();cp=str(runtime)+';'+lines[lines.index('"-classpath"')+1].strip('"');args=['-proc:none','-encoding','UTF-8','-classpath',cp,'-d',str(mod),str(ROOT/'tools/tests/SecondaryEquipmentVisualTest.java')];f=OUT/'compile.args';f.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args));subprocess.run([JAVA.with_name('javac.exe'),'@'+str(f)],cwd=ROOT,check=True)
client=OUT/('client-'+str(int(time.time())));save=client/'saves/SecondaryFixture';shutil.copytree(ROOT/'run/saves/ZombieRool - Les Ateliers v2',save,ignore=shutil.ignore_patterns('session.lock','playerdata','stats','advancements'))
(client/'options.txt').write_text('soundCategory_master:0.0\nfullscreen:false\noverrideWidth:1280\noverrideHeight:720\nguiScale:2\nrenderDistance:5\nsimulationDistance:5\nlang:fr_fr\ntutorialStep:none\n')
env=os.environ.copy();env['MOD_CLASSES']='zombierool%%'+str(runtime)+';secondaryvisual%%'+str(mod)+';tacz%%'+str(ROOT/'build/dev-26.7/client-tacz-with-bedrock.jar');env['MCP_MAPPINGS']='official_1.20.1'
args=(ROOT/'build/dev-26.7/client-test.args').read_text();args=args.replace('D:/Dev/ZombieRool/build/dev-26.7/client-render-test/test-home',str(OUT/'test-home').replace('\\','/'));args+='\n"--width"\n"1280"\n"--height"\n"720"\n';f=OUT/'client.args';f.write_text(args)
with (OUT/'client.log').open('w',encoding='utf8') as log:
    p=subprocess.Popen([JAVA,'@'+str(f)],cwd=client,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print('Isolated secondary equipment client started.',flush=True)
    try:p.wait(timeout=240)
    except subprocess.TimeoutExpired:p.terminate();p.wait()
text=(OUT/'client.log').read_text(encoding='utf8',errors='replace')
for line in text.splitlines():
    if 'SECONDARY VISUAL' in line:print(line)
if 'SECONDARY VISUAL PASS:' not in text:print('\n'.join(text.splitlines()[-70:]));sys.exit(1)
