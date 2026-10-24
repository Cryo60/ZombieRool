"""Retain the reference silhouette with a modest mesh and Minecraft materials."""
from pathlib import Path
import os,sys,json,shutil,zipfile,io,math
os.environ['PXR_USDC_EMIT_DEPRECATION_WARNINGS']='0'
sys.path[:0]=['C:/Users/ecorange1/AppData/Local/ZombieRool/asset-tools/usd','C:/Users/ecorange1/AppData/Local/ZombieRool/asset-tools/mesh']
import numpy as np
import fast_simplification
from pxr import Usd,UsdGeom,UsdShade,Gf
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/zombierool';folder=A/'models/equipment';folder.mkdir(parents=True,exist_ok=True)
stage=Usd.Stage.Open(str(ROOT/'assets-source/26.7/sentry/sentry.usdz'));cache=UsdGeom.XformCache();bounds=UsdGeom.BBoxCache(0,['default','render']).ComputeWorldBound(stage.GetPseudoRoot()).ComputeAlignedBox();lo,hi=bounds.GetMin(),bounds.GetMax();scale=1.75/(hi[2]-lo[2]);cx=(hi[0]+lo[0])/2;cy=(hi[1]+lo[1])/2
models={k:['mtllib sentry.mtl'] for k in ['base','head','full']};counts=dict.fromkeys(models,0);indices=dict.fromkeys(models,0)
palette={'body':'minecraft:block/green_terracotta','steel':'minecraft:block/gray_concrete','dark':'minecraft:block/black_concrete','ammo':'minecraft:block/green_concrete','brass':'minecraft:block/yellow_terracotta'}
materials={};textureFolder=A/'textures/block/sentry';textureFolder.mkdir(parents=True,exist_ok=True)
# Reuse the atlas-boundary clipping routine without running the archival importer.
legacy=(ROOT/'tools/prepare-sentry-assets.py').read_text()
exec(legacy[legacy.index('def wrap_triangle'):legacy.index('with zipfile.ZipFile')])
archive=zipfile.ZipFile(ROOT/'assets-source/26.7/sentry/sentry.usdz')
for prim in stage.Traverse():
 if not prim.IsA(UsdGeom.Mesh):continue
 mesh=UsdGeom.Mesh(prim);world=cache.GetLocalToWorldTransform(prim);raw=mesh.GetPointsAttr().Get();points=[]
 for p in raw:
  q=world.Transform(Gf.Vec3d(*p));points.append([(q[0]-cx)*scale+.5,(q[2]-lo[2])*scale,(q[1]-cy)*scale+.5])
 triangles=[];triangleUV=[];offset=0;verts=mesh.GetFaceVertexIndicesAttr().Get();primvar=UsdGeom.PrimvarsAPI(mesh).GetPrimvar('st');uv=primvar.ComputeFlattened() if primvar else [];interp=primvar.GetInterpolation() if primvar else ''
 for fi,count in enumerate(mesh.GetFaceVertexCountsAttr().Get()):
  for j in range(1,count-1):
   corners=[offset,offset+j,offset+j+1]
   if (world.GetDeterminant()>0)==(mesh.GetOrientationAttr().Get()!='leftHanded'):corners.reverse()
   triangles.append([verts[k] for k in corners]);triangleUV.append([uv[k if interp=='faceVarying' else verts[k] if interp in ('vertex','varying') else fi if interp=='uniform' else 0] if len(uv) else (0,0) for k in corners])
  offset+=count
 # Weld exported duplicate vertices before decimation, preserving separate mechanical pieces.
 points,inv=np.unique(np.round(np.array(points,dtype=np.float64),6),axis=0,return_inverse=True);triangles=inv[np.array(triangles,dtype=np.int32)]
 mask=(triangles[:,0]!=triangles[:,1])&(triangles[:,1]!=triangles[:,2])&(triangles[:,0]!=triangles[:,2]);triangles=triangles[mask];triangleUV=np.array(triangleUV)[mask]
 oldTriangles=points[triangles];centers=oldTriangles.mean(axis=1)
 if len(triangles)>30:points,triangles=fast_simplification.simplify(points,triangles,target_count=max(24,int(len(triangles)*.38)),agg=4,preserve_border=True)
 mat=UsdShade.MaterialBindingAPI(prim).ComputeBoundMaterial()[0];name=str(mat.GetPath()).lower() if mat else '';color='body'
 if any(t in name for t in ['metal','aluminium','silver']):color='steel'
 elif any(t in name for t in ['black','dark','rubber']):color='dark'
 elif any(t in name for t in ['yellow','brass','gold']):color='brass'
 elif any(t in name for t in ['ammo','green']):color='ammo'
 # Untextured source surfaces often describe gunmetal; keep the barrel cluster dark.
 if not mat:color='body'
 key=str(mat.GetPath()) if mat else ''
 if mat and key not in materials:
  texture=None
  for child in mat.GetPrim().GetChildren():
   shader=UsdShade.Shader(child)
   if shader.GetIdAttr().Get()=='UsdUVTexture':
    value=shader.GetInput('file').Get();texture=str(value.path) if value else None
  if texture:
   name='source_'+str(len(materials));stem=Path(texture).stem.lower();Image.open(io.BytesIO(archive.read(texture))).convert('RGB').save(textureFolder/(stem+'.png'));palette[name]='zombierool:block/sentry/'+stem;materials[key]=name
  else:materials[key]=color
 color=materials.get(key,color)
 group='base' if '/Assembly164/Group2/' in str(prim.GetPath()) else 'head'
 for kind in [group,'full']:
  lines=models[kind];lines+=['o '+str(prim.GetPath()).replace('/','_'),'usemtl '+color]
  for t in triangles:
   ps=points[t];normal=np.cross(ps[1]-ps[0],ps[2]-ps[0]);length=np.linalg.norm(normal)
   if length<1e-9:continue
   normal/=length
   # Recover the local UV mapping from the nearest reference surface after decimation.
   nearest=int(np.argmin(((centers-ps.mean(axis=0))**2).sum(axis=1)));reference=oldTriangles[nearest];basis=np.array([reference[1]-reference[0],reference[2]-reference[0]]).T
   coords=np.linalg.lstsq(basis,(ps-reference[0]).T,rcond=None)[0].T;mapping=triangleUV[nearest];uvs=mapping[0]+coords[:,:1]*(mapping[1]-mapping[0])+coords[:,1:]*(mapping[2]-mapping[0])
   if not color.startswith('source_'):uvs=np.array([(0,0),(1,0),(1,1)])
   for triangle,u,v in wrap_triangle(ps,uvs):
    a,b,c=[np.array(p[:3]) for p in triangle]
    if np.linalg.norm(np.cross(b-a,c-a))<1e-10:continue
    start=indices[kind]+1
    for p in triangle:lines+=['v '+' '.join(f'{value:.6f}' for value in p[:3]),'vt '+f'{min(1,max(0,p[3]-u)):.6f} {min(1,max(0,p[4]-v)):.6f}','vn '+' '.join(f'{value:.6f}' for value in normal)]
    lines+=['f '+' '.join(f'{start+i}/{start+i}/{start+i}' for i in range(3))];indices[kind]+=3;counts[kind]+=1
