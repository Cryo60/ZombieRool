"""Authored Minecraft cuboids and pixel atlases, inspired by the source silhouettes.
No source triangles are voxelized or displayed. Bones use a common articulated
armature so source motion can be retargeted without carrying the source geometry.
"""
from pathlib import Path
import json,random,math
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'src/main/resources/assets/zombierool'
MATERIALS=['skin','shade','cloth','pants','boot','metal','rust','bone','dark','eye','teeth','gut','scar','strap','vest','lens','hair','gold','white','red','blue','hood','wound','chain']
DEFAULT=['#829281','#64715e','#6c7779','#484046','#34332f','#666f70','#785d40','#b7b297','#242726','#d4d9a1','#dbd2a7','#686b3c','#5c3432','#66513c','#69694d','#91b7b4','#545146','#ad9462','#afb3a4','#814b43','#718b9d','#65634d','#573e3b','#888778']
def atlas(kind,changes=None):
    colors=dict(zip(MATERIALS,DEFAULT));colors.update(changes or {});image=Image.new('RGBA',(256,256));draw=ImageDraw.Draw(image);rng=random.Random(kind)
    if kind.startswith(('scourge_','geist','abomination')):colors['eye']='#64d9ff'
    for i,mat in enumerate(MATERIALS):
        x=i%4*64;y=i//4*32;base=tuple(bytes.fromhex(colors[mat][1:]));draw.rectangle((x,y,x+63,y+31),fill=base)
        def col(delta=0,tint=None,amount=0):
            return tuple(max(0,min(255,round(c+delta if tint is None else c*(1-amount)+tint[k]*amount+delta))) for k,c in enumerate(base))
        def rect(a,b,c,d,fill):draw.rectangle((x+a,y+b,x+c,y+d),fill=fill)
        def line(points,fill):draw.line([(x+a,y+b) for a,b in points],fill=fill)
        # Small cuboids sample only the first few texels of this tile. Details
        # therefore live throughout the tile, including its top-left UV region.
        for py in range(0,32,4):
            for px in range(0,64,4):
                delta=rng.choice([-17,-10,-5,3,8,14])
                w,h=rng.choice([(3,2),(2,3),(3,3)])
                rect(px,py,min(63,px+w),min(31,py+h),col(delta))
                if rng.random()<.35:rect(px,py,min(63,px+1),py,col(delta+10))
        if mat in ['skin','shade']:
            undead=kind.startswith(('abomination','scourge','geist'))
            # Broad mottling, inset creases and sparse branching veins.
            for py in range(2,30,9):
                for px in range(2,61,11):
                    rect(px,py,px+3,py+1,col(-12,(100,69,80),.24 if undead else .08))
                    rect(px+1,py+2,px+4,py+3,col(-5,(110,77,82),.24 if undead else .06))
                    line([(px-1,py+4),(px+1,py+4),(px+1,py+5),(px+4,py+5)],col(-28))
                    line([(px,py+6),(px+3,py+6)],col(17))
            if undead:
                for px,py in [(7,3),(22,17),(36,6),(51,22)]:
                    line([(px,py),(px,py+3),(px+1,py+3),(px+1,py+6)],col(-25,(82,65,86),.35))
                    line([(px+1,py+3),(px+3,py+2)],col(-18))
            if kind.startswith('abomination'):
                for px,py in [(14,12),(37,3),(52,17)]:
                    line([(px,py),(px+2,py+1),(px+4,py+1),(px+6,py+2)],'#51463d')
                    for a in [1,4]:line([(px+a,py-1),(px+a,py+3)],'#c4b18b')
        elif mat in ['cloth','pants','vest','hood','white','red','blue']:
            # Two-tone folds and one-pixel seams; worn edges remain readable
            # at the same pixel density as an ordinary Minecraft player skin.
            for px in range(2,63,8):
                line([(px,0),(px,5),(px+1,6),(px+1,12),(px,15),(px,31)],col(-25))
                line([(px+1,0),(px+1,4),(px+2,6),(px+2,11)],col(19))
            for py in [7,22]:
                line([(0,py),(63,py)],col(-24))
                for px in range(0,63,3):rect(px,py+1,px,py+1,col(25))
            if kind.startswith(('scourge','geist')):
                for px,py in [(5,4),(17,13),(31,3),(45,19),(57,8)]:
                    rect(px,py,px+2,py+2,col(-39));rect(px+1,py+3,px+2,py+3,col(21))
            if kind.startswith('astronaut'):
                for px in range(6,61,16):
                    rect(px,10,px+5,15,col(-30));rect(px+1,11,px+4,14,col(9))
        elif mat in ['metal','rust','chain','gold']:
            for px in range(0,64,8):
                line([(px,0),(px,31)],col(-32));line([(px+1,0),(px+1,31)],col(29))
                for py in [2,14,26]:
                    rect(px+3,py,px+4,py+1,col(-38));rect(px+3,py,px+3,py,col(60))
            for px,py in [(4,6),(18,18),(29,9),(42,24),(56,3)]:
                line([(px,py+2),(px+2,py),(px+4,py)],col(45))
                rect(px+1,py+3,px+2,py+3,col(-26))
            if mat=='rust':
                for px,py in [(2,7),(10,3),(22,17),(34,5),(46,22),(58,12)]:
                    rect(px,py,px+3,py+2,col(-15,(151,77,38),.65))
                    rect(px+1,py,px+2,py,col(12,(170,103,44),.65))
        elif mat in ['bone','teeth']:
            for px in range(2,62,7):
                line([(px,1),(px,8),(px+1,9),(px+1,20),(px,22),(px,30)],col(-29))
                line([(px+1,1),(px+1,7)],col(26))
            for px,py in [(7,4),(22,13),(37,6),(53,23)]:
                line([(px,py),(px+1,py+2),(px,py+3),(px,py+5)],col(-52))
                line([(px+1,py+2),(px+3,py+2)],col(-39))
        elif mat in ['gut','wound','scar']:
            for py in range(2,31,7):
                line([(0,py+2),(3,py),(9,py),(12,py+2),(18,py+2),(21,py),(28,py),(32,py+2),(40,py+2),(44,py),(53,py),(58,py+2),(63,py+2)],col(-35))
                line([(0,py+3),(3,py+1),(9,py+1),(12,py+3),(18,py+3),(21,py+1),(28,py+1),(32,py+3),(40,py+3),(44,py+1),(53,py+1),(58,py+3),(63,py+3)],col(24))
                if mat=='scar':
                    for px in range(2,63,4):
                        line([(px,py-1),(px,py+3)],'#c8b59a');rect(px,py+4,px,py+4,'#3d2c2a')
        elif mat in ['strap','boot']:
            for py in [2,12,25]:
                line([(0,py),(63,py)],col(-30));line([(0,py+1),(63,py+1)],col(21))
                if mat=='strap':
                    for px in range(1,63,3):rect(px,py+2,px,py+2,col(34))
            for px in range(4,63,12):rect(px,5,px+1,6,col(-38))
        elif mat=='hair':
            for px in range(0,64,3):
                line([(px,0),(px,12),(px+1,15),(px+1,30)],col(rng.choice([-32,-21,19,30])))
                rect(px+1,2,px+1,6,col(30))
        elif mat=='lens':
            for py in range(32):
                rect(0,py,63,py,col(28-py*2))
            for px in range(0,64,8):
                line([(px,0),(px+4,4),(px+4,6)],col(48))
                line([(px+1,0),(px+5,4)],col(73))
        elif mat=='dark':
            for py in [3,10,18,27]:line([(0,py),(63,py)],col(-13))
        elif mat=='eye':
            for px in range(0,64,4):
                for py in range(0,32,4):
                    rect(px,py,px+1,py+1,col(34));rect(px+2,py+2,px+3,py+3,col(-40))
    path=OUT/'textures/entities/blocky'/(kind+'.png');path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
    return image

