"""Use ZombieRool's existing authored-cuboid/pixel-atlas workflow for map actors.

Only selected LOCAL M2 bone tracks are read. Source polygons are never exported.
Final models, textures and retargeted clips go in examples/stratholme, not mod assets.
"""
from pathlib import Path
import sys, json, math, struct, hashlib, runpy
import numpy as np
from PIL import Image
import build_blocky_enemies as art
from local_casc import LocalCasc

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'examples/stratholme'
art.OUT=ROOT/'build/map-actors/authored'
B=np.array([[0,1,0],[0,0,-1],[-1,0,0.]])

def rotation(angles):
    x,y,z=angles;cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

def quaternion(r):
    # Stable matrix-to-quaternion conversion, same convention as the existing rigs.
    m=np.eye(4);m[:3,:3]=r
    from importlib.util import spec_from_file_location,module_from_spec
    trace=np.trace(r)
    if trace>0:
        s=math.sqrt(trace+1)*2;q=np.array([(r[2,1]-r[1,2])/s,(r[0,2]-r[2,0])/s,(r[1,0]-r[0,1])/s,s/4])
    else:
        i=int(np.argmax(np.diag(r)));j=(i+1)%3;k=(i+2)%3;s=math.sqrt(max(0,1+r[i,i]-r[j,j]-r[k,k]))*2;q=np.zeros(4);q[i]=s/4;q[j]=(r[j,i]+r[i,j])/max(s,1e-8);q[k]=(r[k,i]+r[i,k])/max(s,1e-8);q[3]=(r[k,j]-r[j,k])/max(s,1e-8)
    return q/max(1e-8,np.linalg.norm(q))

class Source:
    def __init__(self,casc,fd):
        raw=casc.read(fd);self.hash=hashlib.sha256(raw).hexdigest();self.fd=fd
        self.md=raw[8:8+struct.unpack_from('<I',raw,4)[0]];d=self.md
        self.nb,self.ob=struct.unpack_from('<II',d,44);self.na,self.oa=struct.unpack_from('<II',d,28)
        self.pivots=np.array([struct.unpack_from('<3f',d,self.ob+i*88+76) for i in range(self.nb)])
        nv,ov=struct.unpack_from('<II',d,60);self.height=max(struct.unpack_from('<3f',d,ov+i*48)[2] for i in range(nv))-min(struct.unpack_from('<3f',d,ov+i*48)[2] for i in range(nv))
        self.parents=[struct.unpack_from('<h',d,self.ob+i*88+8)[0] for i in range(self.nb)]
        self.keys={struct.unpack_from('<i',d,self.ob+i*88)[0]:i for i in range(self.nb) if struct.unpack_from('<i',d,self.ob+i*88)[0]>=0}
        self.animations=[struct.unpack_from('<HHIfI',d,self.oa+i*64) for i in range(self.na)]
    def clip(self,ids):
        index=next(i for wanted in ids for i,a in enumerate(self.animations) if a[0]==wanted);seen=set()
        while self.animations[index][4]&0x40:
            assert index not in seen;seen.add(index);index=struct.unpack_from('<H',self.md,self.oa+index*64+62)[0]
        assert self.animations[index][4]&0x20,'External animation must be explicitly imported'
        return index
    def track(self,bone,which,index,time,default):
        d=self.md;mode,global_seq,nt,ot,nv,ov=struct.unpack_from('<HH4I',d,self.ob+bone*88+16+which*20)
        if global_seq!=65535:index=0
        if index>=min(nt,nv):return np.array(default,dtype=float)
        n,ts=struct.unpack_from('<II',d,ot+index*8);vc,vo=struct.unpack_from('<II',d,ov+index*8);n=min(n,vc)
        if not n:return np.array(default,dtype=float)
        times=np.frombuffer(d,'<u4',n,ts);values=np.frombuffer(d,'<u2' if which==1 else '<f4',n*(4 if which==1 else 3),vo).reshape(n,-1).astype(float)
        if which==1:values=(values-32767)/32768
        a=max(0,min(n-1,int(np.searchsorted(times,time,side='right')-1)));b=min(a+1,n-1);mix=0 if a==b or mode==0 else (time-float(times[a]))/max(1,float(times[b])-float(times[a]));right=values[b]
        if which==1 and values[a]@right<0:right=-right
        value=values[a]*(1-mix)+right*mix
        return value/max(np.linalg.norm(value),1e-9) if which==1 else value
    def frame(self,index,time):
        values=[None]*self.nb
        def world(i):
            if values[i] is None:
                x,y,z,w=self.track(i,1,index,time,[0,0,0,1]);r=np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
                local=np.eye(4);local[:3,:3]=r@np.diag(self.track(i,2,index,time,[1,1,1]));local[:3,3]=self.pivots[i]+self.track(i,0,index,time,[0,0,0])-local[:3,:3]@self.pivots[i]
                values[i]=world(self.parents[i])@local if self.parents[i]>=0 else local
            return values[i]
        return [world(i) for i in range(self.nb)]

