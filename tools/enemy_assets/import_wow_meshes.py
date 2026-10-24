"""Import selected LOCAL Classic M2 meshes, skin batches and original skeletal clips.

Format reference: wow.export M2Loader/M2Generics/Skin (MIT, Kruithne/Marlamin).
Only the named files are read; intermediate files stay in ZombieRool-enemy-work.
The resulting sub-1200-triangle meshes retain the actual creature silhouettes.
"""
from pathlib import Path
import os,sys,struct,json,io,importlib.util
import numpy as np
from PIL import Image
from local_casc import LocalCasc
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(os.environ['LOCALAPPDATA'])/'Temp/ZombieRool-enemy-work'
spec=importlib.util.spec_from_file_location('codmesh',ROOT/'tools/import-boss-models.py')
cod=importlib.util.module_from_spec(spec);spec.loader.exec_module(cod)
DTYPE=np.dtype([('p','<f4',3),('w','u1',4),('b','u1',4),('n','<f4',3),('uv','<f4',2),('uv2','<f4',2)])

def chunks(raw):
    out={};p=0
    while p<len(raw):
        size=struct.unpack_from('<I',raw,p+4)[0];out[raw[p:p+4].decode()]=raw[p+8:p+8+size];p+=8+size
    return out

def run(kind,model,textures,height,casc):
    folder=WORK/'extracted/wow';folder.mkdir(parents=True,exist_ok=True)
    def read(fd):
        path=folder/str(fd)
        if not path.exists():path.write_bytes(casc.read(fd))
        return path.read_bytes()
    ch=chunks(read(model));md=ch['MD21'];skin=read(struct.unpack_from('<I',ch['SFID'])[0])
    nv,ov=struct.unpack_from('<II',md,60);v=np.frombuffer(md,DTYPE,nv,ov)
    nb,ob=struct.unpack_from('<II',md,44)
    bones=[struct.unpack_from('<iIhH',md,ob+i*88) for i in range(nb)]
    parents=[b[2] for b in bones];pivots=[np.array(struct.unpack_from('<3f',md,ob+i*88+76)) for i in range(nb)]
    na,oa=struct.unpack_from('<II',md,28)
    animations=[struct.unpack_from('<HHIfI',md,oa+i*64) for i in range(na)]
    def resolve(index):
        visited=set()
        while animations[index][4]&0x40:
            assert index not in visited;visited.add(index);index=struct.unpack_from('<H',md,oa+index*64+62)[0]
        assert animations[index][4]&0x20, 'External animation needs explicit import'
        return index
    def track(bone,which,animation,time,default):
        offset=ob+bone*88+16+which*20
        interpolation,global_seq,nt,ot,nval,oval=struct.unpack_from('<HH4I',md,offset)
        if global_seq!=65535:animation=0
        if animation>=min(nt,nval):return np.array(default)
        count,ts=struct.unpack_from('<II',md,ot+animation*8);vc,vo=struct.unpack_from('<II',md,oval+animation*8)
        if not count or not vc:return np.array(default)
        count=min(count,vc);times=np.frombuffer(md,dtype='<u4',count=count,offset=ts)
        values=np.frombuffer(md,dtype='<u2' if which==1 else '<f4',count=count*(4 if which==1 else 3),offset=vo).reshape(count,-1).astype(float)
        if which==1:values=(values-32767)/32768
        a=max(0,min(count-1,int(np.searchsorted(times,time,side='right')-1)));b=min(a+1,count-1)
        if b==a or interpolation==0:result=values[a].copy()
        else:
            mix=(time-float(times[a]))/max(1,float(times[b])-float(times[a]));vb=values[b]
            if which==1 and np.dot(values[a],vb)<0:vb=-vb
            result=values[a]*(1-mix)+vb*mix
        if which==1:result/=max(np.linalg.norm(result),1e-8)
        return result
    clips={};source_clips={}
    # WoW animation IDs: Stand, Walk, Run, AttackUnarmed, Death, Birth,
    # SpellCastDirected, AttackOff, JumpStart/Jump (fallback is an original attack).
    for name,ids in {'idle':[0],'walk':[4],'run':[5,4],'spawn':[153,0],'melee':[16,17],
                     'death':[1],'hook':[17,16],'vomit':[54,53,16],'crouch':[37,0],'leap':[39,38,5]}.items():
        index=next((i for id in ids for i,a in enumerate(animations) if a[0]==id),None)
        assert index is not None,(kind,name);index=resolve(index);duration=animations[index][2]
        frames=[];fps=20;count=max(2,round(duration*fps/1000))
        for frame in range(count):
            time=frame*duration/max(1,count-1);worlds=[None]*nb
            def world(i):
                if worlds[i] is not None:return worlds[i]
                t=track(i,0,index,time,[0,0,0]);q=track(i,1,index,time,[0,0,0,1]);scale=track(i,2,index,time,[1,1,1])
                local=cod.transform(pivots[i]+t,q);local[:3,:3]@=np.diag(scale);back=np.eye(4);back[:3,3]=-pivots[i];local=local@back
                worlds[i]=world(parents[i])@local if parents[i]>=0 else local
                return worlds[i]
            frames.append(np.array([world(i) for i in range(nb)]))
        clips[name]=(fps,np.array(frames));source_clips[name]={'id':animations[index][0],'durationMs':duration}
    lookup_count,lookup_offset=struct.unpack_from('<II',skin,4);tri_count,tri_offset=struct.unpack_from('<II',skin,12)
    lookup=np.frombuffer(skin,'<u2',lookup_count,lookup_offset);tri=lookup[np.frombuffer(skin,'<u2',tri_count,tri_offset)]
    nc,oc=struct.unpack_from('<II',md,128);combos=struct.unpack_from('<'+'H'*nc,md,oc)
    ns,osub=struct.unpack_from('<II',skin,28);nbs,obs=struct.unpack_from('<II',skin,36)
    meshes=[];images={};used=set();gore=[]
    def gore_mask(bone):
        ids=[];j=bone
        while j>=0:
            ids.append(bones[j][0]);j=parents[j]
        mask=0
        if 6 in ids:mask|=1
        if 6 in ids and 7 not in ids:mask|=1024
        if any(id in ids for id in [0,2]):mask|=2
        if any(id in ids for id in [1,3]):mask|=4
        if 81 in ids:mask|=2|32
        if 80 in ids:mask|=4|64
        if 65 in ids:mask|=2|32|128
        if 64 in ids:mask|=4|64|256
        if 5 in ids and pivots[bone][2]<pivots[next(i for i,b in enumerate(bones) if b[0]==5)][2]-.05:mask|=8 if pivots[bone][1]>0 else 16
        return mask
    for i in range(nbs):
        batch=struct.unpack_from('<Bb11H',skin,obs+i*24);section=batch[3];combo=batch[10]
        # Layered additive overlays are not duplicate opaque surfaces.
        if section in used:continue
        used.add(section);tex=combos[combo];tex=min(tex,len(textures)-1)
        material='source_'+str(textures[tex]);images[material]=Image.open(io.BytesIO(read(textures[tex]))).convert('RGBA')
        sub=struct.unpack_from('<10H',skin,osub+section*48);start=sub[4]+(sub[1]<<16);indices=tri[start:start+sub[5]].reshape(-1,3)
        ids=sorted(set(map(int,indices.ravel())));remap={old:i for i,old in enumerate(ids)};vertices=[]
        for id in ids:
            vert=v[id];weights=[(int(b),float(w)/255) for b,w in zip(vert['b'],vert['w']) if w]
            if not weights:weights=[(0,1.)]
            vertices.append((vert['p'].copy(),vert['n'].copy(),vert['uv'].copy(),weights))
        local_faces=[[remap[int(id)] for id in face] for face in indices]
        meshes.append((material,vertices,local_faces))
        masks=[gore_mask(max(vert[3],key=lambda pair:pair[1])[0]) for vert in vertices]
        gore.append([masks[face[0]]|masks[face[1]]|masks[face[2]] for face in local_faces])
    manifest=cod.export(kind,['wow_'+str(i) for i in range(nb)],parents,np.array([np.eye(4)]*nb),meshes,clips,images,height)
    (ROOT/'src/main/resources/assets/zombierool/models/entities'/(kind+'.gore.json')).write_text(json.dumps(gore,separators=(',',':')),encoding='utf-8')
    manifest.update({'fileDataID':model,'textures':textures,'sourceClips':source_clips,'source':'local Classic WoW M2/SKIN/BLP, original geometry and bone tracks'})
    return manifest

if __name__=='__main__':
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era');report={}
    for kind,model,textures,height in [('abomination',123961,[123964,123969],2.8),('scourge_ghoul',124160,[124162],1.9),('scourge_zombie',126571,[126573],1.95),('scourge_skeleton',125942,[125944],1.95)]:
        report[kind]=run(kind,model,textures,height,casc)
    report['geist']=run('geist',125234,[125237],1.3,LocalCasc('C:/Program Files (x86)/World of Warcraft'))
    output=ROOT/'assets-source/26.7/enemy-skins/source-meshes-wow.json';output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
