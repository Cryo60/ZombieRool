"""Prepare bounded runtime sky assets from the verified original source package."""
from pathlib import Path
import sys,json,struct,gzip,shutil,importlib.util,io,math
ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'build/assets-python'),str(ROOT/'build/audio-python'),str(ROOT/'build/video-python'),str(ROOT/'tools/vendor')]
import numpy as np
from PIL import Image
SOURCE=ROOT/'assets-source/26.7/sky-originals';OUT=ROOT/'src/main/resources/assets/zombierool';folder=OUT/'sky';folder.mkdir(exist_ok=True)
textures=json.loads((SOURCE/'textures.json').read_text(encoding='utf8'));textureLookup={(r['game'],r['name']):r for r in textures};materials=json.loads((SOURCE/'materials.json').read_text(encoding='utf8'));materialLookup={(r['game'],r['name']):r for r in materials}
selected={'cloud':'maps__zombie__fx_zmb_nuke_linger_core.json','eclipse':'maps__zombie_temple__fx_ztem_moon_eclipse.json','meteor':'maps__zombie_highrise__fx_highrise_meteor_trail_lg.json','meteorImpact':'maps__zombie_highrise__fx_highrise_meteor_impact_lg.json','tracers':'maps__zombie_tomb__fx_tomb_sky_plane_tracers_c.json','planeTrail':'maps__zombie_tomb__fx_tomb_sky_plane_trail_c.json'}
effects={k:json.loads((SOURCE/'effects'/v).read_text(encoding='utf8')) for k,v in selected.items()};wanted=set()
for effect in effects.values():
 for e in effect['elements']:
  if e['type']<=6:
   for mat in e['visuals']:
    row=materialLookup[(effect['game'],mat.lstrip(','))]
    wanted.update((effect['game'],name) for name in row['colorImages'])