def scarlet():
    m=art.zombie();m.kind='scarlet_zombie'
    # Keep the established Scourge jaw, exposed ribs, elbow/wrist/knee rig and wounds.
    m.by['torso']['cubes']=[c for c in m.by['torso']['cubes'] if c['material'] not in [7,22]]
    m.box('torso','metal',-4.4,-.3,-2.4,8.8,8,4.8);m.box('torso','white',-3.2,.6,-2.8,6.4,10,.4)
    m.box('torso','red',-.65,2,-3.3,1.3,5,.5);m.box('torso','red',-2,3,-3.3,4,1.3,.5)
    m.box('torso','strap',-4.5,9,-2.6,9,1.5,5.2);m.box('torso','gold',-1,9,-3.1,2,1.5,.5)
    m.box('head','metal',-4.4,-9,-4.3,8.8,3.3,8.6);m.box('head','red',-.8,-10,-3.5,1.6,2.2,7)
    for side in ['left','right']:
        m.box(side+'_arm','metal',-2.6,-1.5,-2.6,5.2,3.5,5.2);m.box(side+'_arm','red',-2.8,-.8,-2.8,5.6,1,5.6)
        m.box(side+'_forearm','metal',-2.2,2.5,-2.2,4.4,2,4.4);m.box(side+'_shin','metal',-2.2,0,-2.3,4.4,4,4.6)
    return m

def balnazzar():
    m=art.humanoid('balnazzar',1.55);m.by['torso']['cubes']=[]
    m.box('torso','skin',-6,-1,-3.5,12,9,7);m.box('torso','shade',-4.5,7,-3,9,5,6)
    m.box('torso','metal',-4,9,-3,8,3,6);m.box('torso','gold',-1,9,-3.5,2,2,.6)
    m.face('head');m.box('head','shade',-3,-2,-4.5,6,3,5)
    for side,s in [('left',1),('right',-1)]:
        m.by[side+'_arm']['pivot']=(s*6.2,1,0)
        m.box(side+'_arm','metal',-2.6,-1.8,-2.7,5.2,3.4,5.4)
        m.extra(side+'_arm',side+'_spike','bone',[-.7,-4,-.7,1.4,4,1.4],p=(s*1.5,-1,0),rot=(0,0,s*.35))
        for x in [-1.5,0,1.5]:m.box(side+'_hand','bone',x,1,-3.5,.8,2.5,2)
        m.extra('head',side+'_horn','dark',[-1,-6,-1,2,6,2],p=(s*4,-6,0),rot=(.3,0,s*.5))
        m.box(side+'_horn','bone',-.7,-8,-.7,1.4,3,1.4)
        wing=m.part(side+'_wing','torso',(s*4,-1,3),rot=(0,s*-.25,0))
        m.box(wing,'bone',0 if s>0 else -13,-2,-.7,13,1.4,1.4)
        for i in range(4):m.box(wing,'hood',i*3 if s>0 else -i*3-3,0,0,3,13-i*2,.8)
        tip=m.part(side+'_wing_tip',wing,(s*12,-1,0),rot=(0,s*.1,0))
        m.box(tip,'bone',0 if s>0 else -8,-1,-.7,8,1.2,1.4)
        for i in range(3):m.box(tip,'hood',i*2.5 if s>0 else -i*2.5-2.5,0,0,2.5,7-i*2,.65)
    return m