class Model:
    def __init__(self,kind,scale=1):self.kind=kind;self.parts=[];self.by={};self.scale=scale
    def part(self,name,parent=None,p=(0,0,0),rot=(0,0,0),gore=0):
        row={'name':name,'pivot':p,'rotation':rot,'cubes':[],'gore':gore}
        if parent:row['parent']=parent
        self.parts.append(row);self.by[name]=row;return name
    def box(self,part,material,x,y,z,w,h,d):
        assert w>0 and h>0 and d>0
        assert 2*(w+d)<=64 and h+d<=32,(self.kind,material,w,h,d)
        self.by[part]['cubes'].append({'material':MATERIALS.index(material),'box':[x,y,z,w,h,d]})
    def extra(self,parent,name,mat,box,p=(0,0,0),rot=(0,0,0)):
        self.part(name,parent,p,rot);self.box(name,mat,*box);return name
    def face(self,head,skull=False,one_eye=False,front=-4):
        for x in [-2.5,1]:
            if one_eye and x==1:continue
            self.box(head,'dark',x,-5,front-.1,2,2,.3);self.box(head,'eye',x+.5,-4.5,front-.35,1,1,.3)
        self.box(head,'dark',-.5,-3,front-.15,1,1.5,.3)
        for x in [-2,-.5,1]:self.box(head,'teeth',x,-1.5,front-.25,1,1,.5)
    def limbs(self,width=4,arm=6,fore=6,leg=6,shin=6,body_y=0,arm_x=5,leg_x=2,skin='skin',pants='pants',gore=False):
        for side,x,mask in [('left',arm_x,2),('right',-arm_x,4)]:
            a=self.part(side+'_arm','torso',(x,body_y+1,0),gore=mask if gore else 0);self.box(a,skin,-width/2,-1,-width/2,width,arm,width)
            f=self.part(side+'_forearm',a,(0,arm-1,0),gore=(32 if side=='left' else 64) if gore else 0);self.box(f,skin,-width/2,0,-width/2,width,fore-1,width)
            hand=self.part(side+'_hand',f,(0,fore-1,0),gore=(128 if side=='left' else 256) if gore else 0);self.box(hand,'skin',-width/2,0,-width/2,width,2,width)
        for side,x,mask in [('left',leg_x,8),('right',-leg_x,16)]:
            a=self.part(side+'_leg',None,(x,24-leg-shin,0),gore=mask if gore else 0);self.box(a,pants,-width/2,0,-width/2,width,leg,width)
            f=self.part(side+'_shin',a,(0,leg,0));self.box(f,pants,-width/2,0,-width/2,width,shin,width)
            self.box(f,'boot',-width/2-.3,shin-2,-width/2-1,width+.6,2,width+1.5)
    def save(self):
        data={'format':1,'kind':self.kind,'texture':'zombierool:textures/entities/blocky/'+self.kind+'.png','scale':self.scale,'parts':self.parts}
        folder=OUT/'models/entities/blocky';folder.mkdir(parents=True,exist_ok=True);(folder/(self.kind+'.json')).write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')
        return {'parts':len(self.parts),'cuboids':sum(len(p['cubes']) for p in self.parts),'scale':self.scale}

