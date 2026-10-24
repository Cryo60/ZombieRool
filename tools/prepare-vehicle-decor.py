"""Convert selected local CoD vehicle meshes/diffuse maps into bounded voxel surface meshes.
No whole-game texture dump, runtime triangle voxelization or ticking vehicle entities.
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import sys,struct,json,math
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools/vendor'))
from PyCoD import Model
TEMP=Path('C:/Users/ecorange1/AppData/Local/Temp/ZombieRool-vehicle-decor')
DEST=ROOT/'src/main/resources/assets/zombierool/scenery/vehicles';DEST.mkdir(parents=True,exist_ok=True)
SOURCES={
 'tank_legacy':('ber2','vehicle_rus_tracked_t34_wet','vehicle_rus_tracked_t34_dmg_wet'),
 'tank_modern':('khe','t5_veh_tank_t55','t5_veh_tank_t55_dead'),
 'truck_legacy':('pel1','vehicle_jap_wheeled_type94','vehicle_jap_wheeled_type94_dmg'),
 'truck_modern':('cuba','t5_veh_truck_gaz63','t5_veh_truck_gaz63_dead'),
 'plane_legacy':('pel1','vehicle_usa_aircraft_f4ucorsair_dist',None),
 'plane_modern':('cuba','t5_veh_jet_mig17_gear',None),
 'helicopter_modern':('khe','t5_veh_helo_huey_usmc',None),
 'dca_quad':('cuba','vehicle_zpu4','vehicle_zpu4_burn')}
ARCHIVES={};TEXTURES={};manifest=[]
for game,folder in [('t4','D:/Jeux/Plutonium/pluto_t4_full_game/main'),('t5','D:/Jeux/pluto_t5_full_game/main')]:
 for p in Path(folder).glob('*.iwd'):
  z=ZipFile(p)
  for n in z.namelist():
   if n.endswith('.iwi'):ARCHIVES[game,Path(n).stem]=(z,n)
def decode(data):
 assert data[:3]==b'IWi';fmt=data[4];w,h=struct.unpack_from('<HH',data,6)
 if fmt in (11,12,13):
  size=((w+3)//4)*((h+3)//4)*(8 if fmt==11 else 16)
  header=b'DDS '+struct.pack('<7I',124,0x81007,h,w,size,0,1)+bytes(44)+struct.pack('<II4s5I',32,4,{11:b'DXT1',12:b'DXT3',13:b'DXT5'}[fmt],0,0,0,0,0)+struct.pack('<5I',0x1000,0,0,0,0)
  return np.asarray(Image.open(BytesIO(header+data[-size:])).convert('RGBA'))
 if fmt in (1,2):
  bpp=4 if fmt==1 else 3;return np.asarray(Image.frombytes('RGBA' if bpp==4 else 'RGB',(w,h),data[-w*h*bpp:],'raw','BGRA' if bpp==4 else 'BGR').convert('RGBA'))
 if fmt==4:return np.asarray(Image.frombytes('L',(w,h),data[-w*h:]).convert('RGBA'))
 raise ValueError(('unsupported iwi',fmt,w,h))
def texture(game,name):
 key=game,name
 if key not in TEXTURES:
  if key not in ARCHIVES:raise FileNotFoundError(f'{game}:{name}')
  z,n=ARCHIVES[key];TEXTURES[key]=decode(z.read(n))
 return TEXTURES[key]
def voxels(zone,name,pitch):
 folder=TEMP/zone/'model_export';files=sorted(folder.glob(name+'_lod*.xmodel_export'))
 if not files:raise FileNotFoundError(name)
 # LOD1 retains the outline and equipment while skipping invisible bolts.
 path=next((p for p in files if '_lod1.' in p.name),files[0]);model=Model.FromFile_Raw(str(path));game='t4' if zone in ['ber2','pel1'] else 't5'
 positions=np.array([v.offset for mesh in model.meshes for v in mesh.verts]);lo=positions.min(axis=0);hi=positions.max(axis=0);offset=np.array([(lo[0]+hi[0])/2,(lo[1]+hi[1])/2,lo[2]])
 points={};missing=set()
 for mesh in model.meshes:
  for face in mesh.faces:
   material=model.materials[face.material_id];imageName=Path(material.images.get('color','').replace('\\','/')).stem
   try:tex=texture(game,imageName)
   except (FileNotFoundError,ValueError):
    missing.add(imageName);tex=np.array([[[74,88,70,255]]],dtype=np.uint8)
   vv=np.array([mesh.verts[v.vertex].offset for v in face.indices]);vv=(vv-offset)/40;vv=vv[:,[1,2,0]];vv[:,0]*=-1
   uv=np.array([v.uv for v in face.indices]);length=max(np.linalg.norm(vv[(i+1)%3]-vv[i]) for i in range(3));steps=max(1,min(160,math.ceil(length/pitch*1.6)))
   a,b=np.meshgrid(np.arange(steps+1)/steps,np.arange(steps+1)/steps);valid=a+b<=1;a=a[valid];b=b[valid];weights=np.stack([1-a-b,a,b],axis=1);p=weights@vv;u=weights@uv
   h,w=tex.shape[:2];colors=tex[(u[:,1]%1*(h-1)).astype(int),(u[:,0]%1*(w-1)).astype(int)];cells=np.floor(p/pitch).astype(int)
   for cell,color in zip(cells,colors):
    if color[3]<100:continue
    key=tuple(cell);r=points.get(key)
    if r is None:points[key]=[int(color[0]),int(color[1]),int(color[2]),1]
    else:r[0]+=int(color[0]);r[1]+=int(color[1]);r[2]+=int(color[2]);r[3]+=1
 colors={key:tuple(int(max(16,min(240,round(v/c[3]/24)*24))) for v in c[:3]) for key,c in points.items()}
 print(name,'cells',len(colors),'missing',sorted(missing),flush=True);return colors,path

def surface(cells,pitch):
 faces=[]
 # Greedy rectangles on exposed cell sides; no interior cube faces.
 for axis in range(3):
  u=(axis+1)%3;v=(axis+2)%3
  for side in [-1,1]:
   planes={}
   for xyz,color in cells.items():
    other=list(xyz);other[axis]+=side
    if tuple(other) in cells:continue
    planes.setdefault(xyz[axis]+(side>0),{})[xyz[u],xyz[v]]=color
   for level,plane in planes.items():
    while plane:
     (x,y),color=next(iter(plane.items()));w=1
     while plane.get((x+w,y))==color:w+=1
     h=1
     while all(plane.get((xx,y+h))==color for xx in range(x,x+w)):h+=1
     for xx in range(x,x+w):
      for yy in range(y,y+h):del plane[xx,yy]
     corners=[]
     for xx,yy in [(x,y),(x+w,y),(x+w,y+h),(x,y+h)]:
      p=[0,0,0];p[axis]=level*pitch;p[u]=xx*pitch;p[v]=yy*pitch;corners.append([round(k,4) for k in p])
     if side<0:corners.reverse()
     faces.append([axis,side,(color[0]<<16)|(color[1]<<8)|color[2],*sum(corners,[])])
 return faces

def save(key,cells,pitch,path,state):
 faces=surface(cells,pitch);data={'pitch':pitch,'faces':faces};file=DEST/(key+'_'+state+'.json');file.write_text(json.dumps(data,separators=(',',':')))
 manifest.append({'id':key+'_'+state,'source':path.name,'faces':len(faces),'bytes':file.stat().st_size});print('SAVED',file.name,len(faces),flush=True)
for key,(zone,name,damaged) in SOURCES.items():
 pitch=.16 if key.startswith(('tank','truck','dca')) else .2
 cells,path=voxels(zone,name,pitch)
 if key.startswith('helicopter'):
  # Main/tail rotor FX are separate source objects, absent from the hull export.
  # Block-style stationary blades keep the parked decoration complete.
  def box(x,y,z,w,h,d,color):
   for xx in range(math.floor(x/pitch),math.ceil((x+w)/pitch)):
    for yy in range(math.floor(y/pitch),math.ceil((y+h)/pitch)):
     for zz in range(math.floor(z/pitch),math.ceil((z+d)/pitch)):cells[xx,yy,zz]=color
  box(-.2,3.8,2.5,.4,.6,.4,(72,80,72));box(-5.8,4.4,2.5,11.6,.08,.22,(48,56,48));box(-.11,4.4,-3.2,.22,.08,11.6,(48,56,48))
  box(.15,2.5,-6.7,.08,.18,1.6,(48,56,48));box(.15,1.8,-6,.08,1.6,.18,(48,56,48))
 save(key,cells,pitch,path,'normal')
 if damaged:
  broken,bpath=voxels(zone,damaged,pitch)
 else:
  # Remove exposed wing/rotor tips and hull panels, leaving a clearly damaged silhouette.
  xs=[k[0] for k in cells];ys=[k[1] for k in cells];zs=[k[2] for k in cells];xmax=max(xs);ymax=max(ys);zmin=min(zs)
  broken={k:tuple(int(c*.65) for c in color) for k,color in cells.items() if not(k[0]>xmax*.55 and k[1]>ymax*.25) and not(k[2]<zmin*.75 and k[1]>ymax*.5)};bpath=path
 save(key,broken,pitch,bpath,'broken')
(ROOT/'tools/vehicle-decor-sources.json').write_text(json.dumps({'sourceGames':'Local T4/T5 installations; original geometry/diffuse converted offline','entries':manifest},indent=2))
for z in {id(v[0]):v[0] for v in ARCHIVES.values()}.values():z.close()

# Transparent inventory thumbnails from the final converted surfaces.
from PIL import ImageDraw
for vehicle in ['tank','plane','helicopter','truck']:
 key=vehicle+('_modern' if vehicle=='helicopter' else '_legacy')
 faces=json.loads((DEST/(key+'_normal.json')).read_text())['faces'];vertices=np.array([f[3:] for f in faces]).reshape(-1,3)
 angle=.65;rot=np.array([[math.cos(angle),0,-math.sin(angle)],[0,1,0],[math.sin(angle),0,math.cos(angle)]])
 v=vertices@rot.T;p=np.column_stack((v[:,0],-v[:,1]+v[:,2]*.4));lo=p.min(axis=0);hi=p.max(axis=0);scale=116/max(hi-lo);center=(lo+hi)/2
 image=Image.new('RGBA',(128,128));draw=ImageDraw.Draw(image)
 for j in sorted(range(len(faces)),key=lambda j:np.mean(v[j*4:j*4+4,2])):
  f=faces[j];normal=np.zeros(3);normal[f[0]]=f[1];normal=rot@normal
  if normal[2]>.4:continue
  color=np.array([(f[2]>>16)&255,(f[2]>>8)&255,f[2]&255]);color=np.clip(color*(.62+.38*max(0,normal[1])+.16*max(0,-normal[0])),0,255).astype(int)
  pts=(p[j*4:j*4+4]-center)*scale+64;draw.polygon([tuple(v) for v in pts],fill=tuple(color)+(255,))
 image.save(ROOT/'src/main/resources/assets/zombierool/textures/item'/('decor_'+vehicle+'.png'))
