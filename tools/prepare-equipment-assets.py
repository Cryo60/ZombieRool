"""Build equipment geometry, hand animations and positional sounds for local development."""
from pathlib import Path
import json,sys,zipfile,struct,io,hashlib,zlib,subprocess,os,shutil

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np
import soundfile as sf

ROOTS=[ROOT/p for p in ['src/main/resources','resources','']]
def write(relative,data):
    encoded=json.dumps(data,indent=2,ensure_ascii=False)+'\n'
    for root in ROOTS:
        p=root/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(encoded,encoding='utf8')

palette={'olive':'minecraft:block/green_terracotta','black':'minecraft:block/black_concrete','steel':'minecraft:block/iron_block','rubber':'minecraft:block/gray_concrete','plastic':'minecraft:block/light_gray_concrete','cloth':'minecraft:block/brown_wool','wire':'minecraft:block/copper_block','red':'minecraft:block/red_concrete','led':'minecraft:block/lime_concrete','label':'minecraft:block/yellow_concrete','white':'minecraft:block/white_concrete','purple':'minecraft:block/purple_terracotta'}
def box(name,a,b,texture,rotation=None):
    e={'name':name,'from':a,'to':b,'faces':{d:{'uv':[0,0,16,16],'texture':'#'+texture} for d in ['north','south','east','west','up','down']}}
    if rotation:e['rotation']=rotation
    return e
def model(elements,name):
    height=max(e['to'][1] for e in elements)
    display={'gui':{'rotation':[25,-40,0],'translation':[0,(8-height/2)*.8,0],'scale':[.85]*3},'ground':{'translation':[0,8,0],'scale':[1]*3},'fixed':{'rotation':[0,0,0],'scale':[.7]*3},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.8]*3},'firstperson_righthand':{'rotation':[0,-20,10],'translation':[1.2,3,1.4],'scale':[.8]*3}}
    write('assets/zombierool/models/item/'+name+'.json',{'ambientocclusion':False,'textures':dict(palette,particle=palette['olive']),'elements':elements,'display':display})

c4=[box('Canvas explosive block',[3,0,4],[13,3.7,12],'cloth'),box('Upper pack',[3.2,3.7,4.2],[12.8,4.1,11.8],'olive')]
for x in [4.5,10.3]:
    c4.extend([box('Tape strap',[x,-.01,3.9],[x+1.2,4.23,12.1],'black'),box('Buckle',[x-.1,4.24,7],[x+1.3,4.65,9],'steel')])
c4.extend([box('Receiver',[6.3,4.12,7],[9.7,5.25,10.7],'plastic'),box('Battery housing',[6.6,4.7,5],[9.4,5.5,7],'black'),box('Safety LED',[8.7,5.26,7.7],[9.1,5.36,8.1],'led'),box('Danger label',[6.5,3.72,4.18],[9.5,4.23,4.35],'label')])
for a,b in [([8,5.3,7],[8.25,5.55,8]),([8,4.3,10.7],[8.3,4.55,11.4]),([7,4.3,11.15],[8.2,4.55,11.4]),([6.8,4.2,10.7],[7.1,4.55,11.4])]:c4.append(box('Detonation wire',a,b,'wire'))
model(c4,'c4')

clay=[box('Left mounting stake',[3.4,0,7],[4.2,6,7.8],'steel'),box('Right mounting stake',[11.8,0,7],[12.6,6,7.8],'steel'),box('Center explosive panel',[4,4,6],[12,10,8.8],'olive'),box('Rear housing',[4.4,4.3,8.8],[11.6,9.7,9.2],'black')]
for x,angle in [(1.5,-22.5),(11,22.5)]:clay.append(box('Curved front wing',[x,4,6.2],[x+3.5,10,8.5],'olive',{'origin':[x+1.75,7,7.4],'axis':'y','angle':angle}))
clay.extend([box('Firing port left',[4.5,10,7],[5.4,10.9,8],'black'),box('Firing port right',[10.6,10,7],[11.5,10.9,8],'black'),box('Front warning strip',[5,7.6,5.96],[11,8.4,6.01],'label'),box('Front direction arrow',[7.5,8.6,5.93],[8.5,9.1,6.03],'black')])
for x in [5,10.3]:clay.append(box('Trigger emitter',[x,4.8,5.82],[x+.7,5.5,6.03],'red'))
model(clay,'claymore')

