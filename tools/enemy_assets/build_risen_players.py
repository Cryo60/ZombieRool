"""Minecraft cuboids driven by the local WoW undead PLAYER male/female skeletons."""
from pathlib import Path
import json,math,subprocess,hashlib
import build_blocky_enemies as art
import build_stratholme_actors as actors
from local_casc import LocalCasc

ROOT=Path(__file__).resolve().parents[2];WORLD=ROOT/'examples/stratholme/zombierool';BUILD=ROOT/'build/warcraft-arsenal'
art.OUT=BUILD/'authored'

def flame(m,bone,x,y,z,scale=1,material='red'):
    # Scarlet Crusade flame: central peak and two flared side tongues, never a cross.
    for px,py,w,h in [(-.5,0,1,1.4),(-1,1,2,1.5),(-1.5,2,3,1.5),(-2,3,4,1.5),(-2.5,4,5,1.4),(-2,5.2,4,1.1),(-1.25,6.1,2.5,.9),(-.6,6.8,1.2,.6),(-2.8,2.4,.8,2.4),(2,2.4,.8,2.4)]:m.box(bone,material,x+px*scale,y+py*scale,z,w*scale,h*scale,.16)

def actor(role,female):
    m=art.humanoid('risen_female' if female else 'risen_male',.9 if female else .96)
    for part in m.parts:part['cubes']=[]
    width=6.5 if female else 7.8;arm=2.8 if female else 3.4
    m.by['torso']['rotation']=(.48,0,0);m.by['head']['pivot']=(0,-.7,-1.4);m.by['head']['rotation']=(-.18,0,0)
    m.box('torso','cloth',-width/2,0,-2,width,11.5,4)
    m.box('torso','metal',-width/2-.2,.3,-2.35,width+.4,7,4.7)
    m.box('torso','white',-2.8,1,-2.8,5.6,11.4,.5);m.box('torso','red',-3.4,1,-2.8,.6,12,.5);m.box('torso','red',2.8,1,-2.8,.6,12,.5)
    flame(m,'torso',0,1.4,-3.05,.8)
    m.box('torso','strap',-width/2-.3,9,-2.8,width+.6,1.3,5.2);m.box('torso','gold',-1,8.8,-3.2,2,1.7,.7)
    m.box('head','skin',-3.5 if female else -3.9,-8,-3.5,7 if female else 7.8,6.6,7)
    m.box('head','shade',-2.7,-1.7,-4,5.4,1.3,4.5);m.box('head','bone',-2.4,-2,-4.35,4.8,.8,1.2)
    for x in [-2.5,1]:
        m.box('head','dark',x,-5.1,-3.65,1.9,1.65,.3);m.box('head','eye',x+.35,-4.75,-3.9,1.2,.6,.3)
    m.box('head','dark',-.45,-3.3,-3.8,.9,1.1,.3)
    for x in [-1.9,-.7,.5,1.7]:m.box('head','teeth',x,-2.6,-4.2,.6,.8,.7)
    for side,s in [('left',1),('right',-1)]:
        m.box('head','skin',s*3.8-.3,-5.8,-.5,.9,2.8,1.8)
        m.box('head','bone',s*3.8-.25,-6.6,0,.5,1,.6)
        m.by[side+'_arm']['pivot']=(s*(4.6 if female else 5.2),1,0)
        m.box(side+'_arm','skin',-arm/2,-1,-arm/2,arm,5.8,arm)
        m.box(side+'_forearm','skin',-arm/2,0,-arm/2,arm,4.7,arm)
        m.box(side+'_forearm','bone',-.5,1,-arm/2-.2,1,3.2,.4)
        m.box(side+'_hand','skin',-arm/2,0,-arm/2,arm,1.8,arm)
        m.box(side+'_arm','metal',-arm/2-.6,-1.8,-arm/2-.5,arm+1.2,3,arm+1)
        m.box(side+'_arm','red',-arm/2-.7,-1.2,-arm/2-.6,arm+1.4,.8,arm+1.2)
        m.box(side+'_forearm','metal',-arm/2-.3,2.8,-arm/2-.3,arm+.6,1.9,arm+.6)
        m.box(side+'_leg','pants',-1.7,0,-1.7,3.4,5.8,3.4)
        m.box(side+'_shin','skin',-1.6,0,-1.6,3.2,4.3,3.2)
        m.box(side+'_shin','metal',-1.9,1,-2,3.8,3,3.9)
        m.box(side+'_shin','boot',-1.9,4,-2.8,3.8,2,4.5)
    if female:
        m.box('head','hair',-3.7,-8.3,-1.5,7.4,1.4,5.4)
        m.box('head','hair',-3.7,-7.3,1.5,7.4,7,2.3)
        m.box('head','hair',-3.9,-6.8,-1.8,1,6,4.7);m.box('head','hair',2.9,-6.8,-1.8,1,4.5,4.7)
    else:
        m.box('head','hair',-3,-8.8,.8,6,1.2,2.7);m.box('head','wound',1.8,-7,-3.7,1.2,2.2,.3)
    if role=='swordsman':
        m.box('right_hand','strap',-.65,-1,-.65,1.3,4,1.3);m.box('right_hand','gold',-3,-2,-1,6,1,2);m.box('right_hand','metal',-1,-19,-.55,2,17,1.1)
        m.box('left_forearm','metal',-4.8,-2.5,-4.8,9.6,14.5,1.2);m.box('left_forearm','red',-4.5,-2.2,-5.1,9,14,.4);m.box('left_forearm','white',-3.6,-1.2,-5.4,7.2,12,.3);flame(m,'left_forearm',0,.3,-5.65,1)
    elif role=='axeman':
        m.box('right_hand','strap',-.7,-14,-.7,1.4,18,1.4);m.box('right_hand','metal',.5,-16,-.9,6,7,1.8);m.box('right_hand','gold',-1.1,-15,-1.1,2.2,3,2.2)
    elif role=='pikeman':
        m.box('right_hand','strap',-.65,-26,-.65,1.3,18,1.3);m.box('right_hand','strap',-.65,-8,-.65,1.3,17,1.3);m.box('right_hand','metal',-.75,-32,-.75,1.5,7,1.5);m.box('right_hand','metal',.5,-26,-1,5,7,2)
    elif role=='archer':
        m.box('left_hand','strap',-.6,-2,-.6,1.2,4,1.2)
        for side in [-1,1]:
            for i in range(4):m.box('left_hand','strap',-.7+i*.7,side*(3+i*2)-.9,-.6,1.4,2.3,1.2)
            for i in range(10):m.box('left_hand','teeth',2.8-.3,-11+i*2.2,-.1,.2,2.3,.2)
        m.box('torso','strap',2,2,2,2,9,2.5)
        for x in [2.4,3.2]:m.box('torso','bone',x,-1,2.7,.35,6,.35)
    elif role=='mage':
        m.box('right_hand','strap',-.6,-25,-.6,1.2,17,1.2);m.box('right_hand','strap',-.6,-8,-.6,1.2,16,1.2);m.box('right_hand','gold',-2,-27,-2,4,3,4);m.box('right_hand','eye',-1.5,-31,-1.5,3,4,3)
        m.box('torso','hood',-4,10,-2.4,8,10,4.8);m.box('torso','red',-3,10,-2.9,6,10,.4)
        m.box('torso','gold',-3,18.7,-3,6,1,.6)
    if role in ['swordsman','axeman','pikeman','mage']:
        # Weapon axis leaves the palm instead of going back through the forearm.
        right=m.by['right_hand'];equipment=right['cubes'];skin=art.MATERIALS.index('skin')
        right['cubes']=[c for c in equipment if c['material']==skin]
        weapon=m.part('right_weapon','right_hand',(0,1,0),rot=(math.pi/2 if role!='mage' else math.pi/6,0,0))
        m.by[weapon]['cubes']=[c for c in equipment if c['material']!=skin]
    # One articulated pelvis carries both the spine and the legs, including Death.
    m.part('pelvis',None,(0,12,0))
    pelvis=m.by['pelvis'];m.parts.remove(pelvis);m.parts.insert(0,pelvis)
    m.by['torso']['parent']='pelvis';m.by['torso']['pivot']=(0,0,0)
    for cube in m.by['torso']['cubes']:cube['box'][1]-=12
    for side in ['left','right']:
        leg=m.by[side+'_leg'];leg['parent']='pelvis';x,y,z=leg['pivot'];leg['pivot']=(x,y-12,z)
        arm=m.by[side+'_arm'];x,y,z=arm['pivot'];arm['pivot']=(x,y-12,z)
    x,y,z=m.by['head']['pivot'];m.by['head']['pivot']=(x,y-12,z)
    if role=='archer':
        hand=m.by['left_hand'];skin=art.MATERIALS.index('skin');equipment=hand['cubes'];hand['cubes']=[c for c in equipment if c['material']==skin]
        weapon=m.part('left_weapon','left_hand',(0,1,0),rot=(0,0,0))
        m.by[weapon]['cubes']=[c for c in equipment if c['material']!=skin]
    return m