def baron():
    m=art.humanoid('baron_rivendare',1)
    m.by['torso']['pivot']=(0,-12,0);m.by['torso']['cubes']=[]
    m.box('torso','metal',-4.5,-1,-2.8,9,9,5.6);m.box('torso','hood',-4,6,-2.4,8,6,5)
    m.box('torso','gold',-4.7,7,-2.9,9.4,1,5.8);m.box('torso','gold',-1,7,-3.5,2,2,.6)
    m.box('torso','hood',-4.5,0,2.5,9,16,.6);m.face('head');m.box('head','hair',-4.3,-8.4,-2.5,8.6,9,7)
    for s,side in [(1,'left'),(-1,'right')]:
        m.box(side+'_arm','metal',-2.9,-2,-2.9,5.8,4,5.8);m.box(side+'_arm','bone',-2,-2.6,-3.5,4,2,1)
        m.box(side+'_forearm','metal',-2.3,1.5,-2.3,4.6,3,4.6)
        m.by[side+'_leg']['parent']='torso';m.by[side+'_leg']['pivot']=(s*3,10,0);m.by[side+'_leg']['rotation']=(0,0,-s*.7)
    m.box('right_hand','strap',-.6,0,-1,1.2,4,1.2);m.box('right_hand','gold',-3,3.5,-1.5,6,1,2);m.box('right_hand','metal',-.8,4,-1.2,1.6,16,1.4);m.box('right_hand','lens',-.35,5,-1.3,.7,13,.2)
    m.part('horse',None,(0,12,1));m.box('horse','bone',-4,-5,-9,8,6,19);m.box('horse','dark',-3.5,-3,-8,7,4,16)
    for z in [-6,-2,2,6]:
        for s in [-1,1]:m.box('horse','bone',s*4-.5,-4,z,1,6,1)
    m.box('horse','hood',-4.8,-6,-1,9.6,2,11);m.box('horse','gold',-4.8,-6,-1,9.6,.7,1)
    m.part('horse_neck','horse',(0,-4,-8),rot=(-.3,0,0));m.box('horse_neck','bone',-2.5,-10,-2,5,11,4)
    m.part('horse_head','horse_neck',(0,-9,-1));m.box('horse_head','bone',-2.6,-3,-7,5.2,4,9);m.box('horse_head','dark',-2.8,-2,-3.5,5.6,1.6,2);m.box('horse_head','eye',-3,-1.5,-3,.4,.8,.8);m.box('horse_head','eye',2.6,-1.5,-3,.4,.8,.8)
    for s,side in [(1,'left'),(-1,'right')]:
        for end,z in [('front',-7),('back',7)]:
            name=side+'_'+end;m.part(name,'horse',(s*3.5,0,z));m.box(name,'bone',-1,0,-1,2,6,2)
            m.part(name+'_shin',name,(0,6,0));m.box(name+'_shin','bone',-.75,0,-.75,1.5,5,1.5);m.box(name+'_shin','metal',-1.4,4,-2,2.8,1.6,3.5)
    return m

