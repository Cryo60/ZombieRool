// Native Minecraft pixel art, cuboid models and an animated 3x3 radar surface.
const fs=require('fs'),path=require('path'),zlib=require('zlib');
const roots=['src/main/resources/assets/zombierool','assets/zombierool','resources/assets/zombierool'];
function crc(b){let c=~0;for(const x of b){c^=x;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^~0)>>>0;}
function png(w,h,pixels){const chunk=(t,b)=>{const size=Buffer.alloc(4),sum=Buffer.alloc(4);size.writeUInt32BE(b.length);const data=Buffer.concat([Buffer.from(t),b]);sum.writeUInt32BE(crc(data));return Buffer.concat([size,data,sum]);};const head=Buffer.alloc(13);head.writeUInt32BE(w);head.writeUInt32BE(h,4);head[8]=8;head[9]=6;const raw=Buffer.alloc(h*(1+w*4));for(let y=0;y<h;y++)Buffer.from(pixels.slice(y*w*4,(y+1)*w*4)).copy(raw,y*(w*4+1)+1);return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
function radar(on){const w=96,frames=32,p=[];for(let frame=0;frame<frames;frame++)for(let y=0;y<w;y++)for(let x=0;x<w;x++){
    const dx=x-47.5,dy=y-47.5,r=Math.hypot(dx,dy),phase=frame/frames;
    const radius=phase*69, distance=Math.abs(r-radius),fade=1-phase*.75;
    const pulse=Math.max(0,1-distance/3.5)*fade*(on?1:.22);
    const trail=Math.max(0,1-Math.max(0,radius-r)/12)*(r<=radius?1:0)*fade;
    let c=[5+20*pulse,18+195*pulse+22*trail,9+68*pulse+8*trail];
    const border=Math.min(x,y,95-x,95-y);
    if(border<2)c=on?[28,91,44]:[15,40,22];
    if(r<2.5)c=on?[76,225,109]:[21,67,33];
    p.push(...c.map(v=>Math.round(Math.min(255,v))),255);
}return png(w,w*frames,p);}
function icon(color,phase){const p=[];for(let y=0;y<32;y++)for(let x=0;x<32;x++){
    let c=x<2||y<2||x>29||y>29?[36,43,48]:[18,25,30],dx=x-15.5,dy=y-15.5;
    let ink=phase===0?(Math.abs(dy)<2&&x>6&&x<23)||(x>17&&Math.abs(dy)<x-16)
        :phase===1?(Math.abs(dx)<1.5&&y>10&&y<25)||(y>6&&y<18&&Math.abs(dx)<18-y)
        :Math.abs(Math.hypot(dx,dy)-9)<1.5||Math.abs(dx)<1.5&&Math.abs(dy)<4;
    if(ink)c=color;if((x===4||x===27)&&(y===4||y===27))c=[150,160,165];p.push(...c,255);
}return png(32,32,p);}
function cube(from,to,uv=[0,0,16,16]){return{from,to,faces:Object.fromEntries(['north','south','west','east','up','down'].map(face=>[face,{texture:'#surface',uv:face==='up'?uv:[0,0,16,16]}]))};}
for(const root of roots){
    for(const folder of ['textures/block','models/block','models/item','blockstates'])fs.mkdirSync(path.join(root,folder),{recursive:true});
    const write=(name,data)=>fs.writeFileSync(path.join(root,name),typeof data==='object'&&!Buffer.isBuffer(data)?JSON.stringify(data,null,2)+'\n':data);
    for(const on of [false,true]){
        const name='teleporter_radar'+(on?'_on':'');write('textures/block/'+name+'.png',radar(on));write('textures/block/'+name+'.png.mcmeta',{animation:{frametime:2,width:96,height:96,interpolate:true}});
        for(let i=0;i<9;i++){
            const uv=[i%3*16/3,Math.floor(i/3)*16/3,(i%3+1)*16/3,(Math.floor(i/3)+1)*16/3];
            write('models/block/teleporter_'+i+(on?'_on':'')+'.json',{ambientocclusion:false,textures:{surface:'zombierool:block/'+name,particle:'zombierool:block/'+name},elements:[cube([0,0,0],[16,2,16],uv)]});
        }
    }
    const variants={};for(let i=0;i<9;i++)for(const on of [false,true])variants[`invisible=false,lit=${on},part=${i}`]={model:'zombierool:block/teleporter_'+i+(on?'_on':'')};for(let i=0;i<9;i++)for(const on of [false,true])variants[`invisible=true,lit=${on},part=${i}`]={model:'zombierool:block/teleporter_invisible'};write('models/block/teleporter_invisible.json',{textures:{particle:'zombierool:block/teleporter_radar'},elements:[]});write('blockstates/teleporter.json',{variants});
    write('models/item/teleporter.json',{ambientocclusion:false,textures:{surface:'zombierool:block/teleporter_radar_on'},elements:[cube([0,0,0],[16,2,16])],display:{gui:{rotation:[30,225,0],translation:[0,0,0],scale:[.8,.8,.8]}}});
    const nodeVariants={};for(let i=0;i<3;i++){
        const name='traversal_node_'+i;write('textures/block/'+name+'.png',icon([[55,213,137],[240,197,65],[65,166,236]][i],i));
        write('models/block/'+name+'.json',{textures:{surface:'zombierool:block/'+name,particle:'zombierool:block/'+name},elements:[cube([3,0,3],[13,3,13])]});nodeVariants['phase='+i]={model:'zombierool:block/'+name};
    }
    write('blockstates/traversal_node.json',{variants:nodeVariants});write('models/item/traversal_node.json',{parent:'zombierool:block/traversal_node_0'});
    write('textures/block/teleporter_switch.png',icon([225,154,57],1));write('models/block/teleporter_switch.json',{textures:{surface:'zombierool:block/teleporter_switch',particle:'zombierool:block/teleporter_switch'},elements:[cube([4,0,4],[12,12,12])]});
    write('blockstates/teleporter_switch.json',{variants:{'':{model:'zombierool:block/teleporter_switch'}}});write('models/item/teleporter_switch.json',{parent:'zombierool:block/teleporter_switch'});
}
console.log('Generated 3x3 expanding green pulse with 32 animation frames, traversal markers and condition switch.');
