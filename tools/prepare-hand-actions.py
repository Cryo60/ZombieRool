"""Retarget original hand timing and build native Minecraft knife props."""
from pathlib import Path
import importlib.util,json,math,hashlib
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('importer',ROOT/'tools/import-boss-models.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);np=m.np;Image=m.Image
OUT=ROOT/'src/main/resources/assets/zombierool';report={'animations':[],'models':[]}
C=np.array([[0,1,0],[0,0,-1],[-1,0,0]])
def write(relative,data):
 for folder in ['src/main/resources','resources','','build/resources/main']:
  target=ROOT/folder/'assets/zombierool'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
def json_asset(relative,data):write(relative,(json.dumps(data,indent=2)+'\n').encode())
def rig(game,zone,name):
 root=ROOT/'build/hand-actions-source'/game/zone
 model=m.Model.FromFile_Bin(str(root/'model_export'/f'{name}_lod0.xmodel_bin'))
 names=[b.name for b in model.bones];parents=[b.parent for b in model.bones];bind=[]
 for b in model.bones:
  matrix=np.eye(4);matrix[:3,:3]=np.array(b.matrix).T;matrix[:3,3]=b.offset;bind.append(matrix)
 bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)])
 return root,names,parents,local
def clip(rig,original,name,cooking=False):
 root,names,parents,local=rig;source=root/'xanim'/original
 fps,frames,notes=m.compiled_anim(source,names,parents,local,True);duration=(len(frames)-1)/fps
 bones={}
 for side,bone,x in [('le','left_arm',5),('ri','right_arm',-5)]:
  rotation={};position={}
  for i,frame in enumerate(frames):
   hand=C@frame[names.index('j_wrist_'+side),:3,3]*.6+np.array([0,-6,0]);hand[0]*=.6
   v=C@(frame[names.index('j_wrist_'+side),:3,3]-frame[names.index('j_shoulder_'+side),:3,3]);v=v/max(1e-6,np.linalg.norm(v))
   angles=[math.degrees(math.atan2(v[2],math.copysign(math.hypot(v[0],v[1]),v[1]))),0,math.degrees(math.atan2(-v[0]*math.copysign(1,v[1]),abs(v[1])))];offset=np.zeros(3)
   blend=min(1,i/4) if cooking else 1 if name.endswith('_throw') else min(1,(len(frames)-1-i)/4)
   time=str(round(i/fps,5))
   rotation[time]={'vector':[round(a*blend,4) for a in angles],'lerp_mode':'linear'}
   position[time]={'vector':[round(float(a)*blend,4) for a in offset],'lerp_mode':'linear'}
  if cooking:
   rotation['5']=rotation[time];position['5']=position[time]
  elif name.endswith('_throw'):
   release=max(.25,duration);rotation[str(release)]=rotation[time];position[str(release)]=position[time]
   rotation[str(release+.2)]={'vector':[0,0,0],'lerp_mode':'linear'};position[str(release+.2)]={'vector':[0,0,0],'lerp_mode':'linear'}
  bones[bone]={'rotation':rotation,'position':position}
 report['animations'].append({'animation':name,'source':original,'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'fps':fps,'frames':len(frames),'notes':notes,'sourceDuration':duration,'adaptedDuration':5 if cooking else max(.25,duration)+.2 if name.endswith('_throw') else duration})
 camera_frames=[]
 for frame in frames:
  points=[]
  for side in ['ri','le']:
   for joint in ['shoulder','elbow','wrist']:
    p=frame[names.index('j_'+joint+'_'+side),:3,3]
    points.append([round(float(-p[1]*.0224),6),round(float(p[2]*.032-.08),6),round(float(-p[0]*.032-.4),6)])
  camera_frames.append(points)
 json_asset(f'animations/hands/{name}.json',{'fps':fps,'frames':camera_frames,'duration':5 if cooking else max(.25,duration)+.2 if name.endswith('_throw') else duration})
 return {'animation_length':5 if cooking else max(.25,duration)+.2 if name.endswith('_throw') else duration,'bones':bones}
t5=rig('t5','common_zombie','viewhands_usmc');t4=rig('t4','nazi_zombie_prototype','viewmodel_usa_marine_arms')
for file,clips in [('grenade',{'grenade_cook':clip(t5,'viewmodel_m67_pullpin','grenade_cook',True),'grenade_throw':clip(t5,'viewmodel_m67_throw','grenade_throw')}),('stielhandgranate',{'stielhandgranate_cook':clip(t4,'viewmodel_germangrenade_pullpin','stielhandgranate_cook',True),'stielhandgranate_throw':clip(t4,'viewmodel_germangrenade_throw','stielhandgranate_throw')}),('knife',{'knife_sweep':clip(t5,'viewmodel_m4m203_knife_melee_1','knife_sweep'),'bowie_sweep':clip(t5,'viewmodel_bowie_slash','bowie_sweep')})]:
 for folder in ['player_animation','animations']:json_asset(f'{folder}/{file}.json',{'format_version':'1.8.0','animations':clips})
# Native cuboids with stepped blade profiles and Minecraft materials, based on the references.
def cube(name,a,b,texture):
 return {'name':name,'from':a,'to':b,'faces':{face:{'texture':'#'+texture,'uv':[4,4,12,12]} for face in ['north','south','east','west','up','down']}}
for item,heavy in [('anim_knife',False),('bowie_knife',True)]:
 parts=[cube('grip',[7.15,1,7.35],[8.85,5.6,8.65],'wood'),cube('pommel',[7,0.5,7.2],[9,1.1,8.8],'guard'),cube('crossguard',[5.7 if heavy else 6.1,5.55,7.1],[10.3 if heavy else 9.9,6.25,8.9],'guard')]
 for y in [1.8,4.5]:parts.append(cube('rivet',[7.65,y,7.23],[8.35,y+.5,8.77],'metal'))
 if heavy:
  parts += [cube('knuckle_guard',[10,1,7.55],[10.6,5.65,8.45],'guard'),cube('guard_return',[8.6,.65,7.55],[10.6,1.25,8.45],'guard')]
  blade=[([6.3,6.2,7.65],[9.8,12.8,8.35]),([6.3,12.8,7.65],[9.3,14.2,8.35]),([6.7,14.2,7.65],[9.3,15.2,8.35]),([7.3,15.2,7.65],[9.3,16.1,8.35]),([8.1,16.1,7.65],[9.3,16.8,8.35]),([8.8,16.8,7.65],[9.3,17.3,8.35])]
 else:
  blade=[([6.95,6.2,7.7],[9.05,11.3,8.3]),([7.2,11.3,7.7],[9.05,12.2,8.3]),([7.65,12.2,7.7],[9.05,13.05,8.3]),([8.15,13.05,7.7],[9.05,13.65,8.3]),([8.65,13.65,7.7],[9.05,14.05,8.3])]
 for a,b in blade:parts.append(cube('stepped_blade',a,b,'metal'))
 parts.append(cube('cutting_edge',[6.25 if heavy else 6.9,6.4,7.6],[6.55 if heavy else 7.15,12.6 if heavy else 11.1,8.4],'edge'))
 for z in [7.59,8.35]:parts.append(cube('fuller',[7.5,6.8,z],[7.85,12 if heavy else 10.5,z+.06],'guard'))
 display={'thirdperson_righthand':{'rotation':[0,0,-25],'translation':[0,3,0],'scale':[.8,.8,.8]},'thirdperson_lefthand':{'rotation':[0,0,25],'translation':[0,3,0],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-80,15],'translation':[1,3,0],'scale':[.8,.8,.8]},'gui':{'rotation':[0,0,-40],'translation':[0,-.5,0],'scale':[.9,.9,.9]},'fixed':{'rotation':[0,0,-40],'scale':[.9,.9,.9]},'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]}}
 json_asset(f'models/item/{item}.json',{'parent':'minecraft:block/block','textures':{'metal':'minecraft:block/iron_block','wood':'minecraft:block/stripped_dark_oak_log','guard':'minecraft:block/gray_concrete','edge':'minecraft:block/quartz_block_side','particle':'minecraft:block/iron_block'},'elements':parts,'display':display})
 report['models'].append({'item':item,'style':'native Minecraft cuboids, stepped clip-point blade','elements':len(parts),'reference':'viewmodel_knife_bowie' if heavy else 'viewmodel_knife'})
target=ROOT/'assets-source/26.7/hand-actions/provenance.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
