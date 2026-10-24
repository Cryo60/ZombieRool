"""Import actual COD meshes and skeletal clips into the native Minecraft renderer.

Inputs: public Panzer Soldat V1.5 (shippuden1592) and Brutus 2 (Spiki) packs.
Format references: mauserzjeh/cod-asset-importer, Laupetin/OpenAssetTools.
XMODEL_BIN/XANIM_BIN decoding uses vendored MIT PyCoD (see vendor/PyCoD/LICENSE).
No executable from either asset pack is run.
"""
from pathlib import Path
import sys,struct,gzip,json,shutil,io,math,hashlib
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tools/vendor'),str(ROOT/'build/assets-python'),str(ROOT/'build/audio-python'),str(ROOT/'build/video-python')]
import numpy as np
from PIL import Image
from PyCoD import Model,Anim

class Reader:
    def __init__(self,path):self.data=Path(path).read_bytes();self.pos=0
    def get(self,fmt):
        fmt='<'+fmt;n=struct.calcsize(fmt);v=struct.unpack_from(fmt,self.data,self.pos);self.pos+=n;return v[0] if len(v)==1 else v
    def skip(self,n):self.pos+=n
    def string(self):
        end=self.data.index(0,self.pos);s=self.data[self.pos:end].decode();self.pos=end+1;return s

def transform(pos=(0,0,0),q=(0,0,0,1)):
    x,y,z,w=q;m=np.eye(4);m[:3,:3]=[[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]];m[:3,3]=pos;return m
def quaternion(v):
    v=np.array(v,dtype=float)/32767.;return np.append(v,math.sqrt(max(0,1-float(v@v))))
def world(locals_,parents):
    out=[]
    for m,p in zip(locals_,parents):out.append(m if p<0 else out[p]@m)
    return np.array(out)

def compiled_model(raw,name):
    r=Reader(raw/'xmodel'/name);assert r.get('H')==62;r.skip(28);r.string();r.string();r.skip(5)
    lods=[]
    for i in range(4):distance=r.get('f');s=r.string();lods.append(s)
    r.skip(4)
    for i in range(r.get('I')):n=r.get('I');r.skip(n*48+36)
    materials=[]
    for lod in filter(None,lods):materials.append([r.string() for _ in range(r.get('H'))])
    r=Reader(raw/'xmodelparts'/lods[0]);assert r.get('H')==62;n,roots=r.get('HH');parents=[-1]*roots;local=[np.eye(4) for _ in range(roots)]
    for i in range(n):parents.append(r.get('B'));pos=r.get('3f');q=quaternion(r.get('3h'));local.append(transform(pos,q))
    names=[r.string() for _ in range(n+roots)];bind=world(local,parents)
    r=Reader(raw/'xmodelsurfs'/lods[0]);assert r.get('H')==62;meshes=[]
    for mat in materials[0][:r.get('H')]:
        r.skip(3);nv,nt,nv2=r.get('HHH')
        if nv!=nv2:
            r.skip(2)
            if nv2:
                while r.get('H')!=0:pass
                r.skip(2)
        else:rigid_bone=r.get('H');r.skip(2)
        verts=[]
        for i in range(nv):
            normal=r.get('3f');r.skip(4);uv=r.get('2f');r.skip(28);nw=r.get('B') if nv!=nv2 else 0;b=r.get('H') if nv!=nv2 else rigid_bone;pos=r.get('3f');weights=[]
            for j in range(nw):bi,w=r.get('HH');weights.append((bi,w/65535.))
            weights.insert(0,(b,1-sum(w for bi,w in weights)));verts.append((pos,normal,uv,weights))
        faces=[r.get('3H') for i in range(nt)];meshes.append((mat,verts,faces))
    return names,parents,np.array(local),bind,meshes

