"""Retarget locally extracted T6 animations onto our original Minecraft cuboids.

Only joint directions/timing are retained; no Call of Duty mesh or texture is used.
Unlinker inputs are zm_tomb.ff and zm_prison.ff from the user's T6 installation.
"""
from pathlib import Path
import importlib.util, json, math, shutil, hashlib
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('boss_import',ROOT/'tools/import-boss-models.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
np=m.np
C=np.array([[0,1,0],[0,0,-1],[-1,0,0]],dtype=float)
parts=['torso','head','left_arm','right_arm','left_forearm','right_forearm','left_leg','right_leg','left_shin','right_shin']
def direction(a,b,pose,names):
    v=C@(pose[names.index(b),:3,3]-pose[names.index(a),:3,3]);return v/max(1e-8,np.linalg.norm(v))
def rotation(v):
    x=math.atan2(v[2],math.hypot(v[0],v[1]));z=math.atan2(-v[0],v[1])
    cx,sx,cz,sz=math.cos(x),math.sin(x),math.cos(z),math.sin(z)
    return [x,0,z],np.array([[cz,-sz*cx,sz*sx],[sz,cz*cx,-cz*sx],[0,sx,cx]])
def angles(R):
    y=math.asin(max(-1,min(1,-R[2,0])))
    return [math.atan2(R[2,1],R[2,2]),y,math.atan2(R[1,0],R[0,0])]
def retarget(pose,names,bind):
    torso,tr=rotation(-direction('j_spinelower','j_neck',pose,names))
    headIndex=names.index('j_head');hr=C@(pose[headIndex,:3,:3]@bind[headIndex,:3,:3].T)@C.T
    out=[torso,angles(tr.T@hr)]
    rotations={}
    for side in ['le','ri']:
        a,r=rotation(tr.T@direction('j_shoulder_'+side,'j_elbow_'+side,pose,names));out.append(a);rotations[side]=tr@r
    for side in ['le','ri']:out.append(rotation(rotations[side].T@direction('j_elbow_'+side,'j_wrist_'+side,pose,names))[0])
    for side in ['le','ri']:
        a,r=rotation(direction('j_hip_'+side,'j_knee_'+side,pose,names));out.append(a);rotations[side]=r
    for side in ['le','ri']:out.append(rotation(rotations[side].T@direction('j_knee_'+side,'j_ankle_'+side,pose,names))[0])
    # Minecraft hinge joints do not have the original rig's limb twist controls.
    for i,limit in [(2,.5),(3,.5),(4,.35),(5,.35),(6,.4),(7,.4),(8,.16),(9,.16)]:out[i][2]=max(-limit,min(limit,out[i][2]))
    return [round(float(v),5) for p in out for v in p]

manifest={'source':'Local T6 zm_tomb.ff / zm_prison.ff, Unlinker xanim version 19','parts':parts,'bosses':{}}
for kind,zone,prefix in [('panzer','zm_tomb','ai_zombie_mech_'),('brutus','zm_prison','ai_zombie_cellbreaker_')]:
    if kind=='panzer':names,parents,local,bind,_=m.compiled_model(ROOT/'build/boss-assets/panzer-raw/raw','c_zom_mech_body')
    else:
        model=m.Model.FromFile_Bin(str(ROOT/'build/boss-assets/brutus-raw/cellbreaker/model_export/Custom/AI/brutus/c_zom_cellbreaker_fb_LOD0.XMODEL_BIN'))
        names=[b.name for b in model.bones];parents=[b.parent for b in model.bones];bind=[]
        for bone in model.bones:
            matrix=np.eye(4);matrix[:3,:3]=np.array(bone.matrix).T;matrix[:3,3]=bone.offset;bind.append(matrix)
        bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)])
    clips={};info={}
    selections=({'idle':'idle','run':'run','spawn':'intro_jump_in','melee':'melee_a','flame':'ft_aim_idle','grab':'grapple_aim_5','stagger':'powercore_pain','death':'death'} if kind=='panzer' else {'idle':'idle_a','run':'run_a','spawn':'spawn','melee':'attack_swingleft','slam':'boardsmash_a','stagger':'boardsmash_a','death':'death_mg'})
    for key,name in selections.items():
        source=ROOT/'build/boss-assets/t6-anims'/zone/'xanim'/(prefix+name)
        fps,frames,notes=m.compiled_anim(source,names,parents,local,True)
        poses=[retarget(p,names,bind) for p in frames]
        info[key]={'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'frames':len(frames),'fps':fps,'notes':notes}
        if kind=='panzer' and key=='spawn':
            clips['spawn']={'fps':fps,'frames':poses[80:135]};clips['land']={'fps':fps,'frames':poses[135:]}
        else:clips[key]={'fps':fps,'frames':poses}
    data={'parts':parts,'clips':clips}
    target=ROOT/'src/main/resources/assets/zombierool/animations'/f'{kind}_minecraft_t6.json';target.parent.mkdir(exist_ok=True)
    target.write_text(json.dumps(data,separators=(',',':'))+'\n')
    for folder in ['resources/assets/zombierool/animations','assets/zombierool/animations']:
        dest=ROOT/folder;dest.mkdir(exist_ok=True);shutil.copy2(target,dest/target.name)
    manifest['bosses'][kind]=info
(ROOT/'assets-source/26.7/t6-boss-animation-retarget.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Retargeted original T6 timing and joint motion for both Minecraft boss models.')
