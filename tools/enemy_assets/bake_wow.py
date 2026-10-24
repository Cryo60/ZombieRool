"""Bake only selected local WoW M2/SKIN/BLP assets into Minecraft skin UVs.
M2/SKIN format reference: wow.export (MIT), src/js/3D/loaders/M2Loader.js.
The raw files live exclusively in the disposable enemy workspace.
"""
from pathlib import Path
import os,sys,json,struct,importlib.util,hashlib
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(os.environ['LOCALAPPDATA'])/'Temp/ZombieRool-enemy-work'
spec=importlib.util.spec_from_file_location('baker',ROOT/'tools/bake-enemy-skins.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)

def chunks(raw):
    out={};p=0
    while p<len(raw):
        name=raw[p:p+4].decode();size=struct.unpack_from('<I',raw,p+4)[0];out[name]=raw[p+8:p+8+size];p+=8+size
    return out

def bake(kind,record):
    folder=WORK/'extracted/wow';md=chunks((folder/str(record['model'])).read_bytes())['MD21'];skin=(folder/str(record['skin'])).read_bytes()
    count,offset=struct.unpack_from('<II',md,60);bone_count,bone_offset=struct.unpack_from('<II',md,44)
    dtype=np.dtype([('position','<f4',3),('weights','u1',4),('bones','u1',4),('normal','<f4',3),('uv','<f4',2),('uv2','<f4',2)])
    vertices=np.frombuffer(md,dtype=dtype,count=count,offset=offset)
    nc,oi=struct.unpack_from('<II',skin,4);nt,ot=struct.unpack_from('<II',skin,12)
    indices=np.frombuffer(skin,dtype='<u2',count=nc,offset=oi);triangles=indices[np.frombuffer(skin,dtype='<u2',count=nt,offset=ot)].reshape(-1,3)
    bones=[(struct.unpack_from('<i',md,bone_offset+i*88)[0],struct.unpack_from('<h',md,bone_offset+i*88+8)[0],np.array(struct.unpack_from('<3f',md,bone_offset+i*88+76))) for i in range(bone_count)]
    waist=next((p for id,parent,p in bones if id==5),np.array([0,0,1.2]))
    def group(index):
        original=index;seen=set()
        while 0<=index<len(bones) and index not in seen:
            seen.add(index);id,parent,p=bones[index]
            if id in (6,7):return 'head'
            if id in (0,1,2,3):return 'arm'
            if id in (4,5):return 'body'
            index=parent
        return 'leg' if bones[original][2][2]<waist[2]-.13 else 'body'
    texture=np.asarray(Image.open(folder/str(record['texture'])).convert('RGBA'));groups={k:[] for k in ['head','body','arm','leg']}
    for tri in triangles:
        vs=vertices[tri];dominant=vs['bones'][np.arange(3),vs['weights'].argmax(axis=1)];tags=[group(int(x)) for x in dominant];tag=max(set(tags),key=tags.count)
        if tag not in ('head','arm'):tag='leg' if vs['position'][:,2].mean()<waist[2]*.9 else 'body'
        pos=vs['position'].copy();pos=pos[:,[1,2,0]]*np.array([1,-1,-1])
        if tag=='arm':
            # Fold the source's outstretched arms down before the skin projection.
            source=vs['position'];side=1 if source[:,1].mean()>=0 else -1
            pos=np.stack([source[:,0],-np.abs(source[:,1]),source[:,2]],axis=1)
        groups[tag].append((pos,vs['uv'],texture))
    print(kind,{k:len(v) for k,v in groups.items()},flush=True)
    canvas=Image.new('RGBA',(256,256));coverage={}
    for key,uv,dim in [('head',(0,0),(8,8,8)),('body',(16,16),(8,12,4)),('arm',(40,16),(4,12,4)),('leg',(0,16),(4,12,4))]:coverage[key]=b.cube(canvas,groups[key],*uv,*dim)
    dest=ROOT/'src/main/resources/assets/zombierool/textures/entities/encounters'/(kind+'.png');dest.parent.mkdir(parents=True,exist_ok=True);canvas.resize((128,128),Image.Resampling.BOX).save(dest)
    return {'kind':kind,**record,'sha256':{str(id):hashlib.sha256((folder/str(id)).read_bytes()).hexdigest() for id in record.values()},'coverage':coverage}

if __name__=='__main__':
    selected=json.loads((WORK/'wow-selected.json').read_text());report=[]
    for kind,record in selected.items():report.append(bake(kind,record));print('Baked',kind,flush=True)
    (ROOT/'assets-source/26.7/enemy-skins/encounters-wow.json').write_text(json.dumps({'source':'local World of Warcraft CASC','assets':report},indent=2)+'\n',encoding='utf-8')
