"""Rebuild the sky necropolis from the installed Classic floating-necropolis geometry."""
from pathlib import Path
import struct,json,io,hashlib,math
import numpy as np
from PIL import Image,ImageDraw
from local_casc import LocalCasc
from import_wow_meshes import chunks,DTYPE
ROOT=Path(__file__).resolve().parents[2]

def main():
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow')
    def wmo_chunks(raw,start=0):
        out={};p=start
        while p+8<=len(raw):
            key=raw[p:p+4][::-1].decode('ascii');size=struct.unpack_from('<I',raw,p+4)[0];out[key]=raw[p+8:p+8+size];p+=8+size
        return out
    raw=casc.read(111729);root=wmo_chunks(raw);group_ids=np.frombuffer(root['GFID'],'<u4');materials=root['MOMT'];images={};surfaces=[]
    for group,fd in enumerate(group_ids):
        if not fd:continue
        ch=wmo_chunks(wmo_chunks(casc.read(int(fd)))['MOGP'],68)
        if 'MOVT' not in ch:continue
        verts=np.frombuffer(ch['MOVT'],'<f4').reshape(-1,3).astype(float);uv=np.frombuffer(ch['MOTV'],'<f4').reshape(-1,2)
        tris=np.frombuffer(ch['MOVI'],'<u2').reshape(-1,3);mats=np.frombuffer(ch['MOPY'],'u1').reshape(-1,2)
        for ids,info in zip(tris,mats):
            flags,mat=map(int,info)
            if mat==255 or not flags&32:continue
            tex=struct.unpack_from('<I',materials,mat*64+12)[0]
            if tex not in images:
                try:images[tex]=np.array(Image.open(io.BytesIO(casc.read(tex))).convert('RGBA'))
                except Exception:images[tex]=None
            xyz=verts[ids][:,[0,2,1]].copy();xyz[:,2]*=-1
            surfaces.append((xyz,uv[ids],tex))
    all_vertices=np.concatenate([t[0] for t in surfaces]);low=all_vertices.min(0);high=all_vertices.max(0);unit=max(high-low)/42;center=(low+high)/2
    cells={};source_faces=[]
    palette=np.array([[.19,.19,.23],[.27,.27,.31],[.37,.37,.40],[.44,.43,.44],[.3,.38,.27],[.23,.63,.12],[.38,.88,.13],[.49,.44,.26]])
    for xyz,uv,tex in surfaces:
        a,b,c=(xyz-center)/unit;image=images[tex];steps=max(1,math.ceil(max(np.linalg.norm(b-a),np.linalg.norm(c-a),np.linalg.norm(c-b))*1.65))
        for j in range(steps+1):
            for k in range(steps+1-j):
                u=j/steps;v=k/steps;point=a+(b-a)*u+(c-a)*v;coord=tuple(np.floor(point).astype(int));color=np.array([.3,.3,.34])
                if image is not None:
                    st=(uv[0]+(uv[1]-uv[0])*u+(uv[2]-uv[0])*v)%1;pixel=image[int(st[1]*image.shape[0])%image.shape[0],int(st[0]*image.shape[1])%image.shape[1]]/255
                    if pixel[3]<.3:continue
                    color=pixel[:3]
                cells[coord]=5 if color[1]>.28 and color[1]>color[0]*1.3 and color[1]>color[2]*1.3 else 2 if color.mean()>.38 else 0
    print('Source exterior triangles',len(surfaces),'surface voxels',len(cells),flush=True)
    # Greedy merge only identical voxel materials. No replacement building templates.
    boxes=[]
    while cells:
        origin=min(cells);mat=cells.pop(origin);x,y,z=origin;sx=1
        while cells.get((x+sx,y,z))==mat:sx+=1
        sy=1
        while all(cells.get((xx,y+sy,z))==mat for xx in range(x,x+sx)):sy+=1
        sz=1
        while all(cells.get((xx,yy,z+sz))==mat for xx in range(x,x+sx) for yy in range(y,y+sy)):sz+=1
        for xx in range(x,x+sx):
            for yy in range(y,y+sy):
                for zz in range(z,z+sz):cells.pop((xx,yy,zz),None)
        color=palette[mat];boxes.append(dict(x=(x+sx/2)/21,y=(y+sy/2)/21,z=(z+sz/2)/21,sx=sx/21,sy=sy/21,sz=sz/21,r=color[0],g=color[1],b=color[2],alpha=1,emissive=1 if mat in [5,6] else 0))
    assert len(boxes)<7500,len(boxes)
    out=ROOT/'src/main/resources/assets/zombierool/scenery/necropolis.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps({'sourceFileDataID':111729,'boxes':boxes},separators=(',',':')))
    receipt={'source':'World/WMO/Dungeon/ND_Necropolis/ND_Necropolis01.wmo','fileDataID':111729,'sha256':hashlib.sha256(raw).hexdigest(),'groupFileDataIDs':list(map(int,group_ids)),'sourceBounds':[low.tolist(),high.tolist()],'gridLongestSide':42,'boxes':len(boxes),'transform':'(X, Z, -Y), uniformly scaled; UV-sampled material palette; greedy voxel merge'}
    (ROOT/'assets-source/26.7/map-actors/necropolis-provenance.json').write_text(json.dumps(receipt,indent=2));print(receipt)

if __name__=='__main__':main()
