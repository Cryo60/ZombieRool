"""Retain the original animated sky rig as a standard, textured GLB.
Only the coordinate-system conversion performed by OAT is retained; no substitute motion.
"""
from pathlib import Path
import sys,json,struct,importlib.util,hashlib,copy
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'build/audio-python'),str(ROOT/'build/video-python'),str(ROOT/'tools/vendor')]
import numpy as np
spec=importlib.util.spec_from_file_location('cod_import',ROOT/'tools/import-boss-models.py');cod=importlib.util.module_from_spec(spec);spec.loader.exec_module(cod)
OUT=ROOT/'assets-source/26.7/sky-originals';(OUT/'models').mkdir(parents=True,exist_ok=True)
def load(path):
 raw=path.read_bytes();length=struct.unpack_from('<I',raw,12)[0];document=json.loads(raw[20:20+length]);offset=20+length;size=struct.unpack_from('<I',raw,offset)[0];return document,bytearray(raw[offset+8:offset+8+size])
def write(path,doc,data):
 document=json.dumps(doc,separators=(',',':')).encode();document+=b' '*((-len(document))%4);data+=bytes((-len(data))%4);path.write_bytes(struct.pack('<III',0x46546c67,2,12+8+len(document)+8+len(data))+struct.pack('<II',len(document),0x4e4f534a)+document+struct.pack('<II',len(data),0x004e4942)+data)
