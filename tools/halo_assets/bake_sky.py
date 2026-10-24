"""Bake extracted MCC sky geometry/material layers to the mod's panorama layout.
Inputs are read-only Reclaimer JSON/DDS exports; no full map dump is required.
"""
from pathlib import Path
import argparse,json,math,struct
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
def texture(path):
    data=path.read_bytes();h,w=struct.unpack_from('<II',data,12)
    fmt=struct.unpack_from('<I',data,128)[0] if data[84:88]==b'DX10' else 0
    image=Image.frombytes('RGBA',(w,h),data[148:148+w*h*4],'raw','BGRA') if fmt==87 else Image.open(path).convert('RGBA')
    pixels=np.asarray(image,dtype=np.float32)/255;pixels[:,:,:3]=pixels[:,:,:3]**2.2;return pixels

def bake(folder,name,out,width=2048):
    import trimesh
    from trimesh.ray.ray_pyembree import RayMeshIntersector
    height=width//2;mesh=json.loads((folder/'geometry.json').read_text());textures={};layers=[]
    for m in mesh:
        xyz=np.asarray(m['positions'],dtype=np.float64);uv=np.asarray(m['uv'],dtype=np.float64)
        for segment in m['segments']:
            if segment['id']==127 and name=='zenith':continue
            ids=np.asarray(segment['indices']).reshape(-1,3);distance=np.mean(np.linalg.norm(xyz[np.unique(ids)],axis=1))
            layers.append((distance,xyz,uv,segment,ids))
    layers.sort(key=lambda item:(item[3]['alpha']!='opaque',-item[0]))
    longitude=(np.arange(width)+.5)/width*2*np.pi-np.pi;latitude=(np.arange(height)+.5)/height*np.pi
    sinlat=np.sin(latitude);dirs=np.stack(np.broadcast_arrays(sinlat[:,None]*np.cos(longitude)[None,:],sinlat[:,None]*np.sin(longitude)[None,:],np.cos(latitude)[:,None]),axis=-1).reshape(-1,3)
    origins=np.zeros_like(dirs);origins[:,2]=1.5
    canvas=np.zeros((height,width,3),np.float32)
    def sample(id,coords,tile=(1,1)):
        if id not in textures:
            p=folder/f'{id}_0.dds'
            if not p.exists():raise FileNotFoundError(f'Missing material texture: {p}')
            textures[id]=texture(p)
        tex=textures[id];h,w=tex.shape[:2];u=(coords[:,0]*tile[0])%1;v=(coords[:,1]*tile[1])%1
        x=u*(w-1);y=v*(h-1);x0=x.astype(int);y0=y.astype(int);fx=(x-x0)[:,None];fy=(y-y0)[:,None]
        return (tex[y0,x0]*(1-fx)+tex[y0,np.minimum(w-1,x0+1)]*fx)*(1-fy)+(tex[np.minimum(h-1,y0+1),x0]*(1-fx)+tex[np.minimum(h-1,y0+1),np.minimum(w-1,x0+1)]*fx)*fy
    for distance,xyz,uv,seg,triangles in layers:
        id=seg['id'];material=json.loads((folder/f'material_{id}.json').read_text())[0] if (folder/f'material_{id}.json').exists() else {}
        params=dict(zip(material.get('names',[]),material.get('constants',[])))
        constants=material.get('constants',[]);maps=seg['mappings'];primary=next((v for v in maps if v['usage']=='diffuse'),next((v for v in maps if v['usage']=='self_illum'),maps[0] if maps else None))
        if name=='zenith' and id==124:primary=next(v for v in maps if v['id']==126)
        depth=np.full((height,width),np.inf,np.float32);color=np.zeros((height,width,3),np.float32);alpha=np.zeros((height,width),np.float32)
        intersector=RayMeshIntersector(trimesh.Trimesh(vertices=xyz,faces=triangles,process=False))
        triangle_ids,ray_ids,hits=intersector.intersects_id(origins,dirs,multiple_hits=False,return_locations=True)
        if not len(ray_ids):continue
        weights=trimesh.triangles.points_to_barycentric(xyz[triangles[triangle_ids]],hits)
        coords=np.sum(uv[triangles[triangle_ids]]*weights[:,:,None],axis=1);px=ray_ids%width;py=ray_ids//width;z=np.linalg.norm(hits-origins[ray_ids],axis=1)
        rgba=sample(primary['id'],coords,primary['tile']) if primary else np.ones((len(px),4),np.float32)
        rgb=rgba[:,:3].copy();opacity=rgba[:,3].copy();elevation=np.cos((py+.5)/height*np.pi)
        if name=='blizzard':
            tint=params.get('albedo_color',[1,1,1,1]);rgb*=np.asarray(tint[:3])
            if id==104:
                rgb=sample(108,coords,(7,7))[:,:3]*2.5;rgb+=np.exp(-np.maximum(0,elevation)*7)[:,None]*np.array([.025,.045,.065]);opacity[:]=1
            elif id==122:
                rgb=sample(127,coords,(.5,1))[:,:3]*sample(126,coords)[:,:3]*2.2;opacity[:]=1
            elif id==110:
                rgb=sample(112,coords,(4,2))[:,:3]*np.array([.35,.4,.55]);opacity=sample(111,coords)[:,3]*.5
            elif id==113:rgb=rgb*.65+np.array([.07,.11,.16]);opacity[:]=1
            elif id==129:rgb=sample(133,coords)[:,:3]*np.array([1.7,1.3,2.3]);opacity[:]=1
            elif id in [134,157]:rgb=rgb*np.array([.24,.34,.48])
            elif id==150:
                rgb=rgb*(sample(155,coords,(1,.5))[:,:3]+sample(156,coords)[:,:3])*np.array([.45,.54,.72])
                opacity=rgba[:,3]
            elif id==140:rgb=rgb*np.array([.19,.24,.32]);opacity*=.45
        else:
            if id==93:rgb=np.maximum(0,1-np.abs(elevation))[:,None]**8*np.array(constants[2][:3])*.55+np.array([.0015,.003,.007]);opacity[:]=1
            elif id==96:rgb=np.array(constants[2][:3])[None,:]*np.maximum(0,1-np.abs(coords[:,1]*2-1))[:,None]*.12;opacity[:]=1
            elif id==107:rgb=sample(109,coords)[:,:3]*np.array(constants[0][:3])+sample(110,coords)[:,:3]*np.array(constants[1][:3]);opacity[:]=1
            elif id==102:rgb=sample(104,coords)[:,:3]*.75;opacity[:]=1
            elif id==111:
                rgb=sample(113,coords)[:,:3]*np.array(constants[0][:3])*1.6
                # The source planet mesh ends below its level's terrain horizon.
                opacity=np.clip((elevation+.02)/.09,0,1)
            elif id==115:rgb=np.tile(np.array(constants[2][:3])*constants[2][3]*.2,(len(px),1));opacity[:]=1
            elif id==116:
                rgb=np.zeros((len(px),3),np.float32)
                for channel in range(5):rgb+=sample(99,coords,material['tiling'][channel][:2])[:,:3]*np.array(constants[channel][:3])*constants[channel][3]*.07
                opacity=np.clip((elevation+.02)/.09,0,1)
            elif id==119:rgb=sample(120,coords,(2,2))[:,:3]*np.array([.6,1,1])*1.5;opacity[:]=1
            elif id in [97,122]:rgb=sample(99,coords)[:,:3]*np.array([.17,.25,.29]);opacity[:]=1
            elif id==124:
                # MCC format Unknown30 (light_volume_glow) is unsupported by Reclaimer.
                # Its secondary, extracted flare supplies the soft glow without a solid quad.
                rgb=sample(126,coords)[:,:3]*np.array([1,.67,.26])*3;opacity[:]=1
        color[py,px]=rgb;alpha[py,px]=np.clip(opacity,0,1);depth[py,px]=z

        valid=alpha>0
        if seg['alpha']=='additive':canvas[valid]+=color[valid]*alpha[valid,None]
        else:canvas[valid]=color[valid]*alpha[valid,None]+canvas[valid]*(1-alpha[valid,None])
        print(name,id,seg['name'],int(valid.sum()),flush=True)
    # Empty nadir lies below the original backdrop; continue the horizon ground color.
    if name=='blizzard':
        # Below the ice field the source mesh is open (hidden by terrain in Halo).
        # Extend its last visible ground sample, avoiding a black belt when looking down.
        for x in range(width):
            visible=np.flatnonzero(canvas[height//2:int(height*.55),x].sum(axis=1)>1e-7)
            last=height//2+visible[-1] if len(visible) else height//2
            canvas[last+1:,x]=canvas[last,x]
    else:
        empty=(canvas.sum(axis=2)<1e-7)&(np.arange(height)[:,None]>height*.5);canvas[empty]=[.005,.012,.017]
    out.parent.mkdir(parents=True,exist_ok=True);Image.fromarray((np.clip(canvas,0,1)**(1/2.2)*255).astype('uint8')).save(out,optimize=True)
    print('SAVED',out,out.stat().st_size,flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('name',choices=['blizzard','zenith']);p.add_argument('output',type=Path);p.add_argument('--width',type=int,default=2048);a=p.parse_args();bake(a.input,a.name,a.output,a.width)
