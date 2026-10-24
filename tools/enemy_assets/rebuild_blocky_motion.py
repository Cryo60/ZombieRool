"""Read selected original bone tracks, retaining root motion and joint twist.
Only authored cuboids are rendered. No source meshes/textures are exported.
Temporary CoD reference inputs live in one removable C: folder.
"""
from pathlib import Path
import os,sys,json,struct,importlib.util,math,subprocess
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(os.environ['LOCALAPPDATA'])/'Temp/ZombieRool-motion-repair'
sys.path.insert(0,str(ROOT/'tools/vendor'))
from PyCoD import Model
from local_casc import LocalCasc
spec=importlib.util.spec_from_file_location('bone_reader',ROOT/'tools/import-boss-models.py')
reader=importlib.util.module_from_spec(spec);spec.loader.exec_module(reader)
B=np.array([[0,1,0],[0,0,-1],[-1,0,0.]])
JOINTS=['torso','head','left_arm','right_arm','left_forearm','right_forearm','left_leg','right_leg','left_shin','right_shin']

def frame_y(v,reference=np.array([1.,0,0])):
    y=v/max(np.linalg.norm(v),1e-9);x=reference-y*np.dot(reference,y)
    if np.linalg.norm(x)<.01:x=np.array([0.,0,1])-y*y[2]
    x/=max(np.linalg.norm(x),1e-9);return np.column_stack((x,y,np.cross(x,y)))

def quat(r):
    # Stable matrix -> xyzw, including turns past 180 degrees.
    k=np.array([[r[0,0]-r[1,1]-r[2,2],r[1,0]+r[0,1],r[2,0]+r[0,2],r[2,1]-r[1,2]],
                [r[1,0]+r[0,1],r[1,1]-r[0,0]-r[2,2],r[2,1]+r[1,2],r[0,2]-r[2,0]],
                [r[2,0]+r[0,2],r[2,1]+r[1,2],r[2,2]-r[0,0]-r[1,1],r[1,0]-r[0,1]],
                [r[2,1]-r[1,2],r[0,2]-r[2,0],r[1,0]-r[0,1],r.trace()]])/3
    values,vectors=np.linalg.eigh(k);q=vectors[:,values.argmax()]
    return q if q[3]>=0 else -q

def ancestor(a,b,parents):
    chain=set()
    while a>=0:chain.add(a);a=parents[a]
    while b not in chain and b>=0:b=parents[b]
    return max(0,b)

def cod(kind,zone,model,clips):
    folder=WORK/zone;path=folder/'model_export'/(model+'_lod1.xmodel_export')
    if not path.exists():path=path.with_name(model+'_lod0.xmodel_export')
    source=Model.FromFile_Raw(str(path));names=[b.name for b in source.bones];parents=[b.parent for b in source.bones]
    bind=[]
    for b in source.bones:
        m=np.eye(4);m[:3,:3]=np.array(b.matrix).T;m[:3,3]=b.offset;bind.append(m)
    bind=np.array(bind);local=np.array([m if p<0 else np.linalg.inv(bind[p])@m for m,p in zip(bind,parents)])
    pairs=[tuple(names.index(n) for n in pair) for pair in [('j_neck','j_spinelower'),('j_neck','j_head'),('j_shoulder_le','j_elbow_le'),('j_shoulder_ri','j_elbow_ri'),('j_elbow_le','j_wrist_le'),('j_elbow_ri','j_wrist_ri'),('j_hip_le','j_knee_le'),('j_hip_ri','j_knee_ri'),('j_knee_le','j_ankle_le'),('j_knee_ri','j_ankle_ri')]]
    tracks={}
    for name,(area,file) in clips.items():
        fps,frames=reader.compiled_anim(WORK/area/'xanim'/file,names,parents,local);tracks[name]=(fps,frames)
    points=np.array([v.offset for mesh in source.meshes for v in mesh.verts])
    return np.array([b.offset for b in source.bones]),bind,parents,pairs,points,tracks

