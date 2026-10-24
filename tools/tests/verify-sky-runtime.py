"""Validate the shipped sky atlas and weighted rig, including interleaved-source regression."""
from pathlib import Path
import sys,struct,json,gzip
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np
from io import BytesIO
resource=ROOT/'src/main/resources/assets/zombierool/sky';n=json.loads((resource/'original_effects.json').read_text(encoding='utf8'));assert len(n['effects']['cloud']['elements'])==23 and len(n['effects']['eclipse']['elements'])==15
for fx in n['effects'].values():
 for e in fx['elements']:
  for sprite in e['sprites']:
   u,v,w,h=sprite['rect'];assert min(u,v)>=0 and min(w,h)>0 and u+w<=1 and v+h<=1
f=BytesIO(gzip.decompress((resource/'air_battle.zrs.gz').read_bytes()))
def read(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))
magic,bones,surfaceCount,frames,sockets=read('5I');fps,=read('f');assert (magic,bones,frames,sockets,fps)==(0x5a525332,65,350,24,30)
surfaces=[]
for _ in range(surfaceCount):
 nv,ni=read('2I');rect=read('4f');vertices=[];influences=[]
 for v in range(nv):
  values=read('8f');vertices.append(values);count,=read('B');weights=[read('Hf') for _ in range(count)];assert abs(sum(w for _,w in weights)-1)<.001 and all(b<bones for b,w in weights);influences.append(weights)
 indices=np.array(read(str(ni)+'I'));positions=np.array(vertices)[:,:3];assert np.max(np.linalg.norm(positions[indices[::3]]-positions[indices[1::3]],axis=1))<.2,'Exploded source mesh: check vertex byteStride';surfaces.append((np.array(vertices),influences,indices))
maxEdge=0;minBattleSpan=float('inf')
for frame in range(frames):
 transforms=np.array(read(str(bones*12)+'f')).reshape(bones,3,4);markers=np.array(read(str(sockets*3)+'f'));assert np.all(np.isfinite(transforms)) and np.all(np.isfinite(markers))
 minBattleSpan=min(minBattleSpan,float(np.linalg.norm(np.ptp(markers.reshape(-1,3),axis=0))))
 if frame in [0,97,349]:
  for vertices,weights,indices in surfaces:
   posed=[]
   for v,influence in zip(vertices,weights):posed.append(sum((transforms[b]@np.append(v[:3],1))*w for b,w in influence))
   posed=np.array(posed);maxEdge=max(maxEdge,float(np.max(np.linalg.norm(posed[indices[::3]]-posed[indices[1::3]],axis=1))))
assert maxEdge<.2,'Animated triangles must stay within an aircraft, not cross the entire rig'
assert minBattleSpan>1,'Aircraft and emitters must retain their separate source origins at every frame, including the loop seam'
assert len(f.read(sockets))==sockets and f.read()==b''
shader=json.loads((ROOT/'src/main/resources/assets/zombierool/shaders/core/sky_objects.json').read_text(encoding='utf8'));assert shader['blend']['srcrgb']=='one' and shader['blend']['dstrgb']=='1-srcalpha','FX require explicit premultiplied blending'
print('SKY RUNTIME PASS: original FX curves, atlas bounds, weighted rig, source/animated triangle spans and shader transparency; max aircraft edge',maxEdge)
