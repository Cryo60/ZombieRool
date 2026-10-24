"""Create map-owned examples. Never adds named Warcraft enemies to the mod registry."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'examples/stratholme'

def box(bone, x, y, z, sx, sy, sz, color):
    return dict(zip(('bone','x','y','z','sx','sy','sz','r','g','b'), (bone,x,y,z,sx,sy,sz,*color)))

def human(armor=(.65,.12,.1), skin=(.45,.6,.4)):
    return [box('head',-4,-8,-4,8,8,8,skin),box('body',-4,0,-2,8,12,4,armor),box('left_arm',4,0,-2,4,12,4,skin),box('right_arm',-8,0,-2,4,12,4,skin),box('left_leg',0,12,-2,4,12,4,(.2,.18,.16)),box('right_leg',-4,12,-2,4,12,4,(.2,.18,.16))]

def profile(id, name, model, **changes):
    n=dict(id=id,name=name,behavior='melee',boss=False,undead=True,countsForWave=False,health=40,damage=3,speed=.23,aggroRadius=12,wanderRadius=5,leashRadius=28,scale=1,width=.6,height=1.8,callback='',boxes=model)
    n['animations']={
        'idle':{'duration':60,'loop':True,'frames':[{'time':0,'body':{'y':0}},{'time':30,'body':{'y':-.3}},{'time':60,'body':{'y':0}}]},
        'cast':{'duration':30,'loop':False,'frames':[{'time':0,'left_arm':{'rx':0},'right_arm':{'rx':0}},{'time':12,'left_arm':{'rx':-110},'right_arm':{'rx':-110}},{'time':30,'left_arm':{'rx':-100},'right_arm':{'rx':-100}}]},
        'death':{'duration':24,'loop':False,'frames':[{'time':0,'root':{'rx':0}},{'time':24,'root':{'rx':85}}]}
    }
    n.update(changes)
    path=OUT/'zombierool/gameplay/entities'/f'{id}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

scarlet=human()
for side in [-1,1]:
    scarlet.append(box('left_arm' if side>0 else 'right_arm',4 if side>0 else -9,-1,-3,5,4,6,(.72,.7,.64)))
scarlet += [box('head',-4.5,-9,-4.5,9,3,9,(.63,.64,.61)),box('body',-3,0,-2.15,6,10,.25,(.85,.82,.7)),box('body',-.7,2,-2.5,1.4,6,.4,(.7,.06,.05)),box('body',-2,3,-2.5,4,1.4,.4,(.7,.06,.05))]
scarlet += [box('head',-3,-5,-4.15,2,1.5,.2,(.09,.1,.06)),box('head',1,-5,-4.15,2,1.5,.2,(.09,.1,.06)),box('head',-2,-2,-4.15,4,1,.2,(.2,.16,.08)),box('head',-4,-2,-4.2,1,2,.3,(.36,.12,.08))]
profile('scarlet_zombie','Croisé écarlate déchu',scarlet,health=65,damage=4,behavior='guard',aggroRadius=10,leashRadius=18)
profile('raised_skeleton','Serviteur de Rivendare',human((.35,.3,.4),(.78,.78,.64)),health=28,damage=2,aggroRadius=24,leashRadius=40)

crypt=[box('body',-6,7,-6,12,9,16,(.2,.2,.38)),box('body',-4,-1,-6,8,10,9,(.27,.25,.46)),box('head',-5,-6,-9,10,7,8,(.27,.25,.47)),box('head',-4,-4,-9.5,2,1,.6,(.25,.9,.55)),box('head',2,-4,-9.5,2,1,.6,(.25,.9,.55))]
for side in [-1,1]:
    crypt += [box('head',side*5-1,-10,-6,2,6,2,(.65,.62,.43)),box('head',side*3-1,-1,-12,2,4,5,(.74,.71,.52))]
    for i in range(3):
        bone='left_leg' if side>0 else 'right_leg';x=5 if side>0 else -14;z=-5+i*6
        crypt += [box(bone,x,11,z,9,3,3,(.27,.25,.46)),box(bone,12 if side>0 else -15,13,z,3,11,3,(.17,.17,.32))]
profile('crypt_fiend','Démon des cryptes',crypt,health=85,damage=4,width=1.35,height=1.9,speed=.21,aggroRadius=18,leashRadius=30,callback='crypt_fiend')

baron=human((.2,.18,.29),(.64,.7,.68))
baron += [box('head',-5,-10,-4.5,10,4,9,(.28,.25,.4)),box('head',-5,-15,-3,2,6,2,(.5,.46,.66)),box('head',3,-15,-3,2,6,2,(.5,.46,.66)),box('body',-5,4,2,10,12,1,(.18,.1,.28)),box('right_arm',-9,9,-3,2,16,2,(.56,.58,.65))]
# Skeletal charger: rider is raised; the horse has four articulated limb groups.
for b in baron:
    b['y']-=12
baron += [box('body',-5,7,-10,10,6,21,(.62,.64,.56)),box('body',-3,0,-12,6,11,6,(.66,.68,.6)),box('body',-3,-1,-17,6,5,10,(.7,.71,.63))]
for side in [-1,1]:
    for z in [-7,7]:baron.append(box('left_leg' if side>0 else 'right_leg',side*4-1,12,z,2,12,3,(.59,.61,.54)))
profile('baron_rivendare','Baron Rivendare',baron,boss=True,health=1200,damage=5,width=1.2,height=2.9,speed=.26,aggroRadius=28,leashRadius=48,wanderRadius=0,callback='baron_rivendare')

bal=human((.27,.13,.18),(.47,.25,.31))
bal += [box('head',-6,-12,-3,2,7,2,(.48,.44,.32)),box('head',4,-12,-3,2,7,2,(.48,.44,.32)),box('head',-3,-5,-4.5,2,1,.7,(.13,.95,.25)),box('head',1,-5,-4.5,2,1,.7,(.13,.95,.25))]
for side in [-1,1]:
    for tier in range(5):
        x=(5+tier*2) if side>0 else (-9-tier*2)
        bal.append(box('left_wing' if side>0 else 'right_wing',x,-8+tier*2,3,4,15-tier*2,1.5,(.3,.13,.23)))
    bal.append(box('left_wing' if side>0 else 'right_wing',4 if side>0 else -17,-10,2.5,13,2,2,(.51,.32,.38)))
profile('balnazzar','Balnazzar',bal,boss=True,undead=False,health=1500,damage=5,scale=1.35,width=.95,height=2.15,speed=.22,aggroRadius=28,leashRadius=44,wanderRadius=0,callback='balnazzar',bones={'left_wing':{'parent':'body','pivotX':4,'pivotY':0,'pivotZ':3},'right_wing':{'parent':'body','pivotX':-4,'pivotY':0,'pivotZ':3}})
balfile=OUT/'zombierool/gameplay/entities/balnazzar.json'
balprofile=json.loads(balfile.read_text(encoding='utf-8'))
balprofile['animations']['idle']['frames']=[{'time':t,'left_wing':{'ry':angle},'right_wing':{'ry':-angle}} for t,angle in [(0,-12),(30,12),(60,-12)]]
balprofile['animations']['cast']['frames']=[{'time':t,'left_wing':{'ry':angle},'right_wing':{'ry':-angle},'left_arm':{'rx':arm},'right_arm':{'rx':arm}} for t,angle,arm in [(0,0,0),(12,-45,-110),(30,-35,-100)]]
balfile.write_text(json.dumps(balprofile,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Created five Stratholme profiles under', OUT)