for k,lines in models.items():(folder/f'sentry_{k}.obj').write_text('\n'.join(lines)+'\n')
(folder/'sentry.mtl').write_text('\n'.join(f'newmtl {k}\nKd 1 1 1\nmap_Kd {v}' for k,v in palette.items())+'\n')
def model(kind):return {'loader':'forge:obj','model':f'zombierool:models/equipment/sentry_{kind}.obj','flip_v':True,'automatic_culling':False,'shade_quads':True,'render_type':'minecraft:cutout','textures':{'particle':'minecraft:block/gray_concrete'}}
display={'gui':{'rotation':[25,35,0],'translation':[0,-4,0],'scale':[.55,.55,.55]},'ground':{'scale':[.3,.3,.3]},'thirdperson_righthand':{'scale':[.4,.4,.4]},'fixed':{'scale':[.6,.6,.6]}}
paths=[]
for rel,kind in [('models/block/deployed_sentry_turret.json','base'),('models/item/sentry_turret.json','full'),('models/item/sentry_tripod.json','base'),('models/item/sentry_receiver.json','head')]:
 d=model(kind)
 if '/item/' in rel:d['display']=display
 p=A/rel;p.write_text(json.dumps(d,indent=2)+'\n');paths.append(p)
archive.close()
for base in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool',ROOT/'build/resources/main/assets/zombierool']:
 for p in paths+list(folder.glob('sentry*'))+list(textureFolder.glob('*.png')):
  dest=base/p.relative_to(A);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
print('Stylized sentry faces:',counts)
