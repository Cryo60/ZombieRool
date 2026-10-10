const fs=require('fs'),path=require('path');
let code=fs.readFileSync('tools/generate-map-devices.cjs','utf8');let prefix=code.slice(0,code.indexOf('const names='));eval(prefix.replace("const roots=['src/main/resources/assets/zombierool','assets/zombierool'];",'')+`\n globalThis.makePNG=png;`);
const pixels=[];for(let y=0;y<32;y++)for(let x=0;x<32;x++)pixels.push(...((x/4+y/4|0)%2?[70,70,70,255]:[120,120,120,255]));
for(const root of ['src/main/resources/assets/zombierool','assets/zombierool']){
 const source='run/resourcepacks/zombierool_maptex/assets/zombierool';
 for(const folder of ['models/block','models/item','blockstates']){fs.mkdirSync(path.join(root,folder),{recursive:true});for(const f of fs.readdirSync(path.join(source,folder)))if(f.startsWith('maptex_'))fs.copyFileSync(path.join(source,folder,f),path.join(root,folder,f));}
 fs.mkdirSync(path.join(root,'textures/block/maptex'),{recursive:true});
 for(let i=0;i<32;i++){for(const face of ['side','top','bottom','lower','upper','north','south','east','west'])fs.writeFileSync(path.join(root,`textures/block/maptex/${i}_${face}.png`),makePNG(pixels));
 let cube=JSON.parse(fs.readFileSync(path.join(root,`models/block/maptex_${i}.json`)));for(const f of ['north','south','east','west'])cube.textures[f]=`zombierool:block/maptex/${i}_${f}`;fs.writeFileSync(path.join(root,`models/block/maptex_${i}.json`),JSON.stringify(cube));
 const variants={};for(const facing of ['north','east','south','west'])for(const half of ['lower','upper'])for(const hinge of ['left','right'])for(const open of [true,false]){const model=`maptex_${i}_door_${half}_${hinge}${open?'_open':''}`;variants[`facing=${facing},half=${half},hinge=${hinge},open=${open}`]={model:'zombierool:block/'+model,y:({east:0,south:90,west:180,north:270}[facing]+(open?(hinge==='left'?90:270):0))%360};fs.writeFileSync(path.join(root,'models/block',model+'.json'),JSON.stringify({parent:`minecraft:block/door_${half}_${hinge}${open?'_open':''}`,textures:{top:`zombierool:block/maptex/${i}_upper`,bottom:`zombierool:block/maptex/${i}_lower`}}));}
 fs.writeFileSync(path.join(root,`blockstates/maptex_${i}_door.json`),JSON.stringify({variants}));fs.writeFileSync(path.join(root,`models/item/maptex_${i}_door.json`),JSON.stringify({parent:'minecraft:item/generated',textures:{layer0:`zombierool:block/maptex/${i}_upper`}}));
 const head=JSON.parse(JSON.stringify(cube));delete head.parent;head.ambientocclusion=false;head.elements=[{from:[4,0,4],to:[12,8,12],faces:Object.fromEntries(['down','up','north','south','east','west'].map(face=>[face,{texture:'#'+face,uv:[0,0,16,16]}]))}];
 fs.writeFileSync(path.join(root,`models/block/maptex_${i}_head.json`),JSON.stringify(head));
 fs.writeFileSync(path.join(root,`blockstates/maptex_${i}_head.json`),JSON.stringify({variants:Object.fromEntries(['north','east','south','west'].map((facing,rotation)=>['facing='+facing,{model:`zombierool:block/maptex_${i}_head`,y:rotation*90}]))}));
 fs.writeFileSync(path.join(root,`models/item/maptex_${i}_head.json`),JSON.stringify({parent:`zombierool:block/maptex_${i}_head`}));
 }
}
