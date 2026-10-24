"""Convert the supplied public Warehouse USDZ into native Forge OBJ parts, retaining UVs/textures."""
from pathlib import Path
import sys,zipfile,json,os,shutil,math
os.environ['PXR_USDC_EMIT_DEPRECATION_WARNINGS']='0'
sys.path.insert(0,'C:/Users/ecorange1/AppData/Local/ZombieRool/asset-tools/usd')
from pxr import Usd,UsdGeom,UsdShade,Gf
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/zombierool';SOURCE=ROOT/'assets-source/26.7/sentry/sentry.usdz'
stage=Usd.Stage.Open(str(SOURCE));transforms=UsdGeom.XformCache();bbox=UsdGeom.BBoxCache(0,['default','render']).ComputeWorldBound(stage.GetPseudoRoot()).ComputeAlignedBox();lo,hi=bbox.GetMin(),bbox.GetMax();scale=1.75/(hi[2]-lo[2]);cx=(hi[0]+lo[0])/2;cy=(hi[1]+lo[1])/2
folder=A/'models/equipment';folder.mkdir(parents=True,exist_ok=True);textureFolder=A/'textures/block/sentry';textureFolder.mkdir(parents=True,exist_ok=True)
models={key:['mtllib sentry.mtl'] for key in ('base','head','full')};counts={key:0 for key in models};materials={};indices={key:0 for key in models}
def wrap_triangle(points,uvs):
 # Forge's shared texture atlas cannot repeat UVs outside its sprite. Clip at
 # integer tile boundaries so repeat textures retain the source's appearance.
 polygon=[tuple(p)+tuple(t) for p,t in zip(points,uvs)]
 def clip(poly,axis,bound,above):
  out=[]
  if not poly:return out
  a=poly[-1];inside=lambda p:p[axis]>=bound if above else p[axis]<=bound
  for b in poly:
   ia,ib=inside(a),inside(b)
   if ia!=ib:
    t=(bound-a[axis])/(b[axis]-a[axis]);out.append(tuple(x+(y-x)*t for x,y in zip(a,b)))
   if ib:out.append(b)
   a=b
  return out
 for u in range(math.floor(min(t[0] for t in uvs)),math.floor(max(t[0] for t in uvs))+1):
  for v in range(math.floor(min(t[1] for t in uvs)),math.floor(max(t[1] for t in uvs))+1):
   poly=polygon
   for axis,bound,above in [(3,u,True),(3,u+1,False),(4,v,True),(4,v+1,False)]:poly=clip(poly,axis,bound,above)
   for i in range(1,len(poly)-1):yield [poly[0],poly[i],poly[i+1]],u,v
with zipfile.ZipFile(SOURCE) as archive:
 for prim in stage.Traverse():
  if not prim.IsA(UsdGeom.Mesh):continue
  mesh=UsdGeom.Mesh(prim);world=transforms.GetLocalToWorldTransform(prim);pts=mesh.GetPointsAttr().Get();faces=mesh.GetFaceVertexCountsAttr().Get();verts=mesh.GetFaceVertexIndicesAttr().Get();uvs=UsdGeom.PrimvarsAPI(mesh).GetPrimvar('st');uv=uvs.ComputeFlattened() if uvs else [];interp=uvs.GetInterpolation() if uvs else ''
  mat=UsdShade.MaterialBindingAPI(prim).ComputeBoundMaterial()[0];name='m'+str(len(materials))
  key=str(mat.GetPath());color=(.25,.3,.25);texture=None
  if key not in materials:
   for child in (mat.GetPrim().GetChildren() if mat else []):
    shader=UsdShade.Shader(child)
    if shader.GetIdAttr().Get()=='UsdUVTexture':
     f=shader.GetInput('file').Get();texture=str(f.path) if f else None
    diffuse=shader.GetInput('diffuseColor') if shader else None
    if diffuse and diffuse.Get() is not None:color=tuple(diffuse.Get())
   if texture:
    import io
    image=Image.open(io.BytesIO(archive.read(texture))).convert('RGB');image.save(textureFolder/(Path(texture).stem.lower()+'.png'))
   materials[key]=(name,color,Path(texture).stem.lower() if texture else None)
  name,color,texture=materials[key];group='base' if '/Assembly164/Group2/' in str(prim.GetPath()) else 'head'
  for kind in (group,'full'):
   lines=models[kind];lines.extend(['o '+str(prim.GetPath()).replace('/','_'),'usemtl '+name]);offset=0
   for fi,count in enumerate(faces):
    for corner in range(1,count-1):
     ids=[offset,offset+corner,offset+corner+1];points=[];tex=[]
     if (world.GetDeterminant()>0)==(mesh.GetOrientationAttr().Get()!='leftHanded'):ids.reverse()
     for k in ids:
      p=world.Transform(Gf.Vec3d(*pts[verts[k]]));points.append(Gf.Vec3d((p[0]-cx)*scale+.5,(p[2]-lo[2])*scale,(p[1]-cy)*scale+.5));u=uv[k if interp=='faceVarying' else verts[k] if interp in ('vertex','varying') else fi if interp=='uniform' else 0] if len(uv) else (0,0);tex.append(u)
     normal=Gf.Cross(points[1]-points[0],points[2]-points[0]);normal.Normalize()
     if normal.GetLength()<.1:continue
     for triangle,u,v in wrap_triangle(points,tex):
      a,b,c=[Gf.Vec3d(*p[:3]) for p in triangle]
      if Gf.Cross(b-a,c-a).GetLength()<1e-10:continue
      start=indices[kind]+1
      for p in triangle:lines.extend(['v '+' '.join(f'{value:.6f}' for value in p[:3]),'vt '+f'{min(1,max(0,p[3]-u)):.6f} {min(1,max(0,p[4]-v)):.6f}','vn '+' '.join(f'{value:.6f}' for value in normal)])
      lines.append('f '+' '.join(f'{start+i}/{start+i}/{start+i}' for i in range(3)));indices[kind]+=3;counts[kind]+=1
    offset+=count
