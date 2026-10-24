"""Bake local COD diffuse triangles onto ZombieRool's Minecraft UV layout.
Source meshes are straightened using skeleton joints before six-face rasterization.
No procedural replacement art or palette recoloring is used.
"""
from pathlib import Path
import sys,json,hashlib,math,gzip
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tools/vendor'),str(ROOT/'build/audio-python'),str(ROOT/'build/assets-python'),str(ROOT/'build/video-python')]
import numpy as np
from PIL import Image,ImageDraw
from PyCoD import Model
RAW=ROOT/'build/zombie-presets/raw';DEST=ROOT/'src/main/resources/assets/zombierool'; SCALE=4

def bone_matrix(b):
 m=np.eye(4);m[:3,:3]=np.array(b.matrix).T;m[:3,3]=b.offset;return m

def read_models(zone,names):
 models=[];anchor=None
 for name in names:
  path=zone/'model_export'/(name+'_lod0.xmodel_export');m=Model.FromFile_Raw(str(path)); transform=np.eye(4)
  if anchor is not None and m.bones[0].name in anchor:
   transform=anchor[m.bones[0].name]@np.linalg.inv(bone_matrix(m.bones[0]))
  if anchor is None:anchor={b.name:bone_matrix(b) for b in m.bones}
  mats=[]
  for mat in m.materials:
   if any(k in mat.name.lower() for k in ['eyes','eye_glow','eye_cornea','decal','gore']) or mat.name.endswith('/lambert1'):
    mats.append(None);continue
   path=(path.parent/mat.images['color'].replace('\\','/')).resolve()
   material_file=zone/'materials'/(mat.name+'.json')
   if material_file.is_file():
    # T6 skin shaders can put a rim mask before the actual diffuse map.
    info=json.loads(material_file.read_text())
    diffuse=[t['image'] for t in info.get('textures',[]) if t.get('name') in ['Diffuse_Map','colorMap']]
    if diffuse:path=zone/'images'/(diffuse[0]+'.dds')
   # Kino's compiled materials omit colorMap in their technique table. The
   # zone still contains their original, shared honorguard diffuse atlas.
   if path.name=='.dds' and 'honorguard_zombie' in mat.name:
    path=zone/'images/~-gchar_ger_honorguard_body1_c.dds'
   if not path.is_file(): raise FileNotFoundError(path)
   mats.append(np.asarray(Image.open(path).convert('RGBA')))
  models.append((m,transform,mats))
 return models,anchor

def group(name,hound=False):
 name=name.lower()
 if any(k in name for k in ['head','neck','jaw','lip','nose','eye','ear','helmet','mouth']):return 'head'
 if 'tail' in name:return 'tail' if hound else 'body'
 if any(k in name for k in ['shoulder','elbow','wrist','palm','finger','thumb','index','mid','ring','pinky']):return 'arm'
 if any(k in name for k in ['hip','knee','ankle','ball','toe']):return 'leg'
 return 'body'

def straighten(pos,name,anchor,hound=False):
 g=group(name,hound);side='le' if name.endswith('_le') or '_le_' in name else 'ri'
 if g in ('arm','leg'):
  chain=['j_shoulder_'+side,'j_elbow_'+side,'j_wrist_'+side] if g=='arm' else ['j_hip_'+side,'j_knee_'+side,'j_ankle_'+side]
  if all(x in anchor for x in chain):
   pts=[anchor[x][:3,3] for x in chain];seg=0 if any(x in name for x in ('shoulder','hip')) else 1
   a,b=pts[seg:seg+2];down=(b-a)/np.linalg.norm(b-a);front=np.array([1.,0,0]);front-=down*np.dot(front,down);front/=np.linalg.norm(front);right=np.cross(down,front);right/=np.linalg.norm(right)
   d=pos-a;return np.array([d@right,(0 if seg==0 else np.linalg.norm(pts[1]-pts[0]))+d@down,-d@front])
 return np.array([-pos[1],-pos[2],-pos[0]])