def accessor(doc,data,index):
 a=doc['accessors'][index];view=doc['bufferViews'][a['bufferView']];offset=view.get('byteOffset',0)+a.get('byteOffset',0);types={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'};sizes={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16};dtype=np.dtype(types[a['componentType']]);components=sizes[a['type']];stride=view.get('byteStride',components*dtype.itemsize);return np.ndarray((a['count'],components),dtype=dtype,buffer=data,offset=offset,strides=(stride,dtype.itemsize)).copy()

def restore_plane_origins(frames,names,parents,bind):
 # Relative root tracks contain displacements; child sockets/rotors still contain local positions.
 # The generic actor decoder only handles j_mainroot, so keep this correction specific to this rig.
 frames=frames.copy();plane_roots={i for i,n in enumerate(names) if n.startswith(('biplane_','triplane_')) and n.endswith('_jnt')}
 for i in range(len(names)):
  root=i
  while root>=0 and root not in plane_roots:root=parents[root]
  if root>=0:frames[:,i,:3,3]+=bind[root,:3,3]
 return frames

def main():
 path=ROOT/'build/sky-originals/models/t6/zm_tomb/model_export/fxanim_zom_tomb_dogfights_mod_lod0.glb';anim=path.parents[1]/'xanim/fxanim_zom_tomb_dogfights_anim';doc,data=load(path);skin=doc['skins'][0];jointNodes=skin['joints'];names=[doc['nodes'][i]['name'] for i in jointNodes];parents=[-1]*len(names)
 for i,node in enumerate(jointNodes):
  for child in doc['nodes'][node].get('children',[]):
   if child in jointNodes:parents[jointNodes.index(child)]=i
 local=[]
 for node in jointNodes:
  n=doc['nodes'][node];local.append(cod.transform(n.get('translation',[0,0,0]),n.get('rotation',[0,0,0,1])))
 local=np.array(local);C=np.eye(4);C[:3,:3]=[[1,0,0],[0,0,1],[0,-1,0]];local[0]=np.linalg.inv(C)@local[0];bind=cod.world(local,parents)
 inverseBind=accessor(doc,data,skin['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1);expected=np.linalg.inv(C@bind)
 assert np.max(np.abs(inverseBind-expected))<.5,(np.max(np.abs(inverseBind-expected)))
 fps,frames,notes,tracks,motion=cod.compiled_anim(anim,names,parents,local,True,True,True)
 frames=restore_plane_origins(frames,names,parents,bind)
 # Encode source frame poses as local TRS animation; use the original root axis conversion.
 posed=[]
 for frame in frames:
  loc=np.array([np.linalg.inv(frame[p])@m if p>=0 else C@m for m,p in zip(frame,parents)]);posed.append(loc)
 posed=np.array(posed)
 def add_view(payload):
  data.extend(bytes((-len(data))%4));offset=len(data);data.extend(payload);index=len(doc['bufferViews']);doc['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':len(payload)});return index
 def add_accessor(array,kind):
  array=np.asarray(array,dtype='<f4');view=add_view(array.tobytes());index=len(doc['accessors']);row={'bufferView':view,'componentType':5126,'count':len(array),'type':kind}
  if kind=='SCALAR':row.update(min=[float(array.min())],max=[float(array.max())])
  doc['accessors'].append(row);return index
 def quaternion(matrix):
  # Stable rotation-matrix to quaternion, xyzw.
  m=matrix[:3,:3];q=np.empty(4);trace=np.trace(m)
  if trace>0:
   s=np.sqrt(trace+1)*2;q[:3]=[(m[2,1]-m[1,2])/s,(m[0,2]-m[2,0])/s,(m[1,0]-m[0,1])/s];q[3]=s/4
  else:
   i=int(np.argmax(np.diag(m)));j=(i+1)%3;k=(i+2)%3;s=np.sqrt(max(0,1+m[i,i]-m[j,j]-m[k,k]))*2;q[i]=s/4;q[j]=(m[j,i]+m[i,j])/s;q[k]=(m[k,i]+m[i,k])/s;q[3]=(m[k,j]-m[j,k])/s
  return q/np.linalg.norm(q)
 time=add_accessor(np.arange(len(frames))/fps,'SCALAR');animation={'name':'original_dogfight','samplers':[],'channels':[]}
 for bone,node in enumerate(jointNodes):
  if names[bone] not in tracks:continue
  rotations=np.array([quaternion(m) for m in posed[:,bone]])
  for i in range(1,len(rotations)):
   if rotations[i]@rotations[i-1]<0:rotations[i]*=-1
  for target,values,kind in [('translation',posed[:,bone,:3,3],'VEC3'),('rotation',rotations,'VEC4')]:
   index=len(animation['samplers']);animation['samplers'].append({'input':time,'output':add_accessor(values,kind),'interpolation':'LINEAR'});animation['channels'].append({'sampler':index,'target':{'node':node,'path':target}})
 doc['animations']=[animation]
 textures=json.loads((OUT/'textures.json').read_text(encoding='utf8'));lookup={r['name']:r for r in textures if r['game']=='t6'}
 for image in doc['images']:
  name=Path(image['uri']).stem
  if name=='$identitynormalmap':
   # A neutral normal map is unnecessary for archival mesh fidelity; strip absent normals below.
   continue
  if name in lookup:
   png=(OUT/'textures'/lookup[name]['file']).read_bytes();image.clear();image.update(bufferView=add_view(png),mimeType='image/png')
 for material in doc['materials']:material.pop('normalTexture',None)
 # Drop unused image/texture records to keep the GLB self-contained.
 used=sorted({m['pbrMetallicRoughness']['baseColorTexture']['index'] for m in doc['materials']});oldTextures=doc['textures'];oldImages=doc['images'];newTextures=[];newImages=[]
 for newIndex,index in enumerate(used):
  texture=oldTextures[index].copy();newImages.append(oldImages[texture['source']]);texture['source']=newIndex;newTextures.append(texture)
  for material in doc['materials']:
   if material['pbrMetallicRoughness']['baseColorTexture']['index']==index:material['pbrMetallicRoughness']['baseColorTexture']['index']=newIndex
 doc['textures']=newTextures;doc['images']=newImages;doc['buffers'][0]['byteLength']=len(data)
 doc['extras']={'sourceModel':path.stem,'sourceAnimation':anim.name,'originalFps':fps,'originalFrames':len(frames),'notes':notes,'rootMotion':motion.tolist(),'method':'Original weighted meshes plus original relative plane tracks applied to distinct reference origins; child socket positions unchanged.'}
 write(OUT/'models/air_battle.glb',doc,data)
 # Keep the compact, untouched original animation as the authoritative source.
 (OUT/'models/air_battle.xanim').write_bytes(anim.read_bytes());(OUT/'models/air_battle-source.glb').write_bytes(path.read_bytes())
 print('Original dogfight:',len(names),'joints,',len(frames),'frames at',fps,'fps;',len(animation['channels']),'channels; bound matrix max error',float(np.max(np.abs(inverseBind-expected))))
 report={'modelSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'animationSha256':hashlib.sha256(anim.read_bytes()).hexdigest(),'joints':len(names),'frames':len(frames),'fps':fps,'durationSeconds':(len(frames)-1)/fps,'planes':sum(n.startswith(('biplane_','triplane_')) and n.endswith('_jnt') for n in names),'rootMotionMax':float(np.max(np.abs(motion))),'bindMatrixError':float(np.max(np.abs(inverseBind-expected)))}
 (OUT/'models/air_battle-provenance.json').write_text(json.dumps(report,indent=2),encoding='utf8')
if __name__=='__main__':main()
