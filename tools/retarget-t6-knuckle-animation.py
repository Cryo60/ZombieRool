"""Retarget the local T6 crafting/PAP wrist trajectories onto Minecraft skin arms.
No Call of Duty mesh or texture is exported. Requires the existing local extraction.
"""
from pathlib import Path
import importlib.util, json, math, hashlib
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('importer',ROOT/'tools/import-boss-models.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);np=m.np
model=m.Model.FromFile_Bin(str(next((ROOT/'build/zombie-animation-source/model_export').glob('c_zom_dempsey_viewhands*'))))
names=[b.name for b in model.bones];parents=[b.parent for b in model.bones];bind=[]
for bone in model.bones:
 matrix=np.eye(4);matrix[:3,:3]=np.array(bone.matrix).T;matrix[:3,3]=bone.offset;bind.append(matrix)
bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)])
source=ROOT/'build/boss-assets/t6-anims/zm_prison/xanim/viewmodel_zombie_packopunch'
fps,frames,notes=m.compiled_anim(source,names,parents,local,True)
conversion=np.array([[0,1,0],[0,0,-1],[-1,0,0]]);bones={}
for side,name,x in [('le','left_arm',5),('ri','right_arm',-5)]:
 rotation={};position={}
 for i,frame in enumerate(frames):
  hand=conversion@frame[names.index('j_wrist_'+side),:3,3]*.6+np.array([0,-6,0]);hand[0]=hand[0]*.25-math.copysign(.5,x) # Compensate the voxel palms and preserve their centre contact.
  shoulder=np.array([x,2,0]);v=hand-shoulder;v=v/np.linalg.norm(v)
  angles=[math.degrees(math.atan2(v[2],math.copysign(math.hypot(v[0],v[1]),v[1]))),0,math.degrees(math.atan2(-v[0]*math.copysign(1,v[1]),abs(v[1])))];offset=hand-12*v-shoulder;offset[1]*=-1 # Bedrock position Y points up, Minecraft model Y points down.
  blend=min(1,i/5,(len(frames)-1-i)/5);time=str(round(i/(len(frames)-1)*2,5))
  rotation[time]={'vector':[round(a*blend,4) for a in angles],'lerp_mode':'linear'}
  position[time]={'vector':[round(float(a)*blend,4) for a in offset],'lerp_mode':'linear'}
 bones[name]={'rotation':rotation,'position':position}
asset={'format_version':'1.8.0','animations':{'knuckle_crack':{'animation_length':2,'bones':bones}}}
for folder in ['src/main/resources/assets/zombierool','resources/assets/zombierool','assets/zombierool']:
 output=ROOT/folder/'player_animation/knuckle_crack.json';output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(asset,indent=2)+'\n')
manifest=ROOT/'assets-source/26.7/knuckle-animation.json';info=json.loads(manifest.read_text()) if manifest.exists() else {}
info.update(source=str(source),sha256=hashlib.sha256(source.read_bytes()).hexdigest(),original_fps=fps,original_frames=len(frames),notes=notes,adaptation='Original wrist trajectories retargeted to two straight Minecraft skin arms; no COD mesh or texture. Original two-second timing retained, with Minecraft palm contact compensation.')
info['duration_ticks']=40
for name,tick in [('knuckle_0',10),('knuckle_1',27)]:
 if name in info.get('sounds',{}):info['sounds'][name]['animation_tick']=tick
manifest.write_text(json.dumps(info,indent=2)+'\n')
print('Retargeted T6 Pack-a-Punch / zombie_builder animation.')