def humanoid(kind,scale=1):
    m=Model(kind,scale);m.part('torso');m.box('torso','cloth',-4,0,-2,8,12,4);m.part('head','torso',(0,0,0),gore=1);m.box('head','skin',-4,-8,-4,8,8,8);m.limbs(gore=kind.startswith('scourge'));return m

def abomination():
    m=Model('abomination',1.08);m.part('torso',(None), (0,-1,1));m.box('torso','skin',-10,-3,-6,20,7,12)
    m.box('torso','shade',-11,4,-5,5,12,12);m.box('torso','skin',6,4,-5,5,12,12);m.box('torso','skin',-8,14,-6,16,4,12)
    # The belly is an actual recessed opening with separate block organs and a stitched rim.
    m.box('torso','wound',-6,4,-3,12,10,8)
    for x,y,w,h in [(-5,5,5,3),(0,7,5,3),(-4,10,6,3),(2,11,4,3)]:m.box('torso','gut',x,y,-6.5,w,h,3)
    for x,y in [(-7,4),(6,5),(-7,9),(6,10)]:m.box('torso','scar',x,y,-6.8,2,4,1)
    for x in [-5,-1,3]:m.box('torso','teeth',x,2.8,-6.8,2,2.5,1)
    m.box('torso','shade',-7,-5,3,14,10,5);
    m.box('torso','shade',-11,4,-6.5,4,6,7);m.box('torso','skin',7,6,-6.5,4,7,7)
    m.box('torso','strap',-10,16,-6.5,20,2,12);m.box('torso','metal',-2,15.5,-7.3,4,3,1)
    m.part('head','torso',(-1,-5,-1));m.box('head','skin',-4,-8,-4,8,8,8);m.box('head','shade',-4,-1,-4.5,8,2,6);m.face('head');m.box('head','scar',2,-7,-4.4,1.5,5,.5)
    m.limbs(width=5,arm=8,fore=7,leg=4,shin=5,arm_x=13,leg_x=5,body_y=-2,pants='shade')
    m.box('left_arm','scar',-2.7,1,-2.7,5.4,2,5.4);m.box('right_forearm','strap',-2.8,2,-2.8,5.6,2,5.6)
    m.extra('right_hand','cleaver_grip','strap',[-.8,1,-1,1.6,8,1.6]);m.extra('right_hand','cleaver_blade','metal',[-4,6,-1.1,8,8,1.3]);m.box('cleaver_blade','rust',-4,6,-1.3,2,8,.4)
    m.extra('left_hand','chain_anchor','chain',[-1,1,-1,2,2,2])
    for i in range(4):m.extra('left_hand','chain_'+str(i),'chain',[-.6,3+i*1.7,-.7,1.2,2,1.4])
    m.extra('left_hand','hook_down','metal',[-.8,9,-1,1.6,5,1.6]);m.extra('left_hand','hook_bottom','metal',[-3.5,13,-1,4.3,1.6,1.6]);m.extra('left_hand','hook_point','metal',[-4,10.5,-1,1.5,3,1.6])
    m.part('third_arm','torso',(8,-4,4),(-.35,0,.7));m.box('third_arm','skin',-2,-8,-2,4,9,4);m.part('third_forearm','third_arm',(0,-8,0),(-.6,0,-.8));m.box('third_forearm','shade',-2,-6,-2,4,7,4);m.box('third_forearm','skin',-2.5,-8,-2.5,5,3,5)
    for x in [-2,1]:m.box('third_forearm','bone',x,-10,-3,1,3,2)
    return m