def wow(fd,casc):
    raw=casc.read(fd);size=struct.unpack_from('<I',raw,4)[0];md=raw[8:8+size]
    nb,ob=struct.unpack_from('<II',md,44);nv,ov=struct.unpack_from('<II',md,60)
    pivots=np.array([struct.unpack_from('<3f',md,ob+i*88+76) for i in range(nb)])
    parents=[struct.unpack_from('<h',md,ob+i*88+8)[0] for i in range(nb)]
    keys={struct.unpack_from('<i',md,ob+i*88)[0]:i for i in range(nb) if struct.unpack_from('<i',md,ob+i*88)[0]>=0}
    def child(i):return next((j for j,p in enumerate(parents) if p==i and np.linalg.norm(pivots[j]-pivots[i])>.01),i)
    arms=[keys[0],keys[1]];hands=[keys.get(65),keys.get(64)];elbows=[keys.get(81),keys.get(80)]
    for i in range(2):
        elbows[i]=elbows[i] if elbows[i] is not None else parents[hands[i]] if hands[i] is not None else child(arms[i]);hands[i]=hands[i] if hands[i] is not None else child(elbows[i])
    feet=[keys[59],keys[60]];knees=[parents[i] for i in feet];hips=[parents[i] for i in knees]
    pairs=[(keys[6],keys.get(4,keys.get(5,1))),(parents[keys[6]],keys[6])]+list(zip(arms,elbows))+list(zip(elbows,hands))+list(zip(hips,knees))+list(zip(knees,feet))
    na,oa=struct.unpack_from('<II',md,28);animations=[struct.unpack_from('<HHIfI',md,oa+i*64) for i in range(na)]
    def track(bone,which,animation,time,default):
        mode,global_seq,nt,ot,nv,ov=struct.unpack_from('<HH4I',md,ob+bone*88+16+which*20)
        if global_seq!=65535:animation=0
        if animation>=min(nt,nv):return np.array(default,dtype=float)
        n,ts=struct.unpack_from('<II',md,ot+animation*8);vc,vo=struct.unpack_from('<II',md,ov+animation*8);n=min(n,vc)
        if not n:return np.array(default,dtype=float)
        times=np.frombuffer(md,'<u4',n,ts);values=np.frombuffer(md,'<u2' if which==1 else '<f4',n*(4 if which==1 else 3),vo).reshape(n,-1).astype(float)
        if which==1:values=(values-32767)/32768
        a=max(0,min(n-1,int(np.searchsorted(times,time,side='right')-1)));b=min(a+1,n-1)
        mix=0 if a==b or mode==0 else (time-float(times[a]))/max(1,float(times[b])-float(times[a]));v=values[b]
        if which==1 and values[a]@v<0:v=-v
        result=values[a]*(1-mix)+v*mix
        return result/max(np.linalg.norm(result),1e-9) if which==1 else result
    tracks={}
    for name,ids in {'idle':[0],'walk':[4],'run':[5,4],'spawn':[153,0],'melee':[16,17],'death':[1],'hook':[17,16],'vomit':[54,53,16],'crouch':[37,0],'leap':[39,38,5]}.items():
        index=next(i for id in ids for i,a in enumerate(animations) if a[0]==id);visited=set()
        while animations[index][4]&0x40:
            assert index not in visited;visited.add(index);index=struct.unpack_from('<H',md,oa+index*64+62)[0]
        assert animations[index][4]&0x20
        duration=animations[index][2];count=max(2,round(duration/50));frames=[]
        for time in np.linspace(0,duration,count):
            worlds=[None]*nb
            def world(i):
                if worlds[i] is None:
                    m=reader.transform(pivots[i]+track(i,0,index,time,[0,0,0]),track(i,1,index,time,[0,0,0,1]));m[:3,:3]@=np.diag(track(i,2,index,time,[1,1,1]));back=np.eye(4);back[:3,3]=-pivots[i];m=m@back
                    worlds[i]=world(parents[i])@m if parents[i]>=0 else m
                return worlds[i]
            frames.append(np.array([world(i) for i in range(nb)]))
        tracks[name]=(20,np.array(frames))
    bind=np.repeat(np.eye(4)[None],nb,axis=0)
    return pivots,bind,parents,pairs,np.array([struct.unpack_from('<3f',md,ov+i*48) for i in range(nv)]),tracks

