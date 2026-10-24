"""Export the imported idle poses as standard OBJ/MTL files for desktop inspection.

The game retains all skeletal animations in zrmesh.gz; OBJ is a static preview.
"""
from pathlib import Path
import sys,struct,gzip,shutil
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np

class Reader:
    def __init__(self,data):self.data=data;self.pos=0
    def get(self,fmt):
        n=struct.calcsize('>'+fmt);v=struct.unpack_from('>'+fmt,self.data,self.pos);self.pos+=n;return v[0] if len(v)==1 else v
    def string(self):n=self.get('H');b=self.data[self.pos:self.pos+n];self.pos+=n;return b.decode()

for kind in ['panzer','brutus']:
    source=ROOT/'src/main/resources/assets/zombierool';r=Reader(gzip.decompress((source/'models/entities'/(kind+'.zrmesh.gz')).read_bytes()));assert r.get('I')==0x5A524D31
    bones=r.get('I');surfaces=[]
    for _ in range(r.get('I')):
        texture=r.string().split(':')[1];nv,ni=r.get('II');verts=[]
        for i in range(nv):
            v=r.get('8f');weights=[r.get('Hf') for _ in range(r.get('B'))];verts.append((v,weights))
        faces=[r.get('3I') for _ in range(ni//3)];surfaces.append((texture,verts,faces))
    for _ in range(r.get('I')):
        name=r.string();fps,nf=r.get('fI');n=nf*bones*12
        if name=='idle':skin=np.frombuffer(r.data,dtype='>f4',count=bones*12,offset=r.pos).reshape(bones,3,4)
        r.pos+=n*4
    dest=Path.home()/'Desktop/Modeles Mini Boss'/kind;dest.mkdir(parents=True,exist_ok=True);lines=[f'mtllib {kind}.mtl'];materials=[];offset=1
    for i,(texture,vertices,faces) in enumerate(surfaces):
        mat=f'material_{i}';local='textures/'+Path(texture).name;materials.extend([f'newmtl {mat}','Kd 1 1 1',f'map_Kd {local}',''])
        tex=dest/local;tex.parent.mkdir(exist_ok=True);shutil.copy2(source/texture,tex)
        lines.extend([f'g surface_{i}',f'usemtl {mat}'])
        for values,weights in vertices:
            pos=np.array([*values[:3],1]);posed=sum(skin[b]@pos*w for b,w in weights);lines.append('v '+' '.join(f'{x:.6f}' for x in posed))
        for values,_ in vertices:lines.append(f'vt {values[6]:.6f} {1-values[7]:.6f}')
        for face in faces:lines.append('f '+' '.join(f'{i+offset}/{i+offset}' for i in face))
        offset+=len(vertices)
    (dest/(kind+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf8');(dest/(kind+'.mtl')).write_text('\n'.join(materials)+'\n',encoding='utf8');print('Exported',dest/(kind+'.obj'))
