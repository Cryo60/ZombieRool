"""Validate extracted FX, textures and the archival animated GLB against independent source reads."""
from pathlib import Path
import sys,json,hashlib,math,struct,importlib.util
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'build/assets-python'),str(ROOT/'build/audio-python'),str(ROOT/'build/video-python'),str(ROOT/'tools/vendor')]
import numpy as np
from PIL import Image
OUT=ROOT/'assets-source/26.7/sky-originals';checks=0

def check(condition,label):
 global checks
 if not condition:raise AssertionError(label)
 checks+=1

def main():
 effects=[json.loads(p.read_text(encoding='utf8')) for p in (OUT/'effects').glob('*.json')];materials={(r['game'],r['name']):r for r in json.loads((OUT/'materials.json').read_text(encoding='utf8'))};textures=json.loads((OUT/'textures.json').read_text(encoding='utf8'));textureNames={(r['game'],r['name']) for r in textures}
 check(len(effects)==28,'All selected effects and referenced runners are retained')
 for fx in effects:
  check(len(fx['elements'])==fx['looping']+fx['oneShot']+fx['emission'],'Effect element counts: '+fx['name'])
  for e in fx['elements']:
   check(len(e['visuals'])==e['visualCount'],'Visual references retained')
   check(len(e['visualSamples']) in (0,e['visCount']+1),'Every original visual sample retained')
   check(len(e['velocitySamples']) in (0,e['velCount']+1),'Every original velocity sample retained')
   check(not e.get('unresolvedInactiveReferences'),'No unresolved effect pointers in exported data')
   if e['type']<=6:
    for name in e['visuals']:
     mat=materials.get((fx['game'],name.lstrip(',')));check(mat is not None and 'definition' in mat,'Original material resolved: '+name)
     for image in mat['images']:check((fx['game'],image) in textureNames,'Original texture resolved: '+image)
   if 'trail' in e:check(all(i<len(e['trail']['vertices']) for i in e['trail']['indices']),'Trail geometry indices are valid')
 for folder in ['validation-nuketown','validation-eclipse']:
  for p in (ROOT/'build/sky-originals'/folder).glob('*.json'):
   check(json.loads(p.read_text(encoding='utf8'))==json.loads((OUT/'effects'/p.name).read_text(encoding='utf8')),'Independent subprocess exports match exactly: '+p.stem)
 for row in textures:
  source=OUT/'textures'/Path(row['file']).with_suffix('.iwi');check(hashlib.sha256(source.read_bytes()).hexdigest()==row['sha256'],'Original IWI SHA-256 preserved')
  a=Image.open(OUT/'textures'/row['file']).convert('RGBA');b=Image.open(ROOT/'build/sky-originals/verification-textures'/Path(row['file']).with_suffix('.dds')).convert('RGBA');check(a.size==b.size and a.tobytes()==b.tobytes(),'PNG pixels match independent OAT conversion: '+row['name'])
 spec=importlib.util.spec_from_file_location('air_archive',ROOT/'tools/prepare-original-air-battle.py');air=importlib.util.module_from_spec(spec);spec.loader.exec_module(air)
 doc,data=air.load(OUT/'models/air_battle.glb');check(all('bufferView' in image and 'uri' not in image for image in doc['images']),'Animated GLB embeds its original aircraft textures')
 animation=doc['animations'][0];check(len(animation['channels'])==128,'Original rig animation channels retained')
 for primitive in [p for m in doc['meshes'] for p in m['primitives']]:
  a=doc['accessors'][primitive['attributes']['POSITION']];vertices=air.accessor(doc,data,primitive['attributes']['POSITION']);check(np.max(np.abs(vertices.min(0)-a['min']))<.002 and np.max(np.abs(vertices.max(0)-a['max']))<.002,'Interleaved original mesh positions match declared bounds')
  uv=air.accessor(doc,data,primitive['attributes']['TEXCOORD_0']);check(np.all(np.isfinite(uv)) and np.max(np.abs(uv))<4,'Original texture UVs decode with the source vertex stride')

 sourceDoc,sourceData=air.load(OUT/'models/air_battle-source.glb');skin=doc['skins'][0];nodes=skin['joints'];names=[doc['nodes'][i]['name'] for i in nodes];parents=[-1]*len(nodes)
 for i,node in enumerate(nodes):
  for child in doc['nodes'][node].get('children',[]):
   if child in nodes:parents[nodes.index(child)]=i
 local=np.array([air.cod.transform(sourceDoc['nodes'][node].get('translation',[0,0,0]),sourceDoc['nodes'][node].get('rotation',[0,0,0,1])) for node in nodes]);C=np.eye(4);C[:3,:3]=[[1,0,0],[0,0,1],[0,-1,0]];local[0]=np.linalg.inv(C)@local[0]
 fps,frames=air.cod.compiled_anim(OUT/'models/air_battle.xanim',names,parents,local);frames=air.restore_plane_origins(frames,names,parents,air.cod.world(local,parents));check(fps==30 and len(frames)==350,'Original 350-frame, 30-fps animation decodes')
 planeRoots=[i for i,name in enumerate(names) if name.startswith(('biplane_','triplane_')) and name.endswith('_jnt')]
 reference=air.cod.world(local,parents)
 check(np.max(np.abs(frames[0,planeRoots,:3,3]-reference[planeRoots,:3,3]))<.5,'Relative aircraft tracks start at their distinct reference positions')
 centers=frames[:,planeRoots,:3,3]
 check(np.min(np.linalg.norm(np.ptp(centers,axis=1),axis=1))>8000,'The complete battle never collapses to one center, including the loop seam')
 arrays={}
 for channel in animation['channels']:
  sample=animation['samplers'][channel['sampler']];values=air.accessor(doc,data,sample['output']);times=air.accessor(doc,data,sample['input']);check(len(values)==350 and np.all(np.isfinite(values)) and np.all(np.diff(times[:,0])>0),'Animation samples finite and ordered');arrays[(channel['target']['node'],channel['target']['path'])]=values
 error=0
 for frame in range(0,len(frames),7):
  poses=[]
  for i,node in enumerate(nodes):
   definition=doc['nodes'][node];position=arrays.get((node,'translation'));rotation=arrays.get((node,'rotation'));m=air.cod.transform(position[frame] if position is not None else definition.get('translation',[0,0,0]),rotation[frame] if rotation is not None else definition.get('rotation',[0,0,0,1]));poses.append(m if parents[i]<0 else poses[parents[i]]@m)
  error=max(error,float(np.max(np.abs(np.array(poses)-C@frames[frame]))))
 check(error<.05,'Animated GLB matches original joint transforms within 0.05 source units')
 earth=json.loads((OUT/'models/earth-provenance.json').read_text(encoding='utf8'));check(earth['earth_original.glb']['vertices']==4 and earth['earth_original.glb']['triangles']==2,'Original Earth is a textured card; no invented source sphere')
 report={'checks':checks,'effects':len(effects),'elements':sum(len(e['elements']) for e in effects),'textures':len(textures),'pixelsVerified':sum(r['width']*r['height'] for r in textures),'aircraft':20,'frames':350,'fps':fps,'animationJointErrorSourceUnits':error}
 (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report,indent=2));print('ORIGINAL SKY EXTRACTION PASS')
if __name__=='__main__':main()
