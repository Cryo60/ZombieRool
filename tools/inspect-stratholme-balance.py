"""Read-only compact audit of saved spawners and interactive blocks."""
from pathlib import Path
import gzip,io,struct,zlib,json,collections
WORLD=Path.home()/'curseforge/minecraft/Instances/ZombieRool (no mods)/saves/Stratholme'
OUT=Path(__file__).resolve().parents[1]/'build/stratholme-balance';OUT.mkdir(exist_ok=True)
def read_nbt(data):
 f=io.BytesIO(data)
 def num(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
 def string():return f.read(num('H')).decode('utf8',errors='replace')
 def tag(t):
  if t in (1,2,3,4,5,6):return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return list(f.read(num('i')))
  if t==8:return string()
  if t==9:
   kind=num('b');return [tag(kind) for _ in range(num('i'))]
  if t==10:
   result={}
   while (kind:=num('b'))!=0:
    key=string();result[key]=tag(kind)
   return result
  if t in (11,12):return [num('i' if t==11 else 'q') for _ in range(num('i'))]
  raise ValueError(t)
 t=num('b');string();return tag(t)
def saved(name):return read_nbt(gzip.decompress((WORLD/'data'/name).read_bytes())).get('data',{})
actors=saved('zombierool_map_actors.dat');config=saved('zombierool_world_config.dat')
markers=[]
for key,rule in actors.get('markers',{}).items():
 n=int(key);x=n>>38;z=n>>12&0x3ffffff;y=n&0xfff
 if z>=1<<25:z-=1<<26
 if y>=1<<11:y-=1<<12
 markers.append(dict(x=x,y=y,z=z,**rule))
blocks=[]
for region in (WORLD/'region').glob('*.mca'):
 raw=region.read_bytes()
 for i in range(1024):
  loc=int.from_bytes(raw[i*4:i*4+4],'big');offset=loc>>8
  if not offset:continue
  start=offset*4096;length=int.from_bytes(raw[start:start+4],'big');codec=raw[start+4];data=raw[start+5:start+4+length]
  if codec==2:data=zlib.decompress(data)
  elif codec==1:data=gzip.decompress(data)
  elif codec!=3:continue
  chunk=read_nbt(data)
  for b in chunk.get('block_entities',[]):
   if b.get('id','').startswith('zombierool:') and any(word in b['id'] for word in ('weapon','buy','actor','obstacle','door','music')):blocks.append(b)
profiles={key:{k:v for k,v in p.items() if k in ('name','baseEntity','health','healthMode','damage','speed','speedMode','aggroRadius','leashRadius','wanderRadius','behavior','faction','countsForWave','boss')} for key,p in actors.get('profiles',{}).items()}
report={'markers':markers,'profiles':profiles,'config':config,'blocks':blocks}
(OUT/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print('Markers:',len(markers),'total configured batch:',sum(r.get('batch',1) for r in markers))
print('Profiles:',dict(collections.Counter(r.get('profile') for r in markers)))
print('Marker settings:',dict(collections.Counter((r.get('behavior'),r.get('aggroRadius'),r.get('leashRadius'),r.get('batch'),r.get('trigger')) for r in markers)))
print('Config fields:',', '.join(config))
print('Interactive blocks:',dict(collections.Counter(b['id'] for b in blocks)))
for b in blocks:
 if not 'actor' in b['id']:print(json.dumps(b,ensure_ascii=False)[:1800])