def triangles(models,anchor,hound=False,exclude_gear=False):
 groups={g:[] for g in ['head','body','arm','leg','tail']}
 for m,transform,mats in models:
  for mesh in m.meshes:
   for face in mesh.faces:
    material=m.materials[face.material_id]
    if mats[face.material_id] is None:continue
    if any(k in material.name.lower() for k in ['eyes','eye_glow','eye_cornea','decal','gore']):continue
    if exclude_gear and is_gear(material.name):continue
    vv=[mesh.verts[v.vertex] for v in face.indices]
    votes={}
    for v in vv:
     for bi,w in v.weights:
      name=m.bones[bi].name;g=group(name,hound);votes[g]=votes.get(g,0)+w
    g=max(votes,key=votes.get) if votes else 'body';points=[]
    for v in vv:
     name=m.bones[max(v.weights,key=lambda q:q[1])[0]].name if v.weights else 'tag_origin'
     p=(transform@np.array([*v.offset,1]))[:3]
     # Head accessories and small facial bones share the head's projection.
     points.append(straighten(p,name,anchor,hound) if group(name,hound)==g else np.array([-p[1],-p[2],-p[0]]))
    groups[g].append((np.array(points),np.array([fv.uv for fv in face.indices]),mats[face.material_id]))
 return groups

def is_gear(name):
 return any(k in name.lower() for k in ['helmet','offcap','gasmask','scubamask','beanie','tech_cap','guard_hat','german_hat','crusader_hat','backpack','rucksack'])

def accessories(models,anchor,base_groups,extra_models,target):
 surfaces=[]
 bounds={g:(np.concatenate([t[0] for t in ts]).min(axis=0),np.concatenate([t[0] for t in ts]).max(axis=0)) for g,ts in base_groups.items() if ts}
 for model_index,(m,transform,mats) in enumerate(models):
  for mat_index,material in enumerate(m.materials):
   if model_index not in extra_models and not is_gear(material.name):continue
   if any(k in material.name.lower() for k in ['eyes','decal','gore']):continue
   vertices=[];part='body' if any(k in material.name.lower() for k in ['backpack','rucksack']) else 'head'
   if part not in bounds:continue
   lo,hi=bounds[part];span=np.maximum(hi-lo,.001)
   size=np.array([8.,8.,8.]) if part=='head' else np.array([8.,12.,4.]);offset=np.array([-4.,-8.,-4.]) if part=='head' else np.array([-4.,0.,-2.])
   for mesh in m.meshes:
    for face in mesh.faces:
     if face.material_id!=mat_index:continue
     for fv in face.indices:
      v=mesh.verts[fv.vertex];p=(transform@np.array([*v.offset,1]))[:3];p=np.array([-p[1],-p[2],-p[0]]);p=(p-lo)/span*size+offset
      n=transform[:3,:3]@np.array(fv.normal);n=np.array([-n[1],-n[2],-n[0]])*span/size;n/=max(np.linalg.norm(n),1e-8)
      vertices.append([round(float(x),5) for x in [*p,*fv.uv,*n]])
   if not vertices:continue
   tex=target.parent/(target.stem.replace('.json','')+'_'+str(len(surfaces))+'.png');Image.fromarray(mats[mat_index]).save(tex)
   surfaces.append({'part':part,'texture':'zombierool:'+tex.relative_to(DEST).as_posix(),'vertices':vertices})
 if surfaces:
  target.write_bytes(gzip.compress(json.dumps({'surfaces':surfaces},separators=(',',':')).encode(),mtime=0))
 return len(surfaces)

def fill_holes(image,valid):
 if not valid.any():return image
 for _ in range(sum(valid.shape)):
  if valid.all():break
  old=valid.copy()
  for axis,delta in [(0,1),(0,-1),(1,1),(1,-1)]:
   mask=np.roll(old,delta,axis)&~valid
   if axis==0:mask[0 if delta==1 else -1,:]=False
   else:mask[:,0 if delta==1 else -1]=False
   image[mask]=np.roll(image,delta,axis)[mask];valid[mask]=True
 return image

