"""Authored cuboid arsenal. All artwork, balance and sound files belong to Stratholme."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random,shutil,math
ROOT=Path(__file__).resolve().parents[2]
PACK=ROOT/'examples/stratholme/zombierool/gameplay/weapon_packs/stratholme'
SRC=ROOT/'src/main/resources'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def model(kind,tier=1,draw=0,loaded=True):
    boxes=[]
    def box(a,b,mat):
        u=mat%4*4;v=mat//4*4
        boxes.append({'from':a,'to':b,'faces':{face:{'texture':'#atlas','uv':[u,v,u+4,v+4]} for face in ['north','south','east','west','up','down']}})
    metal=2 if tier==1 else 3 if tier==2 else 6
    if kind in ['dagger','knife','sword','greatsword']:
        size={'dagger':9 if tier==1 else 12,'knife':11 if tier==1 else 14,'sword':18 if tier==1 else 21,'greatsword':23}[kind]
        guard=8 if kind!='greatsword' else 9
        box([7.35,-2 if kind=='greatsword' else 2,7.35],[8.65,guard-1,8.65],1)
        box([7,1 if kind!='greatsword' else -3,7],[9,2 if kind!='greatsword' else -2,9],5)
        width=2.2 if kind in ['knife','dagger'] else 5 if kind=='greatsword' and tier==3 else 3
        box([8-width/2,guard,7.5],[8+width/2,guard+size-3,8.5],metal)
        box([7.4,guard+size-3,7.55],[8.6,guard+size,8.45],metal)
        box([4.8 if kind!='knife' else 6.5,guard-1,7],[11.2 if kind!='knife' else 9.5,guard,9],5 if tier>1 else 2)
        if tier>1:
            box([7.7,guard+1,7.35],[8.3,guard+size-4,7.6],7 if tier==3 else 4)
            for y in range(guard+2,guard+size-3,3):box([7.1,y,7.25],[8.9,y+.45,7.6],7 if tier==3 else 4)
        if kind=='knife':box([8.8,guard+1,7.6],[10.3,guard+4,8.4],metal)
        if kind=='dagger' and tier==3:box([5.5,guard+4,7.5],[7,guard+8,8.5],metal);box([9,guard+6,7.5],[10.5,guard+10,8.5],metal)
        if kind in ['sword','greatsword'] and tier==3:box([3.5,guard-2,7.1],[5.5,guard,8.9],5);box([10.5,guard-2,7.1],[12.5,guard,8.9],5)
    elif kind=='bow':
        reach=9+tier*2
        box([7,4,7],[9,10,9],1)
        for side in [-1,1]:
            for i in range(5):
                y=7+side*(3+i*(reach-3)/4);x=8+(i/4)**2*5
                box([x-.8,y-(reach-3)/8-.3,7.25],[x+.8,y+(reach-3)/8+.3,8.75],0 if tier<3 else 6)
            tip=(13,7+side*reach);nock=(13+draw*6,7)
            for i in range(12):
                t=i/12;x=tip[0]*(1-t)+nock[0]*t;y=tip[1]*(1-t)+nock[1]*t
                box([x-.12,y-.6,7.9],[x+.12,y+.6,8.1],8)
            box([11.8,7+side*reach-1,6.9],[13.8,7+side*reach+1,9.1],5)
            if tier>=2:box([8.3,7+side*6-1,7],[10.1,7+side*6+1,9],5)
            if tier==3:box([10,7+side*(reach-2)-1,6.7],[12,7+side*(reach-2)+1,9.3],5);box([7.3,6.2,6.8],[8.7,7.8,7.3],7)
        if draw>0:
            box([-11,6.85,7.85],[12+draw*6,7.15,8.15],0)
            box([-12,6.5,7.35],[-10,7.5,8.65],metal)
    elif kind=='crossbow':
        box([7,3,1],[9,6,17],0);box([6.7,0,11],[9.3,5,14],1)
        box([0,5,3],[16,6.3,5.5],metal);box([0,4.7,5.5],[2,6.6,8],5);box([14,4.7,5.5],[16,6.6,8],5)
        for side in [-1,1]:
            for i in range(8):
                x=8+side*i;z=10-i*.5
                box([x-.55,6.1,z-.12],[x+.55,6.3,z+.12],8)
        box([6.5,6,8],[9.5,7.3,10],2)
        if loaded:box([7.8,6.4,-3],[8.2,6.8,11],0);box([7.35,6,-4],[8.65,7.2,-2],metal)
        if tier==3:box([4,2,13],[12,4,17],2);box([5,6,7],[6,9,9],5);box([10,6,7],[11,9,9],5)
        if tier==2:box([6.6,2,11],[9.4,3.5,14],10);box([2,5,2.8],[5,6.5,3.2],10);box([11,5,2.8],[14,6.5,3.2],10)
    elif kind=='wand':
        box([7.4,1,7.4],[8.6,17,8.6],0);box([7,3,7],[9,6,9],1)
        box([6.8,15,6.8],[9.2,17,9.2],5);box([6,17,6],[10,21,10],10 if tier==1 else 12)
        box([7,21,7],[9,24,9],10 if tier==1 else 12)
        box([4.8,15,7],[6,20,9],5);box([10,15,7],[11.2,20,9],5)
    else:
        length={'pistol':12,'musket':24,'blunderbuss':17,'rifle':27}[kind]
        muzzle=12-length
        box([6.8,4,4],[9.2,7,17],0);box([6.7,0,11],[9.3,5,14],1)
        box([6.5,7,muzzle],[9.5,10,13],metal)
        box([7,7.5,muzzle-.15],[9,9.5,muzzle+.15],9)
        box([6.1,6.5,5],[9.9,10.5,6.5],5);box([6.1,6.5,muzzle+2],[9.9,10.5,muzzle+3],5)
        box([9.4,7,10],[11,9,12],5);box([9.7,9,10.4],[10.4,11.5,11.3],2)
        box([7.4,10,9],[8.6,11,10],2);box([7.6,10,muzzle+1],[8.4,11.3,muzzle+2],2)
        if kind!='pistol':box([6,2,14],[10,7,20],0);box([5.7,2,19],[10.3,7,21],2)
        if kind=='blunderbuss':
            box([5.5,6,muzzle-3],[10.5,11,muzzle+2],5);box([6,6.5,muzzle-3.2],[10,10.5,muzzle-2.9],9)
        if kind=='rifle':
            box([7.1,11,2],[8.9,12.8,11],2);box([6.5,10,3],[9.5,11,4],5);box([6.5,10,9],[9.5,11,10],5)
    melee=kind in ['dagger','knife','sword','greatsword']
    display={'gui':{'rotation':[15,-45,15] if melee else [25,135,0],'scale':[.65,.65,.65] if kind!='greatsword' else [.46,.46,.46]},'ground':{'scale':[.5,.5,.5]},'fixed':{'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[0,0,0] if melee else [90,0,0],'translation':[0,1.5,2],'scale':[.8,.8,.8]},'thirdperson_lefthand':{'rotation':[0,0,0] if melee else [90,0,0],'translation':[0,1.5,2],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-90,0] if kind=='bow' else [0,0,0],'translation':[0,0,0],'scale':[.75,.75,.75]},'firstperson_lefthand':{'rotation':[0,90,0] if kind=='bow' else [0,0,0],'scale':[.75,.75,.75]}}
    if kind=='crossbow':
        for hand in ['thirdperson_righthand','thirdperson_lefthand']:display[hand]={'rotation':[0,180,0],'translation':[0,1.5,0],'scale':[.8,.8,.8]}
    if kind=='greatsword':
        for hand in ['thirdperson_righthand','thirdperson_lefthand']:display[hand]['translation']=[0,3.5,2]
    return {'gui_light':'front','textures':{'atlas':'zombierool:item/stratholme/arsenal','particle':'zombierool:item/stratholme/arsenal'},'elements':boxes,'display':display}

# id, English name, family, tier, damage, cadence ticks, range, durability/reserve, reload ticks
ROWS=[('shortbow','Militia Shortbow','bow',1,5,10,32,48,8),('warbow','QuelÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢Thalas Warbow','bow',3,16,14,64,72,10),('hunting_crossbow','Hunting Crossbow','crossbow',1,8,12,40,48,42),('heavy_crossbow','Siege Crossbow','crossbow',3,24,18,64,60,68),('flintlock','Dwarven Flintlock','pistol',1,9,16,30,48,48),('musket','Ironforge Musket','musket',2,18,20,60,60,62),('blunderbuss','Dwarven Blunderbuss','blunderbuss',2,4,22,22,40,66),('dwarven_rifle','Mountain Guard Rifle','rifle',3,28,18,80,80,54),('rusty_dagger','Worn Dagger','dagger',1,4,7,2.3,100,0),('assassin_dagger','Deathstalker Dagger','dagger',3,10,7,2.3,220,0),('hunting_knife','RangerÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢s Hunting Knife','knife',1,5,9,2.5,130,0),('runic_knife','Runed Combat Knife','knife',2,9,9,2.5,200,0),('arming_sword','Militia Arming Sword','sword',1,6,12,2.8,150,0),('runeblade','Lordaeron Runeblade','sword',3,15,12,3,300,0),('claymore','Lordaeron Claymore','greatsword',2,16,24,3.8,220,0),('runic_greatsword','Runed Greatsword','greatsword',3,25,28,4,350,0)]

def main():
    atlas=Image.new('RGBA',(64,64));p=atlas.load();palette=['#60422a','#39291e','#7e8991','#a9b6bf','#3e8b68','#b29555','#526d87','#60db91','#ddd0ae','#191b20','#9b2923','#cec6ae','#69517f','#95bcc8','#5c6552','#856c4d'];rng=random.Random(31337)
    for i,color in enumerate(palette):
        base=bytes.fromhex(color[1:])
        for y in range(16):
            for x in range(16):
                delta=rng.choice([-12,-6,0,4,9]);delta+=10 if x==0 or y==0 else -14 if x==15 or y==15 else 0;p[i%4*16+x,i//4*16+y]=tuple(max(0,min(255,c+delta)) for c in base)+(255,)
    target=PACK/'assets/zombierool/textures/item/stratholme/arsenal.png';target.parent.mkdir(parents=True,exist_ok=True);atlas.save(target)
    language={locale:json.loads((PACK/f'assets/zombierool/lang/{locale}.json').read_text(encoding='utf-8')) for locale in ['en_us','fr_fr']}
    sounds=json.loads((PACK/'assets/zombierool/sounds.json').read_text(encoding='utf-8'))
    for event,files in [('map_powder_fire',['GunFire01','GunFire02','GunFire03']),('map_powder_load',['GunLoad01','GunLoad02','GunLoad03']),('map_bow_release',['BowRelease','BowRelease02','BowRelease03']),('map_bow_pull',['BowPullback','BowPullback02','BowPullback03'])]:
        sounds[event]={'replace':True,'sounds':[{'name':'zombierool:weapons/stratholme/'+file.lower(),'attenuation_distance':24} for file in files]}
        for file in files:
            source=Path.home()/'Downloads'/(file+'.ogg');target=PACK/'assets/zombierool/sounds/weapons/stratholme'/(file.lower()+'.ogg');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    source=json.loads((PACK/'weapons/scarlet_sword.json').read_text(encoding='utf-8'))
    for short,title,family,tier,damage,rate,reach,supply,reload in ROWS:
        id='stratholme_'+short;melee=family in ['knife','dagger','sword','greatsword'];definition=json.loads(json.dumps(source));definition.update(id='zombierool:'+id,name='weapon.zombierool.'+id,type='MELEE' if melee else 'BOW' if family=='bow' else 'CROSSBOW' if family=='crossbow' else 'SHOTGUN' if family=='blunderbuss' else 'RIFLE',weapon_kind=family,draw_ticks=18 if tier==1 else 30,lore=['item.zombierool.'+id+'.tooltip'],tags=['stratholme','early' if tier==1 else 'mid' if tier==2 else 'late','melee' if melee else 'ranged'])
        definition['stats'].update(damage=damage,fire_rate=rate,range=reach,mobility=1.08 if family in ['knife','dagger'] else .9 if family in ['greatsword','blunderbuss'] else 1,durability=supply if melee else 0,penetration=1 if family in ['rifle','crossbow'] and tier==3 else 0)
        if family in ['pistol','musket','blunderbuss','rifle']:
            start,end,minimum={'pistol':(10,28,.35),'musket':(24,60,.55),'blunderbuss':(4,18,.12),'rifle':(40,80,.7)}[family];definition['stats'].update(damage_falloff_start=start,damage_falloff_end=end,damage_falloff_min=minimum)
        definition['ammo'].update(clip_size=1,max_reserve=-1 if melee else supply,reload_time=reload,ammo_per_shot=0 if melee else 1)
        definition['ballistics'].update(count=7 if family=='blunderbuss' else 1,spread=5 if family=='blunderbuss' else .3 if family in ['bow','crossbow','rifle'] else 1,velocity=2.2 if family=='bow' and tier==1 else 3.3 if family=='bow' else 3 if family=='crossbow' and tier==1 else 4.2 if family=='crossbow' and tier==3 else 4,gravity=family in ['bow','crossbow'])
        definition['recoil'].update(pitch=4 if family=='blunderbuss' else 2 if not melee else 0,yaw=.5 if not melee else 0)
        definition['pack_a_punch'].update(name='weapon.zombierool.'+id+'.pap',damage_bonus=damage*1.5,durability_bonus=supply*2 if melee else 0,reserve_bonus=supply if not melee else 0,reload_speed_mult=.75,penetration_bonus=1 if family in ['rifle','crossbow'] else 0)
        if not melee:
            definition['sounds'].update(fire='zombierool:map_bow_release' if family in ['bow','crossbow'] else 'zombierool:map_powder_fire',fire_pap='zombierool:map_bow_release' if family in ['bow','crossbow'] else 'zombierool:map_powder_fire',reload='zombierool:map_bow_pull' if family=='crossbow' else '' if family=='bow' else 'zombierool:map_powder_load',draw='zombierool:map_bow_pull' if family=='bow' else '',equip='',dry='')
            definition['ballistics']['hitscan_vfx']='POWDER';definition['sounds']['draw']='zombierool:map_powder_load'
            definition['animation']={'fpRx':[-4,55,-12],'fpRy':[0,-30,0],'fpRz':[0,25,3],'fpY':[0,-.12,0],'fpZ':[0,.12,.07],'fpScale':[.82,0,0],'tpX':[-1.4,.75,-.1],'tpY':[-.1,0,0],'tpZ':[-.05,.3,0]}
        elif family in ['knife','dagger']:
            definition['animation'].update(fpRx=[-8,-12,15],fpRy=[-15,5,0],fpRz=[-12,0,5],fpX=[0,.02,-.04],fpY=[0,.02,-.03],fpZ=[0,.08,-.38],tpX=[-1.1,-.2,-.5],tpY=[-.15,0,0],tpZ=[-.1,0,0])
        elif family=='greatsword':definition['animation'].update(fpRx=[-30,-40,75],fpRy=[-12,0,-20],fpRz=[10,15,-35],fpScale=[.72,0,0],fpY=[.22,.08,-.08],fpZ=[-.1,0,-.12],tpX=[-1,-.7,1.2])
        write(PACK/f'weapons/{id}.json',definition)
        # Only inert registry templates ship globally. All balance lives in the save.
        template={'id':'zombierool:'+id,'name':title,'type':definition['type'],'weapon_kind':family,'stats':{'damage':0,'range':0,'fire_rate':20},'ammo':{'clip_size':0,'max_reserve':0}}
        for base in [SRC/'data/zombierool/gameplay/weapons',ROOT/'data/zombierool/gameplay/weapons']:write(base/(id+'.json'),template)
        art=model(family,tier)
        if family=='bow':
            art['overrides']=[{'predicate':{'pulling':1,'pull':threshold},'model':f'zombierool:item/{id}_draw_{step}'} for step,threshold in enumerate([0,.4,.8])]
            for step,draw in enumerate([.25,.6,1]):write(PACK/f'assets/zombierool/models/item/{id}_draw_{step}.json',model(family,tier,draw))
        if family=='crossbow':
            art=model(family,tier,loaded=False);art['overrides']=[{'predicate':{'zombierool:loaded':1},'model':f'zombierool:item/{id}_loaded'}];write(PACK/f'assets/zombierool/models/item/{id}_loaded.json',model(family,tier))
        write(PACK/f'assets/zombierool/models/item/{id}.json',art)
        write(SRC/f'assets/zombierool/models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/paper'}})
        text='A swift blade. Strikes from behind deal extra damage.' if family=='dagger' else 'A quick, close-range cutting weapon.' if family=='knife' else 'A two-handed blade that sweeps nearby enemies.' if family=='greatsword' else 'A balanced blade that cuts nearby enemies.' if family=='sword' else 'Hold use to draw, then release to shoot.' if family=='bow' else 'A heavy bolt with a deliberate reload.' if family=='crossbow' else 'A spread of shot for close quarters.' if family=='blunderbuss' else 'A powder firearm with a measured reload.'
        for locale in language:
            language[locale]['weapon.zombierool.'+id]=title;language[locale]['weapon.zombierool.'+id+'.pap']='Reforged '+title;language[locale]['item.zombierool.'+id+'.tooltip']=text
    # Existing longbow joins the same physical draw and recorded sound system.
    bow=json.loads((PACK/'weapons/longbow.json').read_text(encoding='utf-8'));bow.update(weapon_kind='bow',draw_ticks=24);bow['sounds'].update(draw='zombierool:map_bow_pull',fire='zombierool:map_bow_release',fire_pap='zombierool:map_bow_release');write(PACK/'weapons/longbow.json',bow)
    art=model('bow',2);art['overrides']=[{'predicate':{'pulling':1,'pull':threshold},'model':f'zombierool:item/longbow_draw_{step}'} for step,threshold in enumerate([0,.4,.8])];write(PACK/'assets/zombierool/models/item/longbow.json',art)
    for step,draw in enumerate([.25,.6,1]):write(PACK/f'assets/zombierool/models/item/longbow_draw_{step}.json',model('bow',2,draw))
    for id,kind,tier in [('scarlet_crossbow','crossbow',2),('fire_wand','wand',1),('shadow_wand','wand',2)]:
        write(PACK/f'assets/zombierool/models/item/{id}.json',model(kind,tier))
        definition=json.loads((PACK/f'weapons/{id}.json').read_text(encoding='utf-8'));definition['weapon_kind']=kind
        if kind=='crossbow':definition['sounds'].update(fire='zombierool:map_bow_release',fire_pap='zombierool:map_bow_release',reload='zombierool:map_bow_pull')
        write(PACK/f'weapons/{id}.json',definition)
    for locale,data in language.items():write(PACK/f'assets/zombierool/lang/{locale}.json',data)
    write(PACK/'assets/zombierool/sounds.json',sounds)
    print('Created 16 additional map-owned weapons, three-dimensional bow draw states and the recorded WoW bow/powder sounds.')

if __name__=='__main__':
    main()
    import runpy
    runpy.run_path(str(ROOT/'tools/refine-stratholme-grips.py'),run_name='__main__')