planeImages=['~-gveh_t6_dlc_zm_triplane_body_c','~-gveh_t6_dlc_zm_biplane_body_c','~-gmh6_littlebird_blade_spin_c'];wanted.update(('t6',name) for name in planeImages)
images={game+':'+name:Image.open(SOURCE/'textures'/textureLookup[(game,name)]['file']).convert('RGBA') for game,name in wanted}
# Reproject the visible original Earth disk onto the six faces of a cube.
earth=Image.open(SOURCE/'textures'/textureLookup[('t5','moon_vista_earth_c')]['file']).convert('RGBA');faceSize=192;earthCube=Image.new('RGBA',(faceSize*3,faceSize*2));pixels=earth.load()
def cube(face,u,v):return [(1,v,u),(-1,v,-u),(u,1,v),(u,-1,-v),(u,v,1),(-u,v,-1)][face]
for face in range(6):
 for y in range(faceSize):
  for x in range(faceSize):
   direction=np.array(cube(face,(x+.5)/faceSize*2-1,(y+.5)/faceSize*2-1));direction/=np.linalg.norm(direction)
   # The source shows one hemisphere. Reuse its disk projection for the hidden hemisphere.
   xx=int(np.clip(256+direction[0]*164,90,421));yy=int(np.clip(262-direction[1]*164,96,427));color=pixels[xx,yy]
   earthCube.putpixel((face%3*faceSize+x,face//3*faceSize+y),color)
images['earthCube']=earthCube
# One static atlas keeps depth masking and blend state independent of resource binding caches.
width=4096;positions={};x,y,rowHeight=8,0,8
for key,image in sorted(images.items(),key=lambda entry:(-entry[1].height,-entry[1].width)):
 if x+image.width+4>width:x=0;y+=rowHeight;rowHeight=0
 positions[key]=(x+2,y+2,image.width,image.height);x+=image.width+4;rowHeight=max(rowHeight,image.height+4)
height=1
while height<y+rowHeight:height*=2
assert height<=4096
atlas=Image.new('RGBA',(width,height));atlas.paste((255,255,255,255),(0,0,8,8))
for key,image in images.items():
 xx,yy,w,h=positions[key];atlas.paste(image,(xx,yy));atlas.paste(image.crop((0,0,1,h)).resize((2,h)),(xx-2,yy));atlas.paste(image.crop((w-1,0,w,h)).resize((2,h)),(xx+w,yy));atlas.paste(image.crop((0,0,w,1)).resize((w,2)),(xx,yy-2));atlas.paste(image.crop((0,h-1,w,h)).resize((w,2)),(xx,yy+h))
atlasPath=OUT/'textures/environment/sky_scene_atlas.png';atlas.save(atlasPath);atlasPath.with_suffix('.png.mcmeta').write_text(json.dumps({'texture':{'blur':True,'clamp':True}}),encoding='utf8')
def rect(key):
 xx,yy,w,h=positions[key];return [xx/width,yy/height,w/width,h/height]
for effect in effects.values():
 for e in effect['elements']:
  e['sprites']=[]
  if e['type']<=6:
   for mat in e['visuals']:
    material=materialLookup[(effect['game'],mat.lstrip(','))];name=material['colorImages'][0];state=material['definition'].get('stateBitsTable',material['definition'].get('stateBits',[]))
    if isinstance(state,list) and state and isinstance(state[0],dict):state=state[0]
    else:state={}
    # OAT's material JSON calls this array stateBits.
    if not state:
     for value in material['definition'].values():
      if isinstance(value,list) and value and isinstance(value[0],dict) and 'dstBlendRgb' in value[0]:state=value[0];break
    e['sprites'].append({'rect':rect(effect['game']+':'+name),'additive':state.get('dstBlendRgb')=='one','material':mat.lstrip(',')})
(folder/'original_effects.json').write_text(json.dumps({'effects':effects,'earth':rect('earthCube'),'atlas':[width,height],'aircraftSource':'20 original aircraft; 350 source frames at 30 fps'},separators=(',',':')),encoding='utf8')
# Compile the standard archival GLB into a compact weighted-vertex/pose stream.
spec=importlib.util.spec_from_file_location('air',ROOT/'tools/prepare-original-air-battle.py');air=importlib.util.module_from_spec(spec);spec.loader.exec_module(air);doc,data=air.load(SOURCE/'models/air_battle.glb');skin=doc['skins'][0];nodes=skin['joints'];nodeIndex={n:i for i,n in enumerate(nodes)};parents=[-1]*len(nodes)
for i,node in enumerate(nodes):
 for child in doc['nodes'][node].get('children',[]):
  if child in nodeIndex:parents[nodeIndex[child]]=i
curves={}
for channel in doc['animations'][0]['channels']:
 sample=doc['animations'][0]['samplers'][channel['sampler']];curves[(channel['target']['node'],channel['target']['path'])]=air.accessor(doc,data,sample['output'])
inverse=air.accessor(doc,data,skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
reference=[]
for i,node in enumerate(nodes):
 n=doc['nodes'][node];m=air.cod.transform(n.get('translation',[0,0,0]),n.get('rotation',[0,0,0,1]));reference.append(m if parents[i]<0 else reference[parents[i]]@m)
planeRoots=[i for i,node in enumerate(nodes) if doc['nodes'][node]['name'].startswith(('biplane_','triplane_')) and doc['nodes'][node]['name'].endswith('_jnt')]
center=np.mean([reference[i][:3,3] for i in planeRoots],axis=0);N=np.eye(4);N[:3,:3]*=1/5000;N[:3,3]=-center/5000;Ni=np.linalg.inv(N)
buf=io.BytesIO()
def write(fmt,*values):buf.write(struct.pack('>'+fmt,*values))
sockets=[(i,doc['nodes'][node]['name']) for i,node in enumerate(nodes) if 'smoke' in doc['nodes'][node]['name'] or '_fire' in doc['nodes'][node]['name']]
primitives=[p for mesh in doc['meshes'] for p in mesh['primitives']];write('5I',0x5a525332,len(nodes),len(primitives),350,len(sockets))
write('f',30)
for primitive in primitives:
 attrs=primitive['attributes'];p=air.accessor(doc,data,attrs['POSITION']);normal=air.accessor(doc,data,attrs['NORMAL']);uv=air.accessor(doc,data,attrs['TEXCOORD_0']);bones=air.accessor(doc,data,attrs['JOINTS_0']);weights=air.accessor(doc,data,attrs['WEIGHTS_0']);indices=air.accessor(doc,data,primitive['indices']).reshape(-1)
 material=doc['materials'][primitive['material']]['name'];image=materialLookup[('t6',material)]['colorImages'][0];r=rect('t6:'+image);write('2I',len(p),len(indices));write('4f',*r)
 for pp,nn,tt,bb,ww in zip(p,normal,uv,bones,weights):
  pp=(N@np.append(pp,1))[:3];write('8f',*pp,*nn,*tt)
  influence=[(int(b),float(w)) for b,w in zip(bb,ww) if w>0];write('B',len(influence))
  for b,w in influence:write('Hf',b,w)
 for index in indices:write('I',int(index))
for frame in range(350):
 world=[]
 for i,node in enumerate(nodes):
  n=doc['nodes'][node];t=curves.get((node,'translation'));q=curves.get((node,'rotation'));m=air.cod.transform(t[frame] if t is not None else n.get('translation',[0,0,0]),q[frame] if q is not None else n.get('rotation',[0,0,0,1]));world.append(m if parents[i]<0 else world[parents[i]]@m)
 transforms=N@np.array(world)@inverse@Ni;buf.write(np.asarray(transforms[:,:3,:],dtype='>f4').tobytes())
 # Original animated emitter sockets, in the same normalized scene coordinates.
 sockets=[(i,doc['nodes'][node]['name']) for i,node in enumerate(nodes) if 'smoke' in doc['nodes'][node]['name'] or '_fire' in doc['nodes'][node]['name']]
 for i,name in sockets:
  matrix=N@world[i];write('3f',*matrix[:3,3])
for i,name in sockets:write('B',0 if 'smoke' in name else 1)
(folder/'air_battle.zrs.gz').write_bytes(gzip.compress(buf.getvalue(),compresslevel=9,mtime=0))
print('Sky atlas',atlas.size,'effects',len(effects),'air rig',len(nodes),'bones, 350 frames,',len(sockets),'animated emitter sockets')
for root in ['resources/assets/zombierool','assets/zombierool','build/resources/main/assets/zombierool']:
 for source in [folder/'original_effects.json',folder/'air_battle.zrs.gz',atlasPath,atlasPath.with_suffix('.png.mcmeta')]:
  target=ROOT/root/source.relative_to(OUT);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