def george():
    m=humanoid('george_romero',1.24);m.by['torso']['cubes']=[];m.box('torso','cloth',-5,0,-3,10,12,6)
    for x in [-5,1]:m.box('torso','vest',x,0,-4,4,11,2);m.box('torso','strap',x+.5,5,-4.8,3,3,1)
    m.box('torso','vest',-5,0,3,10,11,1);m.box('torso','strap',-5,11,-3.3,10,1.5,6.6)
    for side in ['left','right']:m.by[side+'_arm']['cubes'][0]['material']=MATERIALS.index('cloth')
    m.box('head','hair',-4,-2,-4.1,8,2,1);m.box('head','hair',-4,-7.7,-3.9,8,1,7.8)
    for x in [-3.8,.5]:m.box('head','dark',x,-5.5,-4.25,3.3,2.2,.5);m.box('head','lens',x+.5,-5.1,-4.55,2.3,1.2,.3)
    m.box('head','dark',-.6,-5,-4.3,1.2,.5,.4);m.box('head','skin',-.6,-4,-4.8,1.2,2.3,1)
    m.extra('head','pony_tail','hair',[-1.5,-2,3.5,3,5,2]);m.box('head','teeth',-1.5,-1,-4.3,3,.5,.4)
    m.extra('right_hand','projector_handle','metal',[-1,0,-1,2,6,2]);m.extra('right_hand','projector','metal',[-4,4,-4,8,7,8]);m.box('projector','dark',-3,5,-5,6,5,1.5);m.box('projector','lens',-2,6,-5.6,4,3,.7)
    for x in [-4.5,3.5]:m.box('projector','metal',x,3,-3,1,9,1);m.box('projector','rust',x,4,-2.5,1,1,6)
    m.box('projector','metal',-4.5,3,-3,9,1,1);m.box('projector','metal',-4.5,11,-3,9,1,1)
    return m