def crypt():
    m=art.Model('crypt_fiend',1.05);m.part('torso',None,(0,10,3));m.box('torso','hood',-5,-4,-4,10,7,11)
    m.box('torso','metal',-5.5,-5,-4.5,11,2.5,12);m.box('torso','hood',-4.5,-3,7,9,7,9)
    m.box('torso','bone',-1,-7,-1,2,3,3);m.box('torso','bone',-1,-7,4,2,3,3)
    m.part('head','torso',(0,-10,-5));m.box('head','skin',-4.5,-5,-5,9,7,8);m.face('head',front=-5)
    m.box('head','metal',-5,-6,-4,10,2,7)
    for side,s in [('left',1),('right',-1)]:
        m.extra('head',side+'_horn','bone',[-.8,-6,-.8,1.6,6,1.6],p=(s*4,-4,-1),rot=(.25,0,s*.3))
        m.part(side+'_arm','head',(s*5,0,-2),rot=(-.3,0,-s*.2));m.box(side+'_arm','skin',-1.2,-1,-1.2,2.4,6,2.4)
        m.part(side+'_forearm',side+'_arm',(0,5,0),rot=(-.4,0,0));m.box(side+'_forearm','metal',-1.5,0,-1.5,3,5,3)
        for i in [0,1]:m.box(side+'_forearm','bone',(-2 if i==0 else 1),4,-3,1,4,3)
        for i,z in enumerate([-3,3,8]):
            name=side+'_leg'+str(i);m.part(name,'torso',(s*4.5,0,z),rot=(0,(i-1)*.3,-s*math.pi/3));m.box(name,'metal',-1,0,-1,2,10,2)
            m.part(name+'_shin',name,(0,10,0),rot=(0,0,s*math.pi/3));m.box(name+'_shin','skin',-.85,0,-.85,1.7,16,1.7);m.box(name+'_shin','bone',-1,14,-1.7,2,2,3)
    return m

def raised_skeleton():
    m=art.skeleton();m.kind='raised_skeleton';return m

def biped_mapping(source):
    k=source.keys;mapping={'torso':k.get(4,k.get(26,0)),'head':k[6],'left_arm':k[0],'right_arm':k[1]}
    for side,armkey,elbowkey,handkey,footkey in [('left',0,81,65,59),('right',1,80,64,60)]:
        arm=k[armkey];hand=k.get(handkey);elbow=k.get(elbowkey)
        if elbow is None:elbow=source.parents[hand] if hand is not None else next((i for i,p in enumerate(source.parents) if p==arm),arm)
        if hand is None:hand=next((i for i,p in enumerate(source.parents) if p==elbow),elbow)
        knee=source.parents[k[footkey]];hip=source.parents[knee]
        mapping.update({side+'_forearm':elbow,side+'_hand':hand,side+'_leg':hip,side+'_shin':knee})
    return mapping