betty=[box('Base disk',[3.3,0,3.3],[12.7,1.6,12.7],'olive'),box('Octagonal corners',[3.3,.1,3.3],[12.7,1.5,12.7],'olive',{'origin':[8,.8,8],'axis':'y','angle':45}),box('Rising charge',[4,1.6,4],[12,3.8,12],'rubber'),box('Charge top',[4.2,3.8,4.2],[11.8,4.2,11.8],'olive'),box('Pressure fuse',[7.2,4.2,7.2],[8.8,6.4,8.8],'steel')]
for x,z in [(4,4),(11,4),(4,11),(11,11)]:betty.append(box('Assembly screw',[x,4.2,z],[x+.65,4.38,z+.65],'steel'))
model(betty,'bouncing_betty')

det=[box('Remote housing',[5,0,5],[11,9,9],'black'),box('Rubber front',[5.3,1,4.8],[10.7,7.5,5],'rubber'),box('Arm switch',[6,7.5,4.75],[7.3,8.3,5.1],'steel'),box('Red firing button',[8,5,4.6],[9.5,6.7,5.1],'red'),box('Antenna socket',[9.3,9,6],[10.3,10,7],'steel'),box('Antenna',[9.65,10,6.35],[9.95,14,6.65],'black'),box('Status lamp',[6,3,4.7],[6.65,3.65,5],'led')]
model(det,'anim_c4_detonator')

for name,color in [('flash','steel'),('pheromone','purple'),('semtex','olive')]:
    parts=[box('Canister',[5.3,0,5.3],[10.7,7.5,10.7],color),box('Cap',[5.6,7.5,5.6],[10.4,8.4,10.4],'black'),box('Fuse',[7.2,8.4,7.2],[8.8,9.7,8.8],'steel'),box('Safety lever',[8.8,6.3,7.4],[9.4,10.3,8.4],'steel')]
    for y in [1.3,5.8]:parts.append(box('Identification band',[5.25,y,5.25],[10.75,y+.75,10.75],'white' if name=='flash' else 'red' if name=='semtex' else 'led'))
    for x in [6.4,9.1]:parts.append(box('Pin ring',[x,8.8,7],[x+.45,10.3,7.45],'steel'))
    parts.append(box('Pin ring upper',[6.4,10.3,7],[9.55,10.75,7.45],'steel'))
    if name=='semtex':parts.append(box('Sticky explosive collar',[5,0,5],[11,1.1,11],'cloth'))
    model(parts,name)

write('assets/zombierool/models/item/m1014.json',{'parent':'item/generated','textures':{'layer0':'zombierool:item/weapons/m1014'}})

definition=json.loads((ROOT/'src/main/resources/data/zombierool/gameplay/weapons/spas12.json').read_text(encoding='utf8'))
definition.update(id='zombierool:m1014',name='weapon.zombierool.m1014',lore=['weapon.zombierool.m1014.lore.0'],tacz={'gun_id':'tacz:m1014','ammo_id':'tacz:12g'})
definition['stats'].update(damage=10.5,fire_rate=6,range=22)
definition['ammo'].update(clip_size=6,max_reserve=60,reload_time=20)
definition['ballistics'].update(spread=3,count=8)
definition['pack_a_punch'].update(name='weapon.zombierool.m1014.pap',damage_bonus=9,clip_bonus=6,reserve_bonus=60)
definition['sounds'].update(fire='zombierool:m1014_fire',reload='zombierool:m1014_reload',pump='')
definition['tags']=['call_of_duty','shotgun','semi_auto','tacz']
write('data/zombierool/gameplay/weapons/m1014.json',definition)