def main():
    class Cached:
        casc=None
        def read(self,fd):
            file=BUILD/f'{fd}.m2'
            if not file.exists():
                if self.casc is None:self.casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era',locale='enUS')
                file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(self.casc.read(fd))
            return file.read_bytes()
    receipts={}
    for female,fd in [(False,121768),(True,121608)]:
        source=actors.Source(Cached(),fd);gender='female' if female else 'male'
        for role in ['zombie','swordsman','axeman','pikeman','archer','mage']:
            id='scarlet_zombie'+('' if role=='zombie' else '_'+role)+('_female' if female else '')
            m=actor(role,female);bones,boxes=actors.convert(m)
            choices={'idle':[29] if role=='archer' else [27] if role in ['pikeman','mage'] else [26] if role in ['swordsman','axeman'] else [0],'walk':[4],'run':[5,4],'attack':[46] if role=='archer' else [19,18] if role=='pikeman' else [17,16] if role!='zombie' else [16,17],'cast':[29] if role=='archer' else [53,54],'death':[1]}
            clips,receipt=actors.retarget(m,source,{**actors.biped_mapping(source),'pelvis':source.keys.get(5,source.keys[26])},choices)
            name='The Risen '+{'zombie':'Crusader','swordsman':'Swordsman','axeman':'Axeman','pikeman':'Halberdier','archer':'Archer','mage':'Mage'}[role]+(' (Female)' if female else '')
            prefix=f'zombierool:custom/stratholme/risen_{gender}_'
            profile={'id':id,'name':name,'nameKey':'','behavior':'melee','health':65,'healthMode':'wave','knifeMode':'wave','damage':3,'speed':.23,'speedMode':'walk','aggroRadius':12,'wanderRadius':5,'leashRadius':0,'stayNearSpawn':True,'scale':m.scale,'width':.65 if not female else .6,'height':1.9 if not female else 1.8,'undead':True,'countsForWave':False,'boss':False,'callback':'risen_archer' if role=='archer' else 'risen_mage' if role=='mage' else '', 'faction':'risen','sourceMotion':True,'smokyEyes':True,'knockbackResistance':1,'meleeReach':.85,'meleeCooldown':24,'combatRole':role if role in ['archer','mage'] else 'melee','preferredRange':9 if role=='archer' else 8,'texture':'map_actor/'+id,'textureWidth':256,'textureHeight':256,'bones':bones,'boxes':boxes,'animations':clips,'sounds':{'aggro':[prefix+'aggro'],'attack':[prefix+'attack_a',prefix+'attack_b'],'hurt':[prefix+'hurt_a',prefix+'hurt_b'],'death':[prefix+'death']}}
            profile.update(texture=id,eyeR=.4,eyeG=.95,eyeB=.18)
            assert len(boxes)<=128 and len(bones)<=32
            path=WORLD/'gameplay/entities'/(id+'.json');path.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
            atlas=art.atlas(m.kind,{'skin':'#9caa8c' if female else '#93a181','shade':'#667d5b','metal':'#818a8d','cloth':'#892b28','white':'#d4c8a3','red':'#ac3227','eye':'#89ef45','hood':'#592f35','hair':'#3b333d' if female else '#494339'})
            texture=WORLD/'assets/textures/map_actor'/(id+'.png');texture.parent.mkdir(parents=True,exist_ok=True);atlas.save(texture)
            receipts[id]={'sourceFileDataID':fd,'sha256':source.hash,'bones':len(bones),'boxes':len(boxes),'clips':receipt}
            print('Risen player skeleton:',id,flush=True)
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era',locale='enUS')
    clips={'female':{'attack_a':542481,'attack_b':542482,'hurt_a':542479,'hurt_b':542483,'death':542480,'aggro':542512},'male':{'attack_a':542492,'attack_b':542488,'hurt_a':542497,'hurt_b':542489,'death':542490,'aggro':542507}}
    for gender,rows in clips.items():
        for action,fd in rows.items():
            raw=BUILD/f'{fd}.ogg';raw.write_bytes(casc.read(fd));out=WORLD/f'assets/audio/custom/stratholme/risen_{gender}_{action}.ogg';out.parent.mkdir(parents=True,exist_ok=True)
            subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-y','-i',str(raw),'-ac','1','-ar','44100','-c:a','libvorbis','-q:a','4',str(out)],check=True)
    (BUILD/'risen-player-provenance.json').write_text(json.dumps(receipts,indent=2),encoding='utf-8')
    durable=ROOT/'assets-source/26.7/map-actors/risen-player-provenance.json';durable.parent.mkdir(parents=True,exist_ok=True);durable.write_text(json.dumps(receipts,indent=2),encoding='utf-8')
    print('Twelve Risen profiles, actual player animation tracks, Scarlet flame heraldry and both voice sets exported.',flush=True)

if __name__=='__main__':
    main()
    import runpy
    runpy.run_path(str(ROOT/'tools/fix-risen-bow-grips.py'),run_name='__main__')
    runpy.run_path(str(ROOT/'tools/merge-risen-appearances.py'),run_name='__main__')