def euler(r):
    x,y,z=r;cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

def two_bone(start,end,first,second,pole):
    delta=end-start;distance=np.clip(np.linalg.norm(delta),.01,first+second-.01);direction=delta/max(np.linalg.norm(delta),1e-8)
    bend=pole-direction*np.dot(pole,direction);bend/=max(np.linalg.norm(bend),1e-8)
    along=(first*first-second*second+distance*distance)/(2*distance)
    elbow=start+direction*along+bend*math.sqrt(max(0,first*first-along*along))
    upper=frame_y(elbow-start);lower=frame_y(end-elbow)
    return upper,upper.T@lower

def blend_rotation(a,b,t):
    # Polar interpolation keeps the joint rigid, including near antipodal poses.
    u,_,vt=np.linalg.svd(a*(1-t)+b*t);r=u@vt
    if np.linalg.det(r)<0:u[:,-1]*=-1;r=u@vt
    return r

def export(kind,data):
    pivots,bind,parents,pairs,points,tracks=data;path=ROOT/'src/main/resources/assets/zombierool/models/entities/blocky'/(kind+'.json');model=json.loads(path.read_text(encoding='utf-8'));parts=model['parts'];by={p['name']:p for p in parts}
    authored_hip=(np.array(by['left_leg']['pivot'])+by['right_leg']['pivot'])/2
    default_world={}
    for p in parts:default_world[p['name']]=np.array(p['pivot'])+default_world.get(p.get('parent'),np.zeros(3))
    ys=[default_world[p['name']][1]+c['box'][1]+h for p in parts for c in p['cubes'] for h in [0,c['box'][4]]]
    scale=(max(ys)-min(ys))/(points[:,2].max()-points[:,2].min());source_hip=(pivots[pairs[6][0]]+pivots[pairs[7][0]])/2
    pelvis=ancestor(pairs[6][0],pairs[7][0],parents);inverse=np.linalg.inv(bind)
    rest=[frame_y(B@(pivots[b]-pivots[a])*( -1 if j==1 else 1)) for j,(a,b) in enumerate(pairs)]
    result={};neutral=None
    for name,(fps,frames) in tracks.items():
        poses=[];initial_floor=None
        contacts=None;death_axis=None
        if kind=='scourge_ghoul' and name=='run':
            contacts=[]
            for source in frames:
                points_world=np.einsum('bij,bj->bi',source,np.c_[pivots,np.ones(len(pivots))])[:,:3]
                hips=(points_world[pairs[6][0]]+points_world[pairs[7][0]])/2
                contacts.append([B@(points_world[pairs[i][1]]-hips) for i in [8,9]])
            contacts=np.array(contacts)
            contact_floor=contacts[:,:,1].max(axis=0)
            contact_middle=(contacts[:,:,2].max(axis=0)+contacts[:,:,2].min(axis=0))/2
            contact_scale=6.4/max(np.ptp(contacts[:,:,2],axis=0))
        if kind=='scourge_ghoul' and name=='death':
            last=np.einsum('bij,bj->bi',frames[-1],np.c_[pivots,np.ones(len(pivots))])[:,:3]
            death_axis=B@(last[pairs[0][1]]-last[pairs[0][0]]);death_axis[1]=0
            death_axis/=max(np.linalg.norm(death_axis),1e-8)
        for frame_index,frame in enumerate(frames):
            if np.allclose(bind,np.eye(4)):ps=np.einsum('bij,bj->bi',frame,np.c_[pivots,np.ones(len(pivots))])[:,:3]
            else:ps=frame[:,:3,3]
            delta=frame@inverse;world=[]
            for j,(a,b) in enumerate(pairs):
                bone=b if j==1 else a;ref=B@delta[bone,:3,:3]@B.T@rest[j][:,0]
                v=B@(ps[b]-ps[a])*(-1 if j==1 else 1)
                if j>=2:
                    # Preserve the actual source bone orientation. Rebuilding
                    # twist from a projected pole can flip 180 degrees when an
                    # arm aligns with that pole (especially the WoW zombie).
                    raw=B@delta[a,:3,:3]@B.T@rest[j];u,_,vt=np.linalg.svd(raw);world.append(u@vt)
                else:world.append(frame_y(v,ref))
            # Head rotation includes its own turn/nod, not a zeroed neck channel.
            world[1]=B@delta[pairs[1][1],:3,:3]@B.T@rest[1]
            rp=B@delta[pelvis,:3,:3]@B.T
            local=[world[0],world[0].T@world[1],world[0].T@world[2],world[0].T@world[3],world[2].T@world[4],world[3].T@world[5],world[6],world[7],world[6].T@world[8],world[7].T@world[9]]
            if kind=='scourge_ghoul' and name!='death':
                # Keep its original movement deltas around a less folded rest
                # pose, including the neck and knees rather than torso alone.
                if neutral is None:neutral=[r.copy() for r in local]
                targets=[euler((.35,0,0)),euler((-.22,0,0)),neutral[2],neutral[3],neutral[4],neutral[5],euler((-.22,-.08,.08)),euler((-.22,.08,-.08)),euler((.65,0,0)),euler((.65,0,0))]
                local=[r@n.T@t for r,n,t in zip(local,neutral,targets)];world[0]=local[0]
            if kind=='geist' and name not in ['death','idle','walk','run']:
                # Calibrate the bind stance to the long-legged cuboid rig.
                # Keep every original animated delta, including death rolls;
                # the source's deeply folded knee rest pose is not a rig axis.
                if neutral is None:neutral=[r.copy() for r in local]
                targets=[euler((.75,0,0)),euler((-.65,0,0)),euler((-.45,0,.12)),euler((-.45,0,-.12)),euler((-.4,0,0)),euler((-.4,0,0)),euler((-.55,-.1,.08)),euler((-.35,.1,-.08)),euler((.95,0,0)),euler((.8,0,0))]
                local=[r@n.T@t for r,n,t in zip(local,neutral,targets)];world[0]=local[0]
            hip=(ps[pairs[6][0]]+ps[pairs[7][0]])/2;shift=B@(hip-source_hip)*scale
            if name in ['idle','walk','run','spawn']:shift[[0,2]]=0
            if kind=='scourge_ghoul' and name in ['idle','walk','run']:
                # Retarget the feet onto the authored 6+6 limbs, rather than
                # applying the source's folded thigh bind axes to straight cubes.
                phase=frame_index/max(1,len(frames)-1)*math.pi*2
                running=name=='run'
                shift=np.array([0.,.8+(.4*math.cos(phase*2) if running else 0),0.]);root=authored_hip+shift
                for side_index,side in enumerate(['left','right']):
                    s=1 if side=='left' else -1;p=phase+side_index*math.pi
                    # A planted backwards sweep, then a bent-knee recovery.
                    # The old sine lifted the foot in the wrong half of travel.
                    cycle=(p/(math.pi*2))%1
                    if name=='idle':lift=travel=0
                    elif running:
                        # Retain the asymmetric WoW run foot trajectories; IK
                        # adapts their endpoints to the cuboid knees and shins.
                        lift=min(3.8,(contact_floor[side_index]-contacts[frame_index,side_index,1])*contact_scale)
                        travel=(contacts[frame_index,side_index,2]-contact_middle[side_index])*contact_scale
                    elif cycle<.6:lift=0;travel=(-1+2*cycle/.6)*(3.2 if running else 1.8)
                    else:
                        recovery=(cycle-.6)/.4
                        lift=math.sin(recovery*math.pi)*(3.1 if running else 1.5)
                        travel=(1-2*recovery)*(3.2 if running else 1.8)
                    start=root+np.array(by[side+'_leg']['pivot'])-authored_hip
                    upper,lower=two_bone(start,np.array([s*2.6,24-lift,travel]),6,6,np.array([s*.10,0,-1.]))
                    local[6+side_index]=upper;local[8+side_index]=lower
                rp=np.eye(3)
            if kind=='geist' and name in ['idle','walk','run','crouch']:
                # A low, four-point stance. Use each original foot's forward
                # travel to drive the corresponding planted/swinging limb.
                preparation=frame_index/max(1,len(frames)-1) if name=='crouch' else 0
                preparation=preparation*preparation*(3-2*preparation)
                running=name=='run'
                phase=frame_index/max(1,len(frames)-1)*math.pi*2
                torso=euler((1.04+.13*preparation+(.05*math.sin(phase*2) if running else 0)+float(local[0][0,1])*.06,0,0));local[0]=torso;world[0]=torso
                shift=np.array([0.,20.5+(.5*math.cos(phase*2) if running else 0)+1.5*preparation-authored_hip[1],1.2*preparation]);root=authored_hip+shift
                shoulder_root=root+torso@(np.array(by['torso']['pivot'])-authored_hip)
                local[1]=torso.T@euler((.10,0,0))
                # Opposite hand and hind foot move together. Keep paired
                # supports grounded, rather than lifting both feet at once.
                phase=frame_index/max(1,len(frames)-1)*math.pi*2
                for side_index,side in enumerate(['left','right']):
                    s=1 if side=='left' else -1;wave=math.sin(phase+side_index*math.pi) if name in ['walk','run'] else 0
                    lift=max(0,wave)*(3.0 if running else 1.4);travel=math.cos(phase+side_index*math.pi)*(3.8 if running else 1.8) if name in ['walk','run'] else 0
                    leg_pos=np.array(by[side+'_leg']['pivot']);start=root+(leg_pos-authored_hip)
                    ankle=np.array([s*3.3,22-lift,8+travel]);upper,lower=two_bone(start,ankle,10,10,np.array([s*.45,0,-1.]))
                    local[6+side_index]=upper;local[8+side_index]=lower
                    start=shoulder_root+torso@np.array(by[side+'_arm']['pivot'])
                    hand=np.array([s*5.8,24-max(0,-wave)*(2.2 if running else 1.1),-11-travel]);upper,lower=two_bone(start,hand,5,6,np.array([s*.8,-.2,.5]))
                    local[2+side_index]=torso.T@upper;local[4+side_index]=lower
                rp=np.eye(3)
            upper_offset=np.zeros(3);lower_offset=np.zeros(3)
            if kind in ['scourge_ghoul','scourge_skeleton'] and name=='death':
                t=frame_index/max(1,len(frames)-1);split=np.clip((t-.28)/.48,0,1);split=split*split*(3-2*split)
                # The waist opens along the fall direction. Both thighs remain
                # one lower half instead of twisting independently in mid-air.
                # Keep the split close to the death location. The source root
                # travels much farther than a Minecraft-sized dismemberment.
                travel=np.linalg.norm(shift[[0,2]])
                if travel>4:shift[[0,2]]*=4/travel
                upper_offset=np.array([0.,0,-2])*split;lower_offset=np.array([0.,0,2])*split
                settle=np.clip((t-.48)/.38,0,1);settle=settle*settle*(3-2*settle)
                if death_axis is not None:
                    upper_offset=-death_axis*2*split;lower_offset=death_axis*2*split
                for i in [6,7]:local[i]=blend_rotation(local[i],frame_y(death_axis) if death_axis is not None else euler((math.pi/2,0,.06 if i==6 else -.06)),settle)
                for i in [8,9]:local[i]=blend_rotation(local[i],euler((.08,0,0)),settle)
                rp=blend_rotation(rp,np.eye(3),settle)
            if kind=='scourge_zombie' and name=='death':
                t=frame_index/max(1,len(frames)-1);settle=np.clip((t-.25)/.4,0,1);settle=settle*settle*(3-2*settle)
                # Bring the arms down beside the fallen body; the source hand
                # twist belongs to a different shoulder rig and crossed the back.
                for i in [2,3]:local[i]=blend_rotation(local[i],euler((.12,0,.18 if i==2 else -.18)),settle)
                for i in [4,5]:local[i]=blend_rotation(local[i],euler((.22,0,0)),settle)
            rotations=dict(zip(JOINTS,local));positions={}
            for p in parts:
                n=p['name'];parent=p.get('parent');pos=np.array(p['pivot'],dtype=float)
                if n=='torso':pos=authored_hip+shift+world[0]@(pos-authored_hip)+upper_offset
                elif n in ['left_leg','right_leg']:pos=authored_hip+shift+rp@(pos-authored_hip)+lower_offset
                r=rotations.get(n,euler(p['rotation']))
                if parent:pr,pp=positions[parent];positions[n]=(pr@r,pp+pr@pos)
                else:positions[n]=(r,pos)
            bottom=-1e9;upper_bottom=-1e9;lower_bottom=-1e9;upper_back=-1e9;lower_front=1e9;torso_bottom=-1e9;upper_axis=-1e9;lower_axis=1e9
            for p in parts:
                # A carried projector may enter the floor during Romero's
                # original collapse; it must not hoist his whole corpse.
                if kind=='george_romero' and (p['name'].startswith('projector') or p.get('parent','').startswith('projector')):continue
                r,pos=positions[p['name']]
                parent=p['name']
                while by[parent].get('parent'):parent=by[parent]['parent']
                for cube in p['cubes']:
                    x,y,z,w,h,d=cube['box']
                    for a in [x,x+w]:
                        for b in [y,y+h]:
                            for c in [z,z+d]:
                                corner=pos+r@np.array([a,b,c]);corner_y=corner[1];bottom=max(bottom,corner_y)
                                if p['name']=='torso':upper_back=max(upper_back,corner[2]);torso_bottom=max(torso_bottom,corner_y)
                                if death_axis is not None:
                                    if p['name'] in ['torso','head']:upper_axis=max(upper_axis,np.dot(corner,death_axis))
                                    if parent!='torso':lower_axis=min(lower_axis,np.dot(corner,death_axis))
                                if parent!='torso':lower_front=min(lower_front,corner[2])
                                if parent=='torso':upper_bottom=max(upper_bottom,corner_y)
                                else:lower_bottom=max(lower_bottom,corner_y)
            correction=24-bottom
            if kind=='scourge_zombie' and name=='death':
                # Seat the body itself, including when a blood/gore limb is
                # absent. A projecting hand must not suspend the whole corpse.
                body_bottom=max((pos+r@np.array([x,y,z]))[1] for p in parts if p['name']=='torso' for r,pos in [positions[p['name']]] for c in p['cubes'] for x in [c['box'][0],c['box'][0]+c['box'][3]] for y in [c['box'][1],c['box'][1]+c['box'][4]] for z in [c['box'][2],c['box'][2]+c['box'][5]])
                correction=(1-settle)*correction+settle*(24-body_bottom)
            if kind=='george_romero' and name=='death':
                # His source death sinks away. Re-grounding every frame erased
                # the vertical root track and made the collapse hover in place.
                if initial_floor is None:initial_floor=correction
                correction=initial_floor
            shift[1]+=correction
            if kind in ['scourge_ghoul','scourge_skeleton'] and name=='death':
                upper_offset[1]+=(bottom-torso_bottom)*split;lower_offset[1]+=(bottom-lower_bottom)*split
                # Guarantee an actual opening at the waist, not merely a
                # distance between anchors hidden inside overlapping cubes.
                if death_axis is not None:
                    lower_offset+=death_axis*(upper_axis+1.5-lower_axis)*split
                    lower_offset+=np.cross(death_axis,np.array([0.,1,0]))*2*split
                else:lower_offset[2]+=(upper_back+1.5-lower_front)*split
            poses.append(np.r_[np.concatenate([quat(r) for r in local]),shift,quat(rp),upper_offset,lower_offset].round(6).tolist())
        result[name]={'fps':fps,'frames':poses}
    dest=ROOT/'src/main/resources/assets/zombierool/animations/blocky'/(kind+'.json');dest.write_text(json.dumps({'format':2,'sourceMotion':True,'hip':authored_hip.tolist(),'clips':result},separators=(',',':'))+'\n',encoding='utf-8');print('Source motion restored:',kind,flush=True)