# Explicit linear keyframes avoid the library's constant rotation default.
def track(frames):return {str(t):{'vector':v,'lerp_mode':'linear'} for t,v in frames}
def animation(length,right,left,head=None,hold=False):
    bones={'right_arm':{'rotation':track(right)},'left_arm':{'rotation':track(left)}}
    if head:bones['head']={'rotation':track(head)}
    a={'animation_length':length,'bones':bones}
    if hold:a['loop']='hold_on_last_frame'
    return a
animations={}
for kind in ['c4','claymore','bouncing_betty','flash','pheromone','semtex']:
    mine=kind in ['claymore','bouncing_betty'];r=[-72,-15,-8] if mine else [-83,-12,-4];l=[-70,28,8] if mine else [-72,35,0]
    animations[kind+'_hold']=animation(.8,[(0,[0,0,0]),(.12,[-45,-6,-3]),(.28,r),(.5,r),(.8,r)],[(0,[0,0,0]),(.16,[-35,15,0]),(.32,l),(.47,[-80,32,5]),(.65,l),(.8,l)],hold=True)
    if mine:
        animations[kind+'_place']=animation(.9,[(0,r),(.12,[-100,-12,-8]),(.26,[-122,-10,-5]),(.4,[-135,-7,0]),(.55,[-80,-8,0]),(.72,[-25,0,0]),(.9,[0,0,0])],[(0,l),(.2,[-115,18,8]),(.4,[-130,10,8]),(.6,[-55,20,0]),(.9,[0,0,0])],[(0,[0,0,0]),(.25,[3,0,-1]),(.5,[1,0,0]),(.9,[0,0,0])])
    else:
        animations[kind+'_throw']=animation(.9,[(0,r),(.08,[-115,-20,-10]),(.17,[-145,-16,-6]),(.25,[-180,4,4]),(.4,[-110,3,2]),(.6,[-35,0,0]),(.9,[0,0,0])],[(0,l),(.16,[-40,28,0]),(.32,[-20,16,0]),(.6,[0,0,0]),(.9,[0,0,0])],[(0,[0,0,0]),(.18,[-1,0,-1]),(.4,[1,0,1]),(.9,[0,0,0])])
animations['c4_detonate']=animation(.6,[(0,[-65,-8,0]),(.14,[-87,-22,5]),(.25,[-91,-18,8]),(.31,[-84,-22,3]),(.43,[-55,-10,0]),(.6,[0,0,0])],[(0,[0,0,0]),(.17,[-74,40,-4]),(.25,[-88,35,0]),(.35,[-70,42,0]),(.6,[0,0,0])])
animations['equipment_cancel']=animation(.3,[(0,[-70,-12,0]),(.3,[0,0,0])],[(0,[-65,25,0]),(.3,[0,0,0])])
write('assets/zombierool/player_animation/equipment.json',{'format_version':'1.8.0','animations':animations})

