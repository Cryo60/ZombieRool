import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
const folder=path.join(os.homedir(),'.zombierool','creator');
const privateFile=path.join(folder,'reward-private.pem');
const publicFile=path.resolve('src/main/resources/META-INF/zombierool/rewards/public-key.der');
const [command,...args]=process.argv.slice(2);
function option(key,fallback){const i=args.indexOf('--'+key);return i<0?fallback:args[i+1];}
function date(key){const value=option(key);if(!value)return undefined;const epoch=Math.floor(Date.parse(value)/1000);if(!Number.isFinite(epoch))throw Error('Invalid ISO date: '+key);return epoch;}
if(command==='init'){
 if(fs.existsSync(privateFile))throw Error('Issuer key already exists; it has not been replaced.');
 const keys=crypto.generateKeyPairSync('ed25519');fs.mkdirSync(folder,{recursive:true});fs.writeFileSync(privateFile,keys.privateKey.export({type:'pkcs8',format:'pem'}),{mode:0o600,flag:'wx'});fs.mkdirSync(path.dirname(publicFile),{recursive:true});fs.writeFileSync(publicFile,keys.publicKey.export({type:'spki',format:'der'}));console.log('Issuer key created outside the repository. Public verification key written to mod resources. Back up the creator folder privately.');
}else if(command==='issue'){
 const reward={camos:(option('camos','')).split(',').filter(Boolean),skins:(option('skins','')).split(',').filter(Boolean),zrf:Number(option('zrf','0')),xp:Number(option('xp','0'))};
 for(const amount of [reward.zrf,reward.xp])if(!Number.isInteger(amount)||amount<0||amount>1000000)throw Error('Invalid reward amount');
 const count=Number(option('count','1'));if(!Number.isInteger(count)||count<1||count>1000)throw Error('Count must be 1..1000');
 const source=fs.readFileSync('src/main/java/me/cryo/zombierool/client/career/CareerUnlockables.java','utf8');
 const catalog=new Map([...source.matchAll(/CAMOS\.put\("([a-z0-9_]+)",\s*new CamoDef\("[^"]+",\s*\d+,\s*(?:true|false),\s*\d+,\s*(?:true|false),\s*"([a-z_]+)"/g)].map(m=>[m[1],m[2]]));
 for(const camo of reward.camos)if(!catalog.has(camo))throw Error('Unknown camo: '+camo);
 const randomCount=Number(option('random-count','0'));if(!Number.isInteger(randomCount)||randomCount<0||randomCount>100)throw Error('Random count must be 0..100');
 const rarities=option('rarities','common,rare,epic,legendary').split(',');if(rarities.some(r=>!new Set(catalog.values()).has(r)))throw Error('Unknown rarity');
 const pool=[...catalog].filter(([id,rarity])=>rarities.includes(rarity)&&!reward.camos.includes(id)).map(([id])=>id);
 if(randomCount>pool.length)throw Error('Not enough different camos in the selected rarities');
 const key=crypto.createPrivateKey(fs.readFileSync(privateFile));const issued=[];
 for(let i=0;i<count;i++){const candidates=[...pool],random=[];for(let j=0;j<randomCount;j++)random.push(candidates.splice(crypto.randomInt(candidates.length),1)[0]);const body={id:crypto.randomUUID(),reward:{...reward,camos:[...new Set([...reward.camos,...random])]}};const target=option('target');if(target){if(!/^(?:[a-f0-9]{32}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$/i.test(target))throw Error('Target must be a Minecraft UUID');body.target=target;}const start=date('from'),end=date('until');if(start!==undefined)body.notBefore=start;if(end!==undefined)body.expires=end;if(start!==undefined&&end!==undefined&&start>=end)throw Error('Expiry must follow activation');const bytes=Buffer.from(JSON.stringify(body));const code='ZR2.'+bytes.toString('base64url')+'.'+crypto.sign(null,bytes,key).toString('base64url');issued.push({name:option('name','Reward'),code});}
 const output=option('out');if(output){fs.writeFileSync(output,JSON.stringify(issued,null,2),{flag:'wx'});console.log('Wrote '+issued.length+' signed codes to '+output);}else for(const item of issued)console.log(item.code);
}else{console.log('node tools/reward-codes.mjs init\nnode tools/reward-codes.mjs issue --camos camo_grotog,camo_sponsor_animated --target UUID --until 2026-12-01T00:00:00Z --count 1 --out sponsor-codes.json\nFor a public event omit --target and publish the signed code, never the private key. Offline codes cannot enforce a global use count.');}