def convert(model):
    bones={};boxes=[];global_pivots={}
    for p in model.parts:
        parent=p.get('parent');pos=np.array(p['pivot'])+global_pivots.get(parent,np.zeros(3));global_pivots[p['name']]=pos
        bones[p['name']]={'parent':parent or 'root',**dict(zip(['pivotX','pivotY','pivotZ'],pos.tolist())),**dict(zip(['rx','ry','rz'],[math.degrees(x) for x in p['rotation']]))}
        for c in p['cubes']:
            x,y,z,sx,sy,sz=c['box'];tile=c['material'];boxes.append({'bone':p['name'],'x':x+pos[0],'y':y+pos[1],'z':z+pos[2],'sx':sx,'sy':sy,'sz':sz,'r':1,'g':1,'b':1,'u':tile%4*64,'v':tile//4*32,'emissive':tile==9})
    return bones,boxes

def retarget(model,source,mapping,choices=None):
    bind=source.frame(source.clip([0]),0);neutral={};auth={p['name']:rotation(p['rotation']) for p in model.parts};by=model.by
    root_index=source.keys.get(26,0);root_neutral=(bind[root_index]@np.r_[source.pivots[root_index],1])[:3]
    unit_scale=32/max(.1,source.height)
    def parent_source(name):
        parent=by[name].get('parent')
        while parent and parent not in mapping:parent=by[parent].get('parent')
        return mapping.get(parent)
    def local(frame,name):
        bone=mapping[name];parent=parent_source(name);world=B@frame[bone][:3,:3]@B.T
        if parent is not None:world=(B@frame[parent][:3,:3]@B.T).T@world
        u,_,v=np.linalg.svd(world);return u@v
    for name in mapping:neutral[name]=local(bind,name)
    clips={};receipt={}
    for name,ids in (choices or {'idle':[0],'walk':[4],'run':[5,4],'attack':[16,17],'cast':[54,53,32,16,0],'death':[1]}).items():
        index=source.clip(ids);duration=source.animations[index][2];count=min(48,max(2,round(duration/50)))
        frames=[]
        for time in np.linspace(0,duration,count):
            original=source.frame(index,time);frame={'time':round(time/50,4)}
            for bone in mapping:
                r=local(original,bone)@neutral[bone].T@auth[bone];q=quaternion(r)
                frame[bone]={key:round(float(value),6) for key,value in zip(['qx','qy','qz','qw'],q)}
            root_point=(original[root_index]@np.r_[source.pivots[root_index],1])[:3]
            shift=B@(root_point-root_neutral)*unit_scale
            if name in ['idle','walk','run']:shift[[0,2]]=0
            shift=np.clip(shift,-16,16)
            # Seat the authored feet, or the whole falling body, against the same floor.
            worlds={};bottom=-1e6
            for part in model.parts:
                bone=part['name'];parent=part.get('parent');r=local(original,bone)@neutral[bone].T@auth[bone] if bone in mapping else auth[bone]
                pr,pp=worlds.get(parent,(np.eye(3),np.zeros(3)));worlds[bone]=(pr@r,pp+pr@np.array(part['pivot']))
                if name!='death' and not any(word in bone for word in ['shin','leg']):continue
                if name=='death' and ('hand' in bone or 'weapon' in bone or bone=='torso' and model.kind=='baron_rivendare'):continue
                wr,wp=worlds[bone]
                for cube in part['cubes']:
                    x,y,z,w,h,d=cube['box']
                    for a in [x,x+w]:
                        for b in [y,y+h]:
                            for c in [z,z+d]:bottom=max(bottom,(wp+wr@np.array([a,b,c]))[1])
            shift[1]=24-bottom
            frame['root']={key:round(float(value),5) for key,value in zip(['x','y','z'],shift)}
            frames.append(frame)
        clips[name]={'duration':round(duration/50,4),'loop':name in ['idle','walk','run'],'frames':frames}
        receipt[name]={'animationID':source.animations[index][0],'durationMs':duration,'retargetedFrames':count}
    return clips,receipt

def main():
    runpy.run_path(str(ROOT/'tools/prepare-stratholme-actors.py'))
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era')
    definitions=[
        (scarlet(),126571,{'torso':2,'head':16,'left_arm':10,'left_forearm':10,'left_hand':10,'right_arm':11,'right_forearm':15,'right_hand':19,'left_leg':5,'left_shin':7,'right_leg':6,'right_shin':8}, {'skin':'#a0a57e','shade':'#717c57','metal':'#84847d','white':'#d0c7ad','red':'#a42d28','cloth':'#96342b','pants':'#553e37','eye':'#6acfff'}),
        (balnazzar(),123673,{'torso':3,'head':21,'left_arm':17,'left_forearm':26,'left_hand':33,'right_arm':19,'right_forearm':27,'right_hand':34,'left_leg':6,'left_shin':14,'right_leg':7,'right_shin':15,'left_wing':12,'left_wing_tip':22,'right_wing':13,'right_wing_tip':23}, {'skin':'#786a7e','shade':'#504451','hood':'#622c46','metal':'#3c3544','gold':'#9a7952','eye':'#8deb5e','bone':'#b0a286'}),
        (baron(),124984,{'torso':5,'head':44,'left_arm':43,'left_forearm':56,'left_hand':62,'right_arm':45,'right_forearm':58,'right_hand':64,'horse':1,'horse_neck':6,'horse_head':31,'left_front':8,'left_front_shin':19,'right_front':10,'right_front_shin':21,'left_back':24,'left_back_shin':34,'right_back':25,'right_back_shin':37}, {'skin':'#a3aaa7','shade':'#6b7876','hood':'#463052','metal':'#4e5263','gold':'#b69c68','bone':'#aaa796','hair':'#25272c','eye':'#7bdcff'}),
        (crypt(),123329,{'torso':0,'head':33,'left_arm':31,'left_forearm':34,'right_arm':32,'right_forearm':35,'left_leg0':4,'left_leg0_shin':10,'right_leg0':5,'right_leg0_shin':11,'left_leg1':8,'left_leg1_shin':16,'right_leg1':9,'right_leg1_shin':17,'left_leg2':14,'left_leg2_shin':22,'right_leg2':15,'right_leg2_shin':23}, {'skin':'#686189','shade':'#43405f','hood':'#363552','metal':'#67627d','bone':'#beb38a','eye':'#76cba5'}),
        (raised_skeleton(),125942,None,{'bone':'#b7b09a','metal':'#635d70','rust':'#716377','eye':'#7bdcff'})
    ]
    report={}
    for model,fd,mapping,palette in definitions:
        from polish_map_actors import polish,clips_for
        model=polish(model)
        source=Source(casc,fd);mapping=mapping or biped_mapping(source);bones,boxes=convert(model);clips,receipt=retarget(model,source,mapping)
        clips=clips_for(model,clips)
        if model.kind=='baron_rivendare':
            from polish_map_actors import mounted_source_death
            clips['death']=mounted_source_death(model,source,mapping)
            receipt['death']['workflow']='Original mounted WoW Death sequence; world bone rotations and rider displacement retargeted, cuboid contact grounded'
        path=OUT/'zombierool/gameplay/entities'/(model.kind+'.json');profile=json.loads(path.read_text(encoding='utf-8'))
        profile.update(bones=bones,boxes=boxes,animations=clips,texture='stratholme_'+model.kind,textureWidth=256,textureHeight=256,scale=model.scale,sourceMotion=True)
        if model.kind=='balnazzar':profile.update(width=.85,height=2.25)
        if model.kind=='crypt_fiend':profile.update(width=1.5,height=2.5)
        profile.update(knockbackResistance=1 if model.kind in ['baron_rivendare','balnazzar','crypt_fiend'] else .2,meleeReach=1.4 if model.kind=='crypt_fiend' else 1.1 if model.kind in ['baron_rivendare','balnazzar'] else .8)
        profile.update(eyeR=.4 if model.kind=='balnazzar' else .3,eyeG=.9 if model.kind=='balnazzar' else .75,eyeB=.3 if model.kind=='balnazzar' else 1)
        path.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
        image=art.atlas(model.kind if model.kind=='balnazzar' else 'scourge_'+model.kind,palette);dest=OUT/'zombierool/assets/textures/map_actor'/('stratholme_'+model.kind+'.png');dest.parent.mkdir(parents=True,exist_ok=True);image.save(dest)
        model.save()
        report[model.kind]={'workflow':'ZombieRool build_blocky_enemies.Model + pixel atlas; source bone tracks retargeted to authored cubes','sourceModelFileDataID':fd,'sourceModelSha256':source.hash,'parts':len(model.parts),'cuboids':len(boxes),'clips':receipt,'textureSha256':hashlib.sha256(dest.read_bytes()).hexdigest()}
        print(model.kind,len(model.parts),'bones',len(boxes),'authored cubes',path.stat().st_size,'JSON bytes',flush=True)
    path=ROOT/'assets-source/26.7/map-actors';path.mkdir(parents=True,exist_ok=True);(path/'stratholme-provenance.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':main()
