"""Map-owned armed variants and reusable templates derived from the existing enemy models."""
from pathlib import Path
import json,copy,sys
import build_stratholme_actors as actors
import build_blocky_enemies as art
from polish_map_actors import clips_for
ROOT=Path(__file__).resolve().parents[2]

def main():
    directory=ROOT/'examples/stratholme/zombierool/gameplay/entities'
    base=json.loads((directory/'scarlet_zombie.json').read_text(encoding='utf-8'))
    for kind,name in [('swordsman','Croisé écarlate — épée et bouclier'),('axeman','Croisé écarlate — hache'),('pikeman','Croisé écarlate — hallebarde')]:
        model=actors.scarlet();model.kind='scarlet_zombie_'+kind
        if kind=='swordsman':
            model.box('right_hand','strap',-.6,0,-1,1.2,4,1.2);model.box('right_hand','gold',-3,3,-1.5,6,1,2);model.box('right_hand','metal',-.8,4,-1,1.6,12,1.2)
            model.box('left_forearm','metal',-3,-1,-4,6,10,1);model.box('left_forearm','red',-2,-.4,-4.3,4,8,.4);model.box('left_forearm','white',-.5,0,-4.6,1,7,.4)
        elif kind=='axeman':
            model.box('right_hand','strap',-.6,-3,-1,1.2,15,1.2);model.box('right_hand','metal',-.8,8,-1.5,5,4,2)
        else:
            model.box('right_hand','strap',-.6,-12,-1,1.2,28,1.2);model.box('right_hand','metal',-.8,-17,-1.2,1.6,6,1.6);model.box('right_hand','metal',-.8,-11,-1.2,5,4,1.6)
        p=copy.deepcopy(base);bones,boxes=actors.convert(model);p.update(id=model.kind,name=name,bones=bones,boxes=boxes,damage=5 if kind!='pikeman' else 6,meleeReach=1.3 if kind=='pikeman' else .8,animations=clips_for(model,{}))
        (directory/(model.kind+'.json')).write_text(json.dumps(p,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    # Base enemies are references, never duplicated models or replacement animations.
    templates=ROOT/'src/main/resources/assets/zombierool/map_actor_templates'
    for id,source,name,health in [('ghoul','scourge_ghoul','Ghoul',40),('scourge_zombie','scourge_zombie','Scourge Zombie',40),('skeleton','scourge_skeleton','Skeleton',28),('abomination','abomination','Abomination',250),('geist','geist','Geist',40)]:
        p=dict(id='builtin_'+id,name=name,nameKey='entity.zombierool.map_actor.'+id,baseEntity=source,behavior='melee',health=health,damage=3,speed=.23,aggroRadius=12,wanderRadius=5,leashRadius=28,scale=1,width=.6,height=1.8,undead=True,countsForWave=False,boss=False,callback='',knockbackResistance=.2)
        (templates/('builtin_'+id+'.json')).write_text(json.dumps(p,separators=(',',':')),encoding='utf-8')
    for p in directory.glob('scarlet_zombie*.json'):
        d=json.loads(p.read_text(encoding='utf-8'));id=p.stem
        outfit=[]
        def cube(bone,material,*box):outfit.append(dict(bone=bone,material=art.MATERIALS.index(material),box=[float(v) for v in box]))
        cube('torso','metal',-4.4,-.3,-2.4,8.8,8,4.8)
        cube('torso','white',-3.2,.6,-2.8,6.4,10,.4)
        cube('torso','red',-.65,2,-3.3,1.3,5,.5);cube('torso','red',-2,3,-3.3,4,1.3,.5)
        cube('head','metal',-4.4,-9,-4.3,8.8,2,8.6)
        for side in ['left','right']:cube(side+'_arm','metal',-2.6,-1.5,-2.6,5.2,3.5,5.2)
        if id.endswith('swordsman'):
            cube('right_hand','strap',-.6,-1,-1,1.2,4,1.2);cube('right_hand','gold',-3,-2,-1.5,6,1,2);cube('right_hand','metal',-.8,-15,-1,1.6,13,1.2)
            cube('left_forearm','metal',-3,-1,-3.8,6,10,1);cube('left_forearm','red',-2,-.4,-4.1,4,8,.4);cube('left_forearm','white',-.5,0,-4.5,1,7,.4)
        elif id.endswith('axeman'):
            cube('right_hand','strap',-.6,-12,-1,1.2,15,1.2);cube('right_hand','metal',-.8,-12,-1.5,5,4,2)
        elif id.endswith('pikeman'):
            cube('right_hand','strap',-.6,-21,-1,1.2,28,1.2);cube('right_hand','metal',-.8,-27,-1.2,1.6,6,1.6);cube('right_hand','metal',-.8,-21,-1.2,5,4,1.6)
        names={'scarlet_zombie':'Scarlet Zombie','scarlet_zombie_swordsman':'Scarlet Swordsman','scarlet_zombie_axeman':'Scarlet Axeman','scarlet_zombie_pikeman':'Scarlet Halberdier'}
        d.update(baseEntity='scourge_zombie',name=names[id].replace('Scarlet','The Risen'),faction='risen',speedMode='walk',nameKey='entity.zombierool.map_actor.'+id,armorBoxes=outfit)
        for key in ['boxes','bones','animations','texture','textureWidth','textureHeight','sourceMotion']:d.pop(key,None)
        p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    for p in directory.glob('*.json'):
        d=json.loads(p.read_text(encoding='utf-8'))
        d['nameKey']='entity.zombierool.map_actor.'+p.stem
        if p.stem=='crypt_fiend':d.update(name='Crypt Fiend',health=40,damage=2,meleeCooldown=30,speed=.19,knifeMode='wave')
        if p.stem=='raised_skeleton':
            d.update(name='Scourge Skeleton — Rivendare Minion',baseEntity='scourge_skeleton',knockbackResistance=1,knifeMode='wave',knifeHits=1)
            for key in ['boxes','bones','animations','texture','textureWidth','textureHeight','sourceMotion']:d.pop(key,None)
        p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    from refine_scarlet_equipment import main as refine
    refine()
    from build_risen_players import main as risen_players
    risen_players()
    print('Native entity references and map-owned Scarlet equipment generated.')

if __name__=='__main__':main()
