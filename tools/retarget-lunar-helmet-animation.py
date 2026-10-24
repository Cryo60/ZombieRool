"""Retarget the locally installed T5 gasmask equip/remove wrist trajectories.
Read-only originals, existing source skeleton, no new player mesh.
"""
from pathlib import Path
import importlib.util,json,math,hashlib
ROOT=Path(__file__).resolve().parents[1]
def main():
    spec=importlib.util.spec_from_file_location('boss_importer',ROOT/'tools/import-boss-models.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);np=m.np
    model=m.Model.FromFile_Raw(str(ROOT/'build/lunar-equipment/t5-viewhands.xmodel_export'))
    names=[b.name for b in model.bones];parents=[b.parent for b in model.bones];bind=[]
    for bone in model.bones:
        matrix=np.eye(4);matrix[:3,:3]=np.array(bone.matrix).T;matrix[:3,3]=bone.offset;bind.append(matrix)
    bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)])
    conversion=np.array([[0,1,0],[0,0,-1],[-1,0,0]]);manifest=[]
    for source_name,animation in [('pullout','helmet_equip'),('putaway','helmet_remove')]:
        source=ROOT/'build/lunar-equipment/moon-source/xanim'/('viewmodel_gasmask_'+source_name)
        fps,frames,notes=m.compiled_anim(source,names,parents,local,True);bones={};duration=len(frames)/fps
        for side,name,x in [('le','left_arm',5),('ri','right_arm',-5)]:
            rotation={};position={}
            for i,frame in enumerate(frames):
                hand=conversion@frame[names.index('j_wrist_'+side),:3,3]*.6+np.array([0,-6,0]);hand[0]=hand[0]*.25-math.copysign(.5,x)
                shoulder=np.array([x,2,0]);v=hand-shoulder;v=v/np.linalg.norm(v)
                angles=[math.degrees(math.atan2(v[2],math.copysign(math.hypot(v[0],v[1]),v[1]))),0,math.degrees(math.atan2(-v[0]*math.copysign(1,v[1]),abs(v[1])))];offset=hand-12*v-shoulder;offset[1]*=-1
                blend=min(1,i/4,(len(frames)-1-i)/4);time=str(round(i/(len(frames)-1)*duration,5))
                rotation[time]={'vector':[round(a*blend,4) for a in angles],'lerp_mode':'linear'}
                position[time]={'vector':[round(float(a)*blend,4) for a in offset],'lerp_mode':'linear'}
            bones[name]={'rotation':rotation,'position':position}
        # Camera motion is supplied by the original hand trajectories, no invented head movement.
        camera_frames=[]
        for frame in frames:
            joints=[]
            for side in ['ri','le']:
                for joint in ['shoulder','elbow','wrist']:
                    p=frame[names.index('j_'+joint+'_'+side),:3,3];joints.append([round(float(-p[1]*.04),6),round(float(p[2]*.04),6),round(float(-p[0]*.04),6)])
            camera_frames.append(joints)
        fp=ROOT/'src/main/resources/assets/zombierool/animations/lunar';fp.mkdir(parents=True,exist_ok=True)
        (fp/(animation+'.json')).write_text(json.dumps({'fps':fps,'duration':duration,'frames':camera_frames},separators=(',',':')),encoding='utf-8')
        data={'format_version':'1.8.0','animations':{animation:{'animation_length':duration,'bones':bones}}}
        path=ROOT/'src/main/resources/assets/zombierool/player_animation'/(animation+'.json');path.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
        manifest.append({'animation':animation,'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'fps':fps,'frames':len(frames),'notes':notes,'adaptedDurationTicks':round(duration*20),'adaptation':'Original wrist trajectories fitted to straight Minecraft arms; original T5 duration and T5 viewhands skeleton with entry/exit blending.'})
    target=ROOT/'assets-source/26.7/lunar-equipment/animation-provenance.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Retargeted original T5 gasmask pullout and putaway wrist trajectories')
if __name__=='__main__':main()