def face_image(tris,w,h,axes,sign,flipx=False,flipy=False,repeat_uv=False):
 width,height=w*SCALE,h*SCALE;image=np.zeros((height,width,4),dtype=np.uint8);depth=np.full((height,width),-np.inf)
 points=np.concatenate([t[0] for t in tris]);lo=np.min(points,axis=0);hi=np.max(points,axis=0);span=np.maximum(hi-lo,.001)
 a,b,d=axes
 for p,uv,tex in tris:
  q=(p-lo)/span;coords=np.column_stack([q[:,a]*width,q[:,b]*height]);px,py=coords.T
  if flipx:px=width-px
  if flipy:py=height-py
  xmin=max(0,int(np.floor(px.min())));xmax=min(width-1,int(np.ceil(px.max())));ymin=max(0,int(np.floor(py.min())));ymax=min(height-1,int(np.ceil(py.max())))
  if xmin>xmax or ymin>ymax:continue
  den=(py[1]-py[2])*(px[0]-px[2])+(px[2]-px[1])*(py[0]-py[2])
  if abs(den)<1e-7:continue
  x,y=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
  u=((py[1]-py[2])*(x-px[2])+(px[2]-px[1])*(y-py[2]))/den;v=((py[2]-py[0])*(x-px[2])+(px[0]-px[2])*(y-py[2]))/den;t=1-u-v
  z=(u*q[0,d]+v*q[1,d]+t*q[2,d])*sign;old=depth[ymin:ymax+1,xmin:xmax+1];mask=(u>=-1e-6)&(v>=-1e-6)&(t>=-1e-6)&(z>old)
  sample=u[...,None]*uv[0]+v[...,None]*uv[1]+t[...,None]*uv[2]
  if repeat_uv:sample=np.mod(sample,1.)
  tx=np.clip((sample[:,:,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1);ty=np.clip((sample[:,:,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1);color=tex[ty,tx];mask &= color[:,:,3]>=100
  image[ymin:ymax+1,xmin:xmax+1][mask]=color[mask];old[mask]=z[mask]
 valid=np.isfinite(depth);coverage=float(valid.mean());image=fill_holes(image,valid);image[:,:,3]=255
 return Image.fromarray(image),coverage

def cube(skin,tris,u,v,w,h,d,repeat_uv=False):
 if not tris:raise ValueError('Missing required model part')
 faces=[(u+d,v+d,w,h,(0,1,2),-1,True,False),(u+2*d+w,v+d,w,h,(0,1,2),1,False,False),(u,v+d,d,h,(2,1,0),-1,False,False),(u+d+w,v+d,d,h,(2,1,0),1,True,False),(u+d,v,w,d,(0,2,1),-1,True,True),(u+d+w,v,w,d,(0,2,1),1,True,False)]
 coverage=[]
 for x,y,ww,hh,axes,sign,fx,fy in faces:
  img,c=face_image(tris,ww,hh,axes,sign,fx,fy,repeat_uv);skin.paste(img,(x*SCALE,y*SCALE));coverage.append(round(c,3))
 return coverage

def bake(zone,names,target,hound=False,extra_models=(),texture_size=128,shadow_lift=1,repeat_uv=False):
 models,anchor=read_models(zone,names);base=[m for i,m in enumerate(models) if i not in extra_models];groups=triangles(base,anchor,hound,exclude_gear=not hound);skin=Image.new('RGBA',(64*SCALE,(32 if hound else 64)*SCALE))
 if hound:
  # Match Modelwolf: head, muzzle and ears; mane/body have a 90-degree X rotation.
  coverage={'head':cube(skin,groups['head'],0,0,6,6,4)}
  cube(skin,groups['head'],0,10,3,3,4);cube(skin,groups['head'],16,14,2,2,1)
  groups['body']=[(p[:,[0,2,1]]*np.array([1,1,-1]),uv,tex) for p,uv,tex in groups['body']]
  for key,uv,dim in [('body',(21,0),(8,6,7)),('body',(18,14),(6,9,6)),('leg',(0,18),(2,8,2)),('tail',(9,18),(2,8,2))]:
   tris=groups[key] or groups['body'];cube(skin,tris,*uv,*dim)
 else:
  coverage={key:cube(skin,groups[key],*uv,*dim,repeat_uv=repeat_uv) for key,uv,dim in [('head',(0,0),(8,8,8)),('body',(16,16),(8,12,4)),('arm',(40,16),(4,12,4)),('leg',(0,16),(4,12,4))]}
 # Keep source cloth, facial and wound detail at the mod's original density.
 # Do not replace faces with painted sockets or quantize the entire outfit.
 skin=skin.resize((texture_size,texture_size//2 if hound else texture_size),Image.Resampling.BOX)
 if shadow_lift!=1:
  pixels=np.array(skin);pixels[:,:,:3]=np.round(255*(pixels[:,:,:3]/255.)**shadow_lift).astype(np.uint8);skin=Image.fromarray(pixels)
 target.parent.mkdir(parents=True,exist_ok=True);skin.save(target)
 mesh=target.with_suffix('.json.gz');count=0 if hound else accessories(models,anchor,groups,set(extra_models),mesh)
 return coverage,count

if __name__=='__main__':
 zone=RAW/'t5/zombie_pentagon';print(bake(zone,['c_usa_pent_zombie_scientist_body','c_ger_zombie_head1'],ROOT/'build/zombie-presets/five-scientist.png')[0])
