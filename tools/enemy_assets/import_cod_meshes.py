"""Retain the original Romero and Moon suit meshes and their own T5 skeleton clips.
XMODEL decoding: vendored MIT PyCoD. Source archive reads are targeted/read-only.
"""
from pathlib import Path
import os,sys,json,zipfile,importlib.util
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(os.environ['LOCALAPPDATA'])/'Temp/ZombieRool-enemy-work'
sys.path.insert(0,str(ROOT/'tools/vendor'))
from PyCoD import Model
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
cod=module('codmesh',ROOT/'tools/import-boss-models.py')
images=module('iwi',ROOT/'tools/extract-original-sky-textures.py')
archives=[];index={}
for path in Path('D:/Jeux/pluto_t5_full_game/main').glob('*.iwd'):
    pack=zipfile.ZipFile(path);archives.append(pack)
    for name in pack.namelist():
        if name.startswith('images/') and name.endswith('.iwi'):index[name]=(pack,name)

def run(kind,zone,models,animations,height):
    raw=WORK/'extracted'/zone;names=[];parents=[];bind=[];meshes=[];textures={}
    for model_name in models:
        available=raw/'model_export'/(model_name+'_lod1.xmodel_export')
        if not available.exists():available=raw/'model_export'/(model_name+'_lod0.xmodel_export')
        m=Model.FromFile_Raw(str(available));mapping={};attachment=np.eye(4)
        attach_index=names.index('tag_weapon_right') if model_name=='t5_weapon_engineer_club' else -1
        if attach_index>=0:attachment=bind[attach_index].copy()
        elif names and m.bones[0].name in names:
            bone=m.bones[0];source=np.eye(4);source[:3,:3]=np.array(bone.matrix).T;source[:3,3]=bone.offset
            attachment=bind[names.index(bone.name)]@np.linalg.inv(source)
        for bi,bone in enumerate(m.bones):
            if bone.name in names:mapping[bi]=names.index(bone.name);continue
            mapping[bi]=len(names);names.append(bone.name);parents.append(mapping.get(bone.parent,attach_index))
            b=np.eye(4);b[:3,:3]=np.array(bone.matrix).T;b[:3,3]=bone.offset;bind.append(attachment@b)
        for material in m.materials:
            if 'eye_cornea' in material.name:continue
            metadata=json.loads((raw/'materials'/(material.name+'.json')).read_text(encoding='utf-8'))
            diffuse=next((t['image'] for t in metadata.get('textures',[]) if t.get('semantic')=='colorMap'),None)
            if diffuse is None:continue
            pack,name=index['images/'+diffuse+'.iwi'];image=images.decode_iwi(pack.read(name))
            image.thumbnail((512,512));textures[material.name.replace('/','_')]=image
        grouped={}
        for mesh in m.meshes:
            for face in mesh.faces:
                material=m.materials[face.material_id].name;key=material.replace('/','_')
                if key not in textures or 'eye_cornea' in material:continue
                vs,fs,dedup=grouped.setdefault(key,([],[],{}));ids=[]
                for fv in face.indices:
                    v=mesh.verts[fv.vertex];fingerprint=(fv.vertex,tuple(fv.uv),tuple(fv.normal))
                    if fingerprint not in dedup:
                        dedup[fingerprint]=len(vs)
                        vs.append(((attachment@np.append(v.offset,1))[:3],attachment[:3,:3]@fv.normal,fv.uv,[(mapping[i],w) for i,w in v.weights]))
                    ids.append(dedup[fingerprint])
                fs.append(ids)
        meshes.extend((mat,vs,fs) for mat,(vs,fs,_) in grouped.items())
    bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)])
    clips={};source={}
    for name,(folder,file) in animations.items():
        fps,frames,notes=cod.compiled_anim(WORK/'extracted'/folder/'xanim'/file,names,parents,local,include_notes=True)
        clips[name]=(fps,frames);source[name]={"file":file,"notes":notes}
    report=cod.export(kind,names,parents,bind,meshes,clips,textures,height);report['sourceClips']=source;report['sourceModels']=models
    return report

if __name__=='__main__':
    george={key:('coast-anim','ai_zombie_boss_'+file+'_coast') for key,file in {'idle':'idle_a','walk':'walk_a','run':'sprint_a','spawn':'emerge_from_water','melee':'attack_swing_swipe','slam':'enrage_start_slamground','roar':'enrage_start_scream','rage':'enrage_start','death':'death','calm':'return_to_water'}.items()}
    # These two clips do not carry the map suffix.
    george['spawn']=('coast-anim','ai_zombie_boss_emerge_from_water');george['calm']=('coast-anim','ai_zombie_boss_return_to_water')
    report={'george_romero':run('george_romero','coast',['c_zom_george_romero_light_fb','t5_weapon_engineer_club'],george,2.65)}
    report['george_romero_enraged']=run('george_romero_enraged','coast',['c_zom_george_romero_zombiefied_fb','t5_weapon_engineer_club'],george,2.65)
    astro={key:('moon',file) for key,file in {'idle':'ai_zombie_idle_base','walk':'ai_zombie_astro_walk_moon_v1','run':'ai_zombie_astro_walk_moon_v1','spawn':'ai_zombie_idle_base','melee':'ai_zombie_astro_headbutt','headbutt':'ai_zombie_astro_headbutt','death':'ai_zombie_death_v1'}.items()}
    for key in ['idle','spawn','death']:astro[key]=('common-anim',astro[key][1])
    report['astronaut']=run('astronaut','moon',['c_zom_moon_pressure_suit_body_zombie','c_zom_moon_pressure_suit_helm'],astro,2.35)
    thief={key:('common-anim',file) for key,file in {'idle':'ai_zombie_idle_base','spawn':'ai_zombie_idle_base','melee':'ai_zombie_attack_v1','death':'ai_zombie_death_v1'}.items()}
    thief['walk']=('pentagon','ai_zombie_electrician_walk');thief['run']=('pentagon','ai_zombie_electrician_run')
    report['pentagon_thief']=run('pentagon_thief','pentagon',['c_zom_electrician_body'],thief,1.95)
    (ROOT/'assets-source/26.7/enemy-skins/source-meshes-cod.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