for kind,lines in models.items():(folder/f'sentry_{kind}.obj').write_text('\n'.join(lines)+'\n')
mtl=[]
for name,color,texture in materials.values():
 mtl.extend(['newmtl '+name,'Kd '+' '.join(map(str,color))])
 mtl.append('map_Kd zombierool:block/sentry/'+texture if texture else 'map_Kd minecraft:block/white_concrete')
(folder/'sentry.mtl').write_text('\n'.join(mtl)+'\n')
def save(path,value):
 p=A/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def obj(kind):return {'loader':'forge:obj','model':f'zombierool:models/equipment/sentry_{kind}.obj','flip_v':True,'automatic_culling':False,'shade_quads':True,'render_type':'minecraft:cutout','textures':{'particle':'minecraft:block/gray_concrete'}}
display={'gui':{'rotation':[25,35,0],'translation':[0,-4,0],'scale':[.55,.55,.55]},'ground':{'scale':[.3,.3,.3]},'thirdperson_righthand':{'scale':[.4,.4,.4]},'fixed':{'scale':[.6,.6,.6]}}
save(Path('models/block/deployed_sentry_turret.json'),obj('base'))
full=obj('full');full['display']=display;save(Path('models/item/sentry_turret.json'),full)
for part,kind in [('sentry_tripod','base'),('sentry_receiver','head')]:
 model=obj(kind);model['display']=display;save(Path(f'models/item/{part}.json'),model)
ammo='zombierool:block/sentry/'+next(t for _,_,t in materials.values() if t and 'texture' in t)
save(Path('models/item/sentry_ammo.json'),{'textures':{'ammo':ammo,'particle':ammo},'elements':[{'from':[3,2,5],'to':[13,13,11],'faces':{f:{'texture':'#ammo'} for f in ['north','south','east','west','up','down']}}],'display':{'gui':{'rotation':[20,35,0]},'ground':{'scale':[.5,.5,.5]}}})
save(Path('blockstates/deployed_sentry_turret.json'),{'variants':{f'facing={f}':{'model':'zombierool:block/deployed_sentry_turret','y':rot} for f,rot in [('north',0),('east',90),('south',180),('west',270)]}})
print('Converted original source triangles:',counts,'materials:',len(materials),'pivot Y:',round(4.2*scale,4))
for base in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool',ROOT/'build/resources/main/assets/zombierool']:
 for path in list(folder.glob('sentry*'))+list(textureFolder.glob('*.png'))+[A/'models/block/deployed_sentry_turret.json',A/'blockstates/deployed_sentry_turret.json']+list((A/'models/item').glob('sentry*.json')):
  dest=base/path.relative_to(A);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