# Keep editable source and the runtime registry in step for the revised classic actions.
classic={
    'knife':{'knife_sweep':animation(.6,
        [(0,[0,0,0]),(.08,[-54,-25,18]),(.17,[-98,-42,26]),(.25,[-104,54,-36]),(.34,[-76,72,-42]),(.46,[-24,15,-8]),(.6,[0,0,0])],
        [(0,[0,0,0]),(.12,[-65,24,-6]),(.24,[-72,12,-12]),(.38,[-40,30,0]),(.6,[0,0,0])],
        [(0,[0,0,0]),(.17,[0,-1,-1]),(.27,[1,2,1]),(.6,[0,0,0])])},
    'drink_perk':{'drink_perk':animation(3.5,
        [(0,[0,0,0]),(.18,[-48,-8,-5]),(.42,[-78,-16,-8]),(.7,[-82,-9,-4]),(.94,[-118,-8,0]),(1.16,[-138,-6,5]),(2.65,[-140,-6,6]),(2.92,[-106,-12,-2]),(3.10,[-46,-4,4]),(3.3,[-15,0,0]),(3.5,[0,0,0])],
        [(0,[0,0,0]),(.25,[-42,22,5]),(.42,[-82,34,12]),(.53,[-86,29,-4]),(.64,[-80,38,15]),(.77,[-44,44,0]),(.94,[-18,20,0]),(2.65,[-12,16,0]),(3.1,[-8,4,0]),(3.5,[0,0,0])],
        [(0,[0,0,0]),(.4,[-1,0,0]),(.85,[-2,0,0]),(1.18,[-9,0,0]),(2.65,[-10,0,0]),(2.95,[-3,0,0]),(3.2,[0,0,0]),(3.5,[0,0,0])])},
    'grenade':{
        'grenade_cook':animation(5,
            [(0,[0,0,0]),(.12,[-52,-14,-5]),(.3,[-81,-15,-8]),(.48,[-78,-13,-7]),(.66,[-83,-10,-4]),(.85,[-78,-12,-5]),(5,[-78,-12,-5])],
            [(0,[0,0,0]),(.17,[-45,20,5]),(.34,[-82,31,7]),(.46,[-86,35,10]),(.6,[-65,56,8]),(.79,[-30,38,0]),(1,[-18,18,0]),(5,[-18,18,0])],hold=True),
        'grenade_throw':animation(.8,
            [(0,[-78,-12,-5]),(.1,[-119,-28,-14]),(.18,[-147,-22,-8]),(.25,[-178,4,7]),(.36,[-124,14,10]),(.53,[-55,5,2]),(.8,[0,0,0])],
            [(0,[-18,18,0]),(.15,[-40,25,4]),(.3,[-32,20,0]),(.5,[-15,7,0]),(.8,[0,0,0])],
            [(0,[0,0,0]),(.15,[-1,0,-1]),(.3,[1,0,1]),(.8,[0,0,0])])}
}
for name,actions in classic.items():
    data={'format_version':'1.8.0','animations':actions}
    write('assets/zombierool/player_animation/'+name+'.json',data)
    write('assets/zombierool/animations/'+name+'.json',data)