def astronaut():
    m=humanoid('astronaut',1.1);m.by['head']['cubes']=[]
    m.box('head','cloth',-5,-9,-4.5,10,2,9);m.box('head','cloth',-5,-7,3,10,8,2);m.box('head','cloth',-5,-7,-4.5,2,8,7.5);m.box('head','cloth',3,-7,-4.5,2,8,7.5);m.box('head','metal',-5,-.5,-4.5,10,2,9)
    m.box('head','dark',-3,-7,-3.8,6,6,1);m.box('head','lens',-2.5,-6.5,-4.1,5,4.5,.4)
    m.box('torso','metal',-3,2,-3.5,6,4,1.5);m.box('torso','red',-2,3,-4.4,1,1,.4);m.box('torso','gold',.5,3,-4.4,1,1,.4)
    m.box('torso','cloth',-4,0,2,8,11,4);m.box('torso','strap',-4,4,2,8,1,4.5)
    for side in ['left','right']:
        for bone in [side+'_arm',side+'_forearm',side+'_leg',side+'_shin']:
            for cube in m.by[bone]['cubes']:cube['material']=MATERIALS.index('cloth')
        m.box(side+'_arm','red',-2.2,2,-2.2,4.4,1.5,4.4);m.box(side+'_shin','strap',-2.2,1,-2.2,4.4,2,4.4)
    for i in range(5):m.extra('torso','hose_'+str(i),'strap',[3+i*.3,5+i*.7,-3-i*.5,1.3,1.5,1.3])
    return m

def thief():
    m=humanoid('pentagon_thief');m.box('torso','white',-4,0,-2.8,3,15,1.5);m.box('torso','white',1,0,-2.8,3,15,1.5);m.box('torso','white',-4,0,2,8,15,1)
    m.box('torso','gold',-.5,1,-3,1,6,.6);m.box('torso','strap',2,7,-3.6,2.3,3,1);m.box('torso','strap',-4.3,7,-3.6,2.3,3,1)
    m.box('head','hair',-4,-8.2,-4,8,2,8);m.box('head','hair',-4,-6,3.4,8,3,1)
    for x in [-3.8,.5]:m.box('head','dark',x,-5.5,-4.3,3.3,2,.6);m.box('head','lens',x+.5,-5.1,-4.65,2,1,.3)
    m.box('head','dark',-.5,-5,-4.4,1,.5,.4);m.box('head','skin',-.5,-3.5,-4.6,1,2,1);m.box('head','shade',-2,-1.5,-4.2,4,.5,.4)
    for side in ['left','right']:
        m.by[side+'_arm']['cubes'][0]['material']=MATERIALS.index('white');m.by[side+'_forearm']['cubes'][0]['material']=MATERIALS.index('white');m.by[side+'_hand']['cubes'][0]['material']=MATERIALS.index('dark')
    return m

def ghoul():
    m=humanoid('scourge_ghoul');m.by['torso']['pivot']=(0,1.5,1);m.by['head']['pivot']=(0,0,-3);m.by['torso']['cubes']=[];m.box('torso','skin',-4.5,-1,-3,9,9.5,6);m.box('torso','shade',-4,-2,1,8,7,4);m.box('torso','wound',-2,3,-3.3,4,5,.5)
    # A narrow connected waist exposes the upper thighs without removing the
    # bulky chest, hunched shoulders or the existing texture/detail layers.
    m.box('torso','shade',-2.4,8,-2,4.8,2.5,4)
    m.face('head');m.box('head','shade',-4,-1,-4.8,8,2,6);m.box('head','hair',-4,-8.2,-3,8,1,6)
    for side in ['left','right']:
        m.by[side+'_arm']['pivot']=(5 if side=='left' else -5,1,0);m.by[side+'_forearm']['pivot']=(0,7,0);m.by[side+'_forearm']['cubes'][0]['box'][4]=7;m.by[side+'_hand']['pivot']=(0,7,0)
        for x in [-1.7,0,1.5]:m.box(side+'_hand','bone',x,1,-3,1,3,3)
        m.box(side+'_leg','pants',-2.2,1,-2.2,4.4,2,4.4)
    m.box('torso','scar',2,0,-3.6,1.5,7,.6);return m

def zombie():
    m=humanoid('scourge_zombie');m.face('head');m.box('head','shade',-4,-1,-4.5,8,2,6);m.box('head','wound',-4,-6,-4.2,2,3,.5);m.box('head','hair',-4,-8.2,-3,8,1,7)
    m.box('torso','wound',-3,2,-2.3,5,7,.5)
    for y in [3,5,7]:m.box('torso','bone',-2.5,y,-2.7,4,1,.8)
    m.by['left_arm']['cubes'][0]['material']=MATERIALS.index('cloth');m.by['right_arm']['cubes'][0]['material']=MATERIALS.index('cloth');m.box('left_forearm','scar',-2.2,2,-2.2,4.4,1.5,4.4);return m

