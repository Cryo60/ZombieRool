"""Build local 26.7 wonder-weapon definitions and export only named T5 audio."""
from pathlib import Path
import json,zlib,io,hashlib,importlib.util

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('equipment_assets',ROOT/'tools/prepare-equipment-assets.py')
e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
np,sf=e.np,e.sf

for id,name,pap,clip,reserve,reload,damage,bonus,weapon_range in [
    ('winters_howl',"Winter's Howl","Winter's Fury",6,24,76,85,35,20),
    ('vr11','V-R11','V-R11 Lazarus',3,9,72,0,0,40),
]:
    data=json.loads((ROOT/'src/main/resources/data/zombierool/gameplay/weapons/thundergun.json').read_text())
    data.update(id='zombierool:'+id,name='weapon.zombierool.'+id,lore=['weapon.zombierool.'+id+'.lore.0'],tags=['cod_zombies','wonder_weapon','black_ops','single_fire'])
    data['stats'].update(damage=damage,fire_rate=7 if id=='winters_howl' else 14,range=weapon_range,mobility=.95)
    data['ammo'].update(clip_size=clip,max_reserve=reserve,reload_time=reload)
    data['pack_a_punch'].update(name='weapon.zombierool.'+id+'.pap',damage_bonus=bonus,clip_bonus=3,reserve_bonus=12 if id=='winters_howl' else 9,reload_speed_mult=1)
    data['sounds'].update(fire='zombierool:'+id+'_fire',fire_pap='zombierool:'+id+'_fire',reload='zombierool:'+id+'_reload')
    data['headshot']['can_explode_head']=False
    data['recoil'].update(pitch=2.2,yaw=.3)
    e.write('data/zombierool/gameplay/weapons/'+id+'.json',data)
    e.write('assets/zombierool/models/item/'+id+'.json',{'parent':'item/generated','textures':{'layer0':'zombierool:item/weapons/'+id}})

zones=ROOT/'build/audio-tools'
game=Path('D:/Jeux/pluto_t5_full_game/zone/Common')
raw={name:zlib.decompress((game/name).read_bytes()[12:]) for name in ['common_zombie.ff','zombie_coast.ff']}
manifest={};sounds={}
def load(zone,name):return e.loaded_t5(name,raw[zone])
def mix(zone,clips):
    result=np.zeros(1)
    for at,name in clips:
        data,rate=load(zone,name);data=data.mean(axis=1)
        data=np.interp(np.arange(0,len(data),rate/44100),np.arange(len(data)),data)
        start=round(at*44100);size=start+len(data)
        if size>len(result):result=np.pad(result,(0,size-len(result)))
        result[start:size]+=data
    return result,44100
single={
 'winters_howl_fire':('common_zombie.ff','sound/wpn/energy/freezegun/plr/shot_plr_fnt.wav'),
 'winters_howl_freeze':('common_zombie.ff','sound/wpn/energy/freezegun/projectile/freeze/freeze_00.wav'),
 'winters_howl_shatter':('common_zombie.ff','sound/wpn/energy/freezegun/zombie/shatter/shatter_00.wav'),
 'vr11_fire':('zombie_coast.ff','sound/wpn/energy/human/plr/hg_shot.wav'),
 'vr11_convert':('zombie_coast.ff','sound/wpn/energy/human/effects/oneshot/oneshot_00.wav'),
 'vr11_explode':('zombie_coast.ff','sound/wpn/energy/human/effects/explode/explode_00.wav'),
}
for event,(zone,name) in single.items():
    data,rate=load(zone,name);sounds[event]=(data.mean(axis=1),rate);manifest[event]={'source':str(game/zone)+' :: '+name}
human_source=game.parent.parent/'main/iw_23.iwd';human_name='sound/vox/dds/mpl/share/fire_screams/vox_fire_scream_00.wav'
data,rate=e.t5(human_source,human_name);sounds['vr11_human']=(data.mean(axis=1),rate);manifest['vr11_human']={'source':str(human_source)+' :: '+human_name}
for event,zone,clips in [
 ('winters_howl_reload','common_zombie.ff',[(0,'fly_freeze_open'),(.65,'fly_freeze_twist'),(1.65,'fly_freeze_backon'),(2.7,'fly_freeze_finish')]),
 ('vr11_reload','zombie_coast.ff',[(0,'hg_battery_open'),(.55,'hg_battery_eject'),(1.25,'hg_battery_insert'),(2.2,'hg_battery_close'),(2.85,'hg_arm_close')]),
]:
    folder='sound/wpn/energy/freezegun/reload/' if event.startswith('winters') else 'sound/wpn/energy/human/foley/'
    clips=[(at,folder+name+'.wav') for at,name in clips];sounds[event]=mix(zone,clips);manifest[event]={'source':str(game/zone),'sequence':clips}
for event,(data,rate) in sounds.items():
    data=np.interp(np.arange(0,len(data),rate/44100),np.arange(len(data)),data);data*=.65/max(.001,np.max(np.abs(data)))
    fade=min(220,len(data)//2);data[:fade]*=np.linspace(0,1,fade);data[-fade:]*=np.linspace(1,0,fade)
    for attempt in range(4):
        buf=io.BytesIO();sf.write(buf,data,44100,format='OGG',subtype='VORBIS');encoded=buf.getvalue();decoded,_=sf.read(io.BytesIO(encoded));peak=float(np.max(np.abs(decoded)))
        if peak<=.75:break
        data*=.65/peak
    assert peak<=.75,event
    for root in e.ROOTS:
        path=root/'assets/zombierool/sounds/wonder_weapons'/(event+'.ogg');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encoded)
    manifest[event].update(duration=len(data)/44100,sha256=hashlib.sha256(encoded).hexdigest())
for root in e.ROOTS:
    path=root/'assets/zombierool/sounds.json';data=json.loads(path.read_text())
    for name in sounds:data[name]={'subtitle':'subtitles.zombierool.'+name,'sounds':[{'name':'zombierool:wonder_weapons/'+name,'stream':False}]}
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for path in (root/'assets/zombierool/lang').glob('*.json'):
        data=json.loads(path.read_text(encoding='utf8'));fr=path.stem=='fr_fr'
        data.update({'weapon.zombierool.winters_howl':"Winter's Howl",'weapon.zombierool.winters_howl.pap':"Winter's Fury",'weapon.zombierool.winters_howl.lore.0':'Ralentit puis enferme les zombies dans la glace.' if fr else 'Slows, then encases zombies in ice.','weapon.zombierool.vr11':'V-R11','weapon.zombierool.vr11.pap':'V-R11 Lazarus','weapon.zombierool.vr11.lore.0':'Transforme les zombies en humains leurres.' if fr else 'Turns zombies into human decoys.','entity.zombierool.vril_human':'Humain' if fr else 'Human'})
        for name in sounds:data['subtitles.zombierool.'+name]=name.replace('_',' ').capitalize()
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(ROOT/'assets-source/26.7/audio/wonder-weapons-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Built two wonder weapons and',len(sounds),'T5-only mono sounds.')
