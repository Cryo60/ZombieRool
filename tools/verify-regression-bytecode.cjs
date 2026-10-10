const fs=require('fs'),{execFileSync}=require('child_process'),assert=require('node:assert/strict');
const javap='C:/Java/jdk-17.0.20.1+1/bin/javap.exe';
const args=fs.readFileSync('build/verify-fixes/javac.args','utf8').split(/\r?\n/);const cp=args[args.indexOf('-classpath')+1].replace(/^"|"$/g,'');const forge=cp.split(';').find(p=>p.includes('minecraft_user_repo')&&p.endsWith('.jar'));
function dump(jar,cls){return execFileSync(javap,['-c','-p','-classpath',jar,cls],{encoding:'utf8'});}
function method(text,name){const start=text.indexOf(' '+name+'(');assert(start>=0,'Missing method '+name);const next=text.indexOf('\n  public ',start+1),nextPrivate=text.indexOf('\n  private ',start+1);const ends=[next,nextPrivate].filter(x=>x>start);return text.slice(start,ends.length?Math.min(...ends):undefined);}
const tacz=dump('dev-libs/tacz-1.20.1-1.1.7-hotfix.jar','com.tacz.guns.client.sound.SoundPlayManager');const reload=method(tacz,'playReloadSound');assert.equal((reload.match(/invokestatic.*Method playClientSound:/g)||[]).length,2,'Both reload branches must be intercepted');
const food=method(dump(forge,'net.minecraft.world.food.FoodData'),'tick');assert.equal((food.match(/invokevirtual.*Player.heal:\(F\)V/g)||[]).length,2,'Both vanilla food healing paths must be intercepted');
const config=JSON.parse(fs.readFileSync('src/main/resources/mixins.zombierool.json'));assert(config.client.includes('TacZReloadSoundMixin'));assert(config.mixins.includes('FoodDataMixin'));
const lang=JSON.parse(fs.readFileSync('src/main/resources/assets/zombierool/lang/en_us.json'));assert.equal(lang['message.zombierool.trap.activate'],'Press [%s] to buy %s [Cost: %s]');
console.log('Verified both real TacZ reload call sites, both vanilla food-healing call sites, mixin registration and purchase prompt.');