# Re-wrap T5's MS-ADPCM header (fields verified against Harmony's t5 reader).
def t5(archive,name):
    with zipfile.ZipFile(archive) as z:b=z.read(name)
    if b[:4]==b'RIFF':return sf.read(io.BytesIO(b),always_2d=True)
    version,frames,rate,channels,start=struct.unpack_from('<IIIII',b)
    assert version==1 and channels in (1,2) and 4000<=rate<=96000 and start<len(b)
    payload=b[start:];align=262*channels
    assert len(payload)%align==0
    coeffs=[(256,0),(512,-256),(0,0),(192,64),(240,0),(460,-208),(392,-232)]
    fmt=struct.pack('<HHIIHHHHH',2,channels,rate,rate*align//512,align,4,32,512,7)+b''.join(struct.pack('<hh',*c) for c in coeffs)
    chunks=b'fmt '+struct.pack('<I',len(fmt))+fmt+b'fact'+struct.pack('<II',4,frames)+b'data'+struct.pack('<I',len(payload))+payload
    return sf.read(io.BytesIO(b'RIFF'+struct.pack('<I',len(chunks)+4)+b'WAVE'+chunks),always_2d=True)
def read(path):return sf.read(path,always_2d=True)
audio=ROOT/'assets-source/26.7/audio'
sources={
    'equipment_draw':audio/'rpg-audio/Audio/clothBelt.ogg',
    'equipment_throw':audio/'rpg-audio/Audio/cloth2.ogg',
    'equipment_place':audio/'impact-sounds/Audio/impactMetal_light_000.ogg',
    'equipment_stick':audio/'rpg-audio/Audio/metalLatch.ogg',
    'c4_click':audio/'rpg-audio/Audio/metalClick.ogg',
    'mine_trigger':audio/'rpg-audio/Audio/metalClick.ogg',
    'semtex_tick':audio/'rpg-audio/Audio/metalClick.ogg',
    'pheromone_burst':audio/'sci-fi-sounds/Audio/forceField_002.ogg',
    'm1014_fire':ROOT/'run/tacz/tacz_default_gun/assets/tacz/tacz_sounds/m1014/m1014_shoot_3p.ogg',
    'm1014_reload':ROOT/'run/tacz/tacz_default_gun/assets/tacz/tacz_sounds/m1014/m1014_reload_once.ogg',
}
game=Path('D:/Jeux/pluto_t5_full_game/main')
if not game.exists():game=Path('D:/Jeux/Plutonium/pluto_t5_full_game/main')
sounds={n:read(p) for n,p in sources.items()}
sounds['equipment_explode']=t5(game/'iw_28.iwd','sound/exp/c4/c4_det.wav')
sounds['flash_burst']=t5(game/'iw_24.iwd','sound/wpn/grenade/flashbang/fizzle/fizzle.wav')
# T5 stores short weapon sounds inside common_mp.ff, independently of the IWD streams.
# Layout: OpenAssetTools T5 snd_asset / ZoneLoaderFactoryT5; export only named audio.
zone=game.parent/'zone/Common/common_mp.ff'
zone_data=zlib.decompress(zone.read_bytes()[12:])
ffmpeg=Path(os.environ.get('FFMPEG',str(ROOT/'build/audio-tools/ffmpeg.exe')))
if not ffmpeg.exists():
    installed=Path('C:/Users/ecorange1/AppData/Local/Temp/ffbin/node_modules/ffmpeg-static/ffmpeg.exe')
    if installed.exists():ffmpeg.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(installed,ffmpeg)
def loaded_t5(name,raw=None):
    raw=zone_data if raw is None else raw
    encoded=name.replace('/','\\').encode()+b'\0';at=raw.index(encoded)
    h=struct.unpack_from('<14I',raw,at-56)
    version,frames,rate,channels,header,block,buffer,codec,flags,loop,seeks,seekptr,size,dataptr=h
    assert version==1 and codec==7 and channels in (1,2) and seekptr==dataptr==0xffffffff
    start=at+len(encoded);seek=raw[start:start+seeks*4];payload=raw[start+seeks*4:start+seeks*4+size]
    # XAudio2 WMA v2 packets and their decoded-byte seek table, in a standard XWMA wrapper.
    assert seeks>0 and size%seeks==0
    fmt=struct.pack('<HHIIHHH',0x161,channels,rate,12000,size//seeks,16,0)
    chunks=b'fmt '+struct.pack('<I',len(fmt))+fmt+b'dpds'+struct.pack('<I',len(seek))+seek+b'data'+struct.pack('<I',len(payload))+payload
    scratch=ROOT/'build/audio-tools';scratch.mkdir(parents=True,exist_ok=True)
    packed=scratch/'t5-loaded.xwma';decoded=scratch/'t5-loaded.wav'
    packed.write_bytes(b'RIFF'+struct.pack('<I',len(chunks)+4)+b'XWMA'+chunks)
    subprocess.run([str(ffmpeg),'-v','error','-y','-i',str(packed),str(decoded)],check=True)
    return read(decoded)
for event,name in {
    'claymore_draw':'sound/fly/gear/pull_out/fly_gear_pull_out_00.wav',
    'claymore_place':'sound/wpn/grenade/claymore/claymore_plant_00.wav',
    'claymore_trigger':'sound/wpn/grenade/claymore/alert/alert_00.wav',
    'claymore_explode':'sound/wpn/grenade/explosion/explode/explode_00.wav',
    'semtex_draw':'sound/wpn/grenade/sticky/alert/semtex_charge.wav',
    'semtex_throw':'sound/wpn/grenade/foley/throw/gren_throw.wav',
    'semtex_stick':'sound/wpn/grenade/sticky/stick/stick_00.wav',
    'semtex_tick':'sound/wpn/grenade/sticky/alert/semtex_alert.wav',
    'semtex_explode':'sound/wpn/grenade/explosion/explode/explode_01.wav',
}.items():
    sounds[event]=loaded_t5(name)
    sources[event]=str(zone)+' :: '+name
manifest={}
for name,(data,rate) in sounds.items():
    data=data.mean(axis=1);data=np.interp(np.arange(0,len(data),rate/44100),np.arange(len(data)),data);data*=.7/max(.001,np.max(np.abs(data)))
    fade=min(220,len(data)//2);data[:fade]*=np.linspace(0,1,fade);data[-fade:]*=np.linspace(1,0,fade)
    # Vorbis can overshoot transient peaks: verify the decoded signal too.
    for attempt in range(4):
        buf=io.BytesIO();sf.write(buf,data,44100,format='OGG',subtype='VORBIS');encoded=buf.getvalue()
        decoded,_=sf.read(io.BytesIO(encoded));peak=float(np.max(np.abs(decoded)))
        if peak<=.75:break
        data*=.7/peak
    assert peak<=.75,name
    for root in ROOTS:
        out=root/'assets/zombierool/sounds/equipment'/(name+'.ogg');out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(encoded)
    manifest[name]={'duration':len(data)/44100,'sha256':hashlib.sha256(encoded).hexdigest(),'source':str(sources.get(name,game))}
for root in ROOTS:
    path=root/'assets/zombierool/sounds.json';definitions=json.loads(path.read_text(encoding='utf8'))
    for name in sounds:definitions[name]={'subtitle':'subtitles.zombierool.'+name,'sounds':[{'name':'zombierool:equipment/'+name,'stream':False}]}
    path.write_text(json.dumps(definitions,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    for lang in (root/'assets/zombierool/lang').glob('*.json'):
        data=json.loads(lang.read_text(encoding='utf8'));fr=lang.stem=='fr_fr'
        data['message.zombierool.equipment_already_equipped']='Cet équipement est déjà équipé. Max Ammo recharge ses charges.' if fr else 'This equipment is already equipped. Max Ammo replenishes its charges.'
        data.update({'key.zombierool.equipment':'Équipement' if fr else 'Equipment','message.zombierool.equipment_equipped':'Équipement équipé : %s' if fr else 'Equipped: %s','tooltip.zombierool.virtual_equipment':'Clic droit pour équiper un emplacement virtuel ; ne prend pas de place dans l’inventaire.' if fr else 'Right-click to equip a virtual slot; does not occupy your inventory.','config.zombierool.startingEquipment':'Équipement de départ' if fr else 'Starting Equipment','config.zombierool.startingTactical':'Grenade tactique de départ' if fr else 'Starting Tactical Grenade','config.zombierool.startingEquipment.tooltip':'C4, Claymore ou Bouncing Betty ; 2 charges, touche Equipment.' if fr else 'C4, Claymore or Bouncing Betty; 2 charges, Equipment key.','config.zombierool.startingTactical.tooltip':'Flash ou Pheromone ; 2 charges, touche Tactical Grenade.' if fr else 'Flash or Pheromone; 2 charges, Tactical Grenade key.','weapon.zombierool.m1014':'M1014','weapon.zombierool.m1014.pap':'M1014 Hellfire','weapon.zombierool.m1014.lore.0':'Fusil à pompe semi-automatique' if fr else 'Semi-automatic shotgun'})
        for name,title in [('c4','C4'),('claymore','Claymore'),('bouncing_betty','Bouncing Betty'),('flash','Flash Grenade'),('pheromone','Pheromone Grenade'),('semtex','Semtex'),('anim_c4_detonator','C4 Detonator')]:data['item.zombierool.'+name]=title
        for name in sounds:data['subtitles.zombierool.'+name]=name.replace('_',' ').capitalize()
        lang.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
out=ROOT/'assets-source/26.7/audio/equipment-manifest.json';out.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
print('Built 7 equipment models, M1014, 14 hand animations, and',len(sounds),'mono sounds.')
