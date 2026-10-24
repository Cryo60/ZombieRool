"""Derive coarse, bounded collision bands from the final voxel surface meshes."""
from pathlib import Path
import json,math
ROOT=Path(__file__).resolve().parents[2];step=.6;profiles={}
for p in (ROOT/'src/main/resources/assets/zombierool/scenery/vehicles').glob('*.json'):
 if p.stem.startswith('dca'):continue
 rows={}
 for face in json.loads(p.read_text())['faces']:
  v=face[3:];lo=[min(v[i::3]) for i in range(3)];hi=[max(v[i::3]) for i in range(3)]
  if p.stem.startswith('helicopter') and lo[1]>4:continue
  for y in range(math.floor(lo[1]/step),math.floor((hi[1]-1e-5)/step)+1):
   for z in range(math.floor(lo[2]/step),math.floor((hi[2]-1e-5)/step)+1):
    old=rows.get((y,z),(1e9,-1e9));rows[y,z]=(min(old[0],lo[0]),max(old[1],hi[0]))
 boxes=[]
 for (y,z),(xmin,xmax) in sorted(rows.items()):
  if xmax-xmin>.01:boxes.append([round(xmin,3),round(y*step,3),round(z*step,3),round(xmax,3),round((y+1)*step,3),round((z+1)*step,3)])
 profiles[p.stem]=boxes
out=ROOT/'src/main/resources/data/zombierool/scenery/vehicle_collision.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(profiles,separators=(',',':')))
print('Collision bands:',sum(map(len,profiles.values())),'in',len(profiles),'profiles')