def compiled_anim(path,names,parents,local,include_notes=False,include_tracks=False,include_root_motion=False):
    r=Reader(path);ver,nf,nb,flags,typ,fps=r.get('HHHBBH');assert ver in (17,19),ver
    count=nf+(1 if flags&1 else 0);byte=count-1<256
    if flags&8:r.skip(4)
    def indices(n):return list(range(n)) if n>=count else [r.get('B' if byte else 'H') for i in range(n)]
    def rotations(half=False,flip=False):
        n=r.get('H')
        if not n:return None
        idx=[0] if n==1 else indices(n);vals=[]
        for i in range(n):
            q=quaternion((0,0,r.get('h')) if half else r.get('3h'))
            if (i==0 and flip) or (i>0 and vals[-1]@q<0):q=-q
            vals.append(q)
        return idx,vals
    def translations():
        n=r.get('H')
        if not n:return None
        if n==1:return [0],[np.array(r.get('3f'))]
        idx=indices(n);small=r.get('B');mins=np.array(r.get('3f'));size=np.array(r.get('3f'))/(255 if small else 65535)
        return idx,[mins+size*np.array(r.get('3B' if small else '3H')) for i in range(n)]
    root_translation=None
    if flags&2:
        rotations(True)
        root_translation=translations() # Navigation supplies motion; retain it for stride calibration.
    size=(nb+7)//8;flip=[r.get('B') for _ in range(size)];half=[r.get('B') for _ in range(size)];bn=[r.string() for _ in range(nb)];tracks={}
    for i,name in enumerate(bn):tracks[name]=(rotations(bool(half[i//8]&(1<<(i%8))),bool(flip[i//8]&(1<<(i%8)))),translations())
    notes=[(r.string(),r.get('H')) for _ in range(r.get('B'))];assert r.pos==len(r.data),(path,r.pos,len(r.data))
    def sample(track,f,quat=False):
        if track is None:return None
        ix,v=track;k=max(0,min(len(ix)-1,int(np.searchsorted(ix,f,side='right')-1)))
        if k==len(ix)-1:return v[k]
        a=(f-ix[k])/(ix[k+1]-ix[k]);out=v[k]*(1-a)+v[k+1]*a
        return out/np.linalg.norm(out) if quat else out
    frames=[]
    for f in range(count):
        ls=local.copy()
        for i,name in enumerate(names):
            if name not in tracks:continue
            qr,tr=tracks[name];q=sample(qr,f,True);p=sample(tr,f)
            if q is not None:ls[i,:3,:3]=transform(q=q)[:3,:3]
            if p is not None:ls[i,:3,3]=p
            # Relative actor clips offset the model's main root from its reference height.
            if typ==2 and name=='j_mainroot' and p is not None:ls[i,:3,3]+=local[i,:3,3]
        frames.append(world(ls,parents))
    if include_root_motion:
        motion=np.array([sample(root_translation,f) if root_translation else np.zeros(3) for f in range(count)])
        return fps,np.array(frames),notes,bn,motion
    if include_tracks:return fps,np.array(frames),notes,bn
    return (fps,np.array(frames),notes) if include_notes else (fps,np.array(frames))

def export(kind,names,parents,bind,meshes,clips,textures,height):
    out=ROOT/'src/main/resources/assets/zombierool';folder=out/'models/entities';folder.mkdir(parents=True,exist_ok=True)
    # Change COD's forward-X/up-Z inches to Minecraft forward-Z/up-Y blocks.
    points=np.array([v[0] for _,vs,_ in meshes for v in vs]);scale=height/(points[:,2].max()-points[:,2].min());c=np.array([[0,-scale,0,0],[0,0,scale,-points[:,2].min()*scale],[-scale,0,0,0],[0,0,0,1.]])
    ci=np.linalg.inv(c);inverse=np.linalg.inv(bind);manifest={'bones':len(names),'vertices':len(points),'triangles':sum(len(fs) for _,_,fs in meshes),'height':height,'clips':{}}
    buf=io.BytesIO()
    def write(fmt,*v):buf.write(struct.pack('>'+fmt,*v))
    def string(s):b=s.encode();write('H',len(b));buf.write(b)
    write('II',0x5A524D31,len(names));write('I',len(meshes))
    for mat,vs,fs in meshes:
        tex='textures/entities/'+kind+'/'+mat+'.png';string('zombierool:'+tex);dest=out/tex;dest.parent.mkdir(parents=True,exist_ok=True);textures[mat].save(dest)
        write('II',len(vs),len(fs)*3)
        for pos,normal,uv,weights in vs:
            p=(c@np.append(pos,1))[:3];n=c[:3,:3]@normal/scale;write('8f',*p,*n,*uv);write('B',len(weights))
            assert abs(sum(w for _,w in weights)-1)<.001
            for bi,w in weights:assert bi<len(names);write('Hf',bi,w)
        for tri in fs:write('3I',*tri)
    write('I',len(clips))
    for name,(fps,frames) in clips.items():
        string(name);write('fI',fps,len(frames));manifest['clips'][name]={'frames':len(frames),'fps':fps,'seconds':round(len(frames)/fps,3)}
        for frame in frames:
            # Pin horizontal root displacement; actual movement follows the server.
            frame=frame.copy();root=names.index('j_mainroot') if 'j_mainroot' in names else 0;frame[:,:2,3]-=frame[root,:2,3]
            skin=c@frame@inverse@ci
            assert np.isfinite(skin).all()
            buf.write(np.asarray(skin[:,:3,:],dtype='>f4').tobytes())
    dest=folder/(kind+'.zrmesh.gz');dest.write_bytes(gzip.compress(buf.getvalue(),compresslevel=9,mtime=0));manifest['bytes']=dest.stat().st_size;print(kind,json.dumps(manifest));return manifest

def panzer():
    raw=ROOT/'build/boss-assets/panzer-raw/raw';names,parents,local,bind,meshes=compiled_model(raw,'c_zom_mech_body');print('Panzer bones',len(names),names[:5])
    for file in (raw/'xmodel').iterdir():
        # Origins uses the grapple claw; the alternate taser occupies the same socket.
        if file.name in ['c_zom_mech_body','c_zom_mech_taser','c_zom_mech_powersupply_cap']:continue
        an,ap,al,ab,am=compiled_model(raw,file.name);mapping={}
        for i,name in enumerate(an):
            if name in names:mapping[i]=names.index(name)
            else:
                p=mapping[ap[i]] if ap[i]>=0 else 0;mapping[i]=len(names);names.append(name);parents.append(p);local=np.append(local,[al[i]],axis=0);bind=np.append(bind,[bind[p]@al[i]],axis=0)
        # Attachments are authored in their own root's coordinate space.
        attachment=bind[mapping[0]]@np.linalg.inv(ab[0])
        for mat,vs,fs in am:
            vv=[]
            for pos,n,uv,weights in vs:vv.append(((attachment@np.append(pos,1))[:3],attachment[:3,:3]@n,uv,[(mapping[b],w) for b,w in weights]))
            meshes.append((mat,vv,fs))
    textures={}
    for mat,_,_ in meshes:
        # Diffuse image references are stored as plain C strings in the material.
        data=(raw/'materials'/mat).read_bytes();refs=[s.decode() for s in data.split(b'\0') if b'_c' in s and all(32<=b<127 for b in s)]
        candidates=[p for p in (raw/'images').glob('*.iwi') if p.stem in refs]
        if not candidates:
            candidates=[p for p in (raw/'images').glob('*.iwi') if mat.removeprefix('mtl_').replace('_headgear','_alpha').replace('_head_unlit','_head')+'_c' in p.stem]
        assert candidates,(mat,refs)
        r=Reader(candidates[0]);b=r.data;fmt=b[4];w,h=struct.unpack_from('<HH',b,6);fourcc={11:b'DXT1',12:b'DXT3',13:b'DXT5'}[fmt];size=w*h*(8 if fmt==11 else 16)//16
        header=struct.pack('<I6I11I',124,0x81007,h,w,size,0,1,*([0]*11))+struct.pack('<II4s5I',32,4,fourcc,0,0,0,0,0)+struct.pack('<5I',0x1000,0,0,0,0)
        textures[mat]=Image.open(io.BytesIO(b'DDS '+header+b[-size:])).convert('RGBA')
    selected={'idle':'idle','walk':'walk_basic','run':'run','spawn':'intro_jump_in','melee':'melee_a','flame':'ft_fire_loop','grab':'grapple_arm_open_idle','death':'death'}
    clips={key:compiled_anim(raw/'xanim'/('ai_zombie_mech_'+file),names,parents,local) for key,file in selected.items()}
    return export('panzer',names,parents,bind,meshes,clips,textures,2.65)

def brutus():
    root=ROOT/'build/boss-assets/brutus-raw/cellbreaker/model_export/Custom/AI';m=Model.FromFile_Bin(str(root/'brutus/c_zom_cellbreaker_fb_LOD0.XMODEL_BIN'));names=[b.name for b in m.bones];parents=[b.parent for b in m.bones];bind=[]
    for b in m.bones:
        mat=np.eye(4);mat[:3,:3]=np.array(b.matrix).T;mat[:3,3]=b.offset;bind.append(mat)
    bind=np.array(bind);local=np.array([b if p<0 else np.linalg.inv(bind[p])@b for b,p in zip(bind,parents)]);meshes=[];textures={}
    for material in m.materials:
        p=root/'_images'/material.images['color'].replace('\\','/').split('/')[-1];textures[material.name]=Image.open(p).convert('RGBA')
    helmet=Model.FromFile_Bin(str(root/'brutus/c_zom_cellbreaker_helmet_LOD0.XMODEL_BIN'))
    for material in helmet.materials:
        p=root/'_images'/material.images['color'].replace('\\','/').split('/')[-1];textures[material.name]=Image.open(p).convert('RGBA')
    for model in [m,helmet]:
      attachment=np.eye(4) if model is m else bind[names.index('j_head')]
      for mesh in model.meshes:
        grouped={}
        for face in mesh.faces:
            mat=model.materials[face.material_id].name;vs,fs=grouped.setdefault(mat,([],[]));ids=[]
            for fv in face.indices:
                v=mesh.verts[fv.vertex];ids.append(len(vs))
                weights=v.weights if model is m else [(names.index('j_head'),1.)]
                vs.append(((attachment@np.append(v.offset,1))[:3],attachment[:3,:3]@fv.normal,fv.uv,weights))
            fs.append(ids)
        meshes.extend((mat,vs,fs) for mat,(vs,fs) in grouped.items())
    clips={}
    for key,file in {'idle':'idle_a','walk':'walk_a','run':'run_a','spawn':'spawn','melee':'attack_swingleft','slam':'boardsmash_a','death':'death'}.items():
        a=Anim.FromFile_Bin(str(root/'anims/brutus'/('ai_zombie_cellbreaker_'+file+'.XANIM_BIN')));mapping={i:names.index(p.name) for i,p in enumerate(a.parts) if p.name in names};frames=[]
        for frame in a.frames:
            pose=bind.copy();overrides={}
            for i,b in enumerate(frame.parts):
                if i in mapping:
                    mat=np.eye(4);mat[:3,:3]=np.array(b.matrix).T;mat[:3,3]=b.offset;overrides[mapping[i]]=mat
            for i,p in enumerate(parents):pose[i]=overrides.get(i,local[i] if p<0 else pose[p]@local[i])
            frames.append(pose)
        clips[key]=(a.framerate,np.array(frames))
    return export('brutus',names,parents,bind,meshes,clips,textures,2.4)

if __name__=='__main__':
    manifest={'panzer':panzer(),'brutus':brutus()}
    for kind,file,url,credits in [('panzer','panzer.rar','https://www.ugx-mods.com/forum/models/125/panzer-soldat-v1-5-new-spawn-system/23087/',['Treyarch/Activision','shippuden1592','psulions45','Venom Modding']),('brutus','brutus.zip','https://forum.modme.co/wiki/threads/2875.html',['Treyarch/Activision','Spiki'])]:
        manifest[kind]['source']={'page':url,'sha256':hashlib.sha256((ROOT/'build/boss-assets'/file).read_bytes()).hexdigest(),'credits':credits}
    p=ROOT/'assets-source/26.7/mini-boss-models.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
    source=ROOT/'src/main/resources/assets/zombierool'
    for base in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool']:
        for kind in manifest:
            (base/'models/entities').mkdir(parents=True,exist_ok=True)
            shutil.copytree(source/'textures/entities'/kind,base/'textures/entities'/kind,dirs_exist_ok=True);shutil.copy2(source/'models/entities'/(kind+'.zrmesh.gz'),base/'models/entities'/(kind+'.zrmesh.gz'))