def skeleton():
    m=Model('scourge_skeleton');m.part('torso');m.box('torso','bone',-4,0,-1.5,8,2,3);m.box('torso','bone',-1,1,1,2,10,2);m.box('torso','bone',-3.5,10,-2,7,3,4)
    for y in [3,5.5,8]:
        for x in [-4,1]:m.box('torso','bone',x,y,-2,3,1,3)
    m.part('head','torso',(0,0,0),gore=1);m.box('head','bone',-4,-8,-3.5,8,6,7);m.box('head','bone',-3,-2,-3.5,6,2,6);m.face('head',True)
    m.limbs(width=2,arm=6,fore=6,leg=6,shin=6,arm_x=5,leg_x=2,skin='bone',pants='bone',gore=True)
    for side in ['left','right']:
        m.by[side+'_hand']['cubes'][0]['material']=MATERIALS.index('bone');m.by[side+'_shin']['cubes'][-1]['material']=MATERIALS.index('bone');m.box(side+'_arm','rust',-2,-1,-2,4,3,4);m.box(side+'_forearm','bone',-1.5,-.5,-1.5,3,2,3)
    m.box('torso','strap',-4,9,-2.4,8,1.5,5);return m

def geist():
    m=Model('geist',.85);m.part('torso',None,(0,-6,2));m.box('torso','hood',-3.5,-1,-3,7,10,6);m.box('torso','skin',-3,4,-3.2,6,5,1)
    m.box('torso','hood',-3.4,8,-2.9,6.8,3,5.8)
    m.part('head','torso',(0,-1,-2));m.box('head','hood',-3,-8,-3,6,8,6);m.box('head','shade',-2.5,-6,-3.4,5,5,1);m.face('head',one_eye=True,front=-3.5);m.box('head','scar',1,-7,-3.7,1.2,5,.5)
    m.limbs(width=2.8,arm=6,fore=7,leg=10,shin=10,arm_x=4.6,leg_x=1.7,skin='skin',pants='hood')
    for side in ['left','right']:
        m.by[side+'_arm']['pivot']=(4.6 if side=='left' else -4.6,0,0)
        for x in [-1.4,0,1.4]:m.box(side+'_hand','bone',x,1,-3,1,2,4)
        m.box(side+'_leg','strap',-1.6,3,-1.6,3.2,1,3.2)
        m.box(side+'_shin','skin',-1.6,-.5,-1.6,3.2,2,3.2)
    for y in [3,5.5,8]:m.box('torso','bone',-2,y,-3.7,3.5,.7,.6)
    m.box('torso','strap',-2,-2,-3.5,4,2,7);m.box('torso','strap',-2.3,-1.7,-3.9,4.6,1.4,1)
    m.extra('torso','noose','strap',[-.5,-1,3,1,14,1]);m.box('noose','strap',-.8,8,2.8,1.6,2,1.4);return m

def main():
    report={}
    for builder,changes in [(abomination,{'skin':'#92977b','shade':'#747b63','pants':'#564638','wound':'#442c30','gut':'#a19876'}),(george,{'skin':'#aaa39a','cloth':'#929da4','vest':'#686b4e','pants':'#40474a','hair':'#74716b','lens':'#526b74'}),(astronaut,{'cloth':'#aeb0a4','skin':'#666963','lens':'#293e42','strap':'#6b6256'}),(thief,{'skin':'#9f9675','white':'#b7b69b','cloth':'#847a59','pants':'#5c4e3e','hair':'#afae99','lens':'#465341'}),(ghoul,{}),(zombie,{}),(skeleton,{}),(geist,{'skin':'#9d9470','hood':'#686653','eye':'#dbd485'})]:
        m=builder();report[m.kind]=m.save();atlas(m.kind,changes)
    atlas('george_romero_enraged',{'skin':'#878f90','cloth':'#86969d','vest':'#5b6252','pants':'#3f464a','hair':'#696d69','lens':'#b0e2eb','eye':'#b7edfa'})
    (ROOT/'assets-source/26.7/enemy-skins/blocky-designs.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(report)
if __name__=='__main__':main()