def main():
    if '--extract-cod' in sys.argv:
        WORK.mkdir(parents=True,exist_ok=True)
        for zone in ['zombie_coast','zombie_moon','zombie_pentagon','common_zombie']:
            (WORK/zone).mkdir(exist_ok=True)
            for suffix in ['', '_patch']:
                source=Path('D:/Jeux/pluto_t5_full_game/zone/Common')/(zone+suffix+'.ff')
                with (WORK/(zone+suffix+'.log')).open('w') as log:
                    subprocess.run([str(ROOT/'build/boss-assets/oat/Unlinker.exe'),'--include-assets','xanim' if zone=='common_zombie' else 'xmodel,xanim','--model-format','XMODEL_EXPORT','-o',str(WORK/zone),str(source)],stdout=log,stderr=subprocess.STDOUT,check=True)
    george={k:('zombie_coast','ai_zombie_boss_'+v+'_coast') for k,v in {'idle':'idle_a','walk':'walk_a','run':'sprint_a','melee':'attack_swing_swipe','slam':'enrage_start_slamground','roar':'enrage_start_scream','rage':'enrage_start','death':'death'}.items()};george['spawn']=('zombie_coast','ai_zombie_boss_emerge_from_water');george['calm']=('zombie_coast','ai_zombie_boss_return_to_water')
    export('george_romero',cod('george_romero','zombie_coast','c_zom_george_romero_light_fb',george))
    astro={k:('zombie_moon',v) for k,v in {'walk':'ai_zombie_astro_walk_moon_v1','run':'ai_zombie_astro_walk_moon_v1','melee':'ai_zombie_astro_headbutt','headbutt':'ai_zombie_astro_headbutt'}.items()}
    astro.update({k:('common_zombie',v) for k,v in {'idle':'ai_zombie_idle_base','spawn':'ai_zombie_idle_base','death':'ai_zombie_death_v1'}.items()});export('astronaut',cod('astronaut','zombie_moon','c_zom_moon_pressure_suit_body_zombie',astro))
    thief={k:('common_zombie',v) for k,v in {'idle':'ai_zombie_idle_base','spawn':'ai_zombie_idle_base','melee':'ai_zombie_attack_v1','death':'ai_zombie_death_v1'}.items()};thief.update({'walk':('zombie_pentagon','ai_zombie_electrician_walk'),'run':('zombie_pentagon','ai_zombie_electrician_run')});export('pentagon_thief',cod('pentagon_thief','zombie_pentagon','c_zom_electrician_body',thief))
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era')
    for kind,fd in [('abomination',123961),('scourge_ghoul',124160),('scourge_zombie',126571),('scourge_skeleton',125942)]:export(kind,wow(fd,casc))
    export('geist',wow(125234,LocalCasc('C:/Program Files (x86)/World of Warcraft')))
if __name__=='__main__':main()
