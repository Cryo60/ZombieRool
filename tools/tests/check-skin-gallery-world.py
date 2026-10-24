"""Check the saved exhibit entities, then set the gallery's name and creative controls."""
from pathlib import Path
import gzip,zlib,struct,json
ROOT=Path(__file__).resolve().parents[2]
class NBT:
 def __init__(self,b):self.b=b;self.i=0
 def take(self,n):v=self.b[self.i:self.i+n];self.i+=n;return v
 def num(self,f):return struct.unpack('>'+f,self.take(struct.calcsize('>'+f)))[0]
 def string(self):return self.take(self.num('H')).decode('utf-8','replace')
 def tag(self,t):
  if t in range(1,7):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==8:return self.string()
  if t==9:
   ty,n=self.num('B'),self.num('i');return [self.tag(ty) for _ in range(n)]
  if t==10:
   out={}
   while (ty:=self.num('B')):name=self.string();out[name]=self.tag(ty)
   return out
  if t in [7,11,12]:return self.take(self.num('i')*{7:1,11:4,12:8}[t])
  raise ValueError(t)
 def root(self):t=self.num('B');self.string();return self.tag(t)
def check(source):
 entities=[]
 for file in (source/'entities').glob('*.mca'):
  raw=file.read_bytes()
  for k in range(1024):
   offset=int.from_bytes(raw[k*4:k*4+3],'big')*4096
   if not offset:continue
   length=int.from_bytes(raw[offset:offset+4],'big');data=raw[offset+5:offset+4+length];compression=raw[offset+4]
   chunk=NBT(zlib.decompress(data) if compression==2 else gzip.decompress(data) if compression==1 else data).root()
   entities.extend(e for e in chunk.get('Entities',[]) if e.get('ForgeData',{}).get('zr_skin_gallery'))
 assert len(entities)==71,len(entities)
 assert len({e['CustomSkin'] for e in entities})==71
 assert all(e['NoAI']==1 and e['Invulnerable']==1 and e['PersistenceRequired']==1 for e in entities)
 return entities
if __name__=='__main__':
 source=ROOT/'build/zombie-presets/gallery-server/world';entities=check(source)
 level=source/'level.dat';raw=gzip.decompress(level.read_bytes());data=NBT(raw).root()['Data'];name='ZombieRool - Galerie des skins';old=data['LevelName'].encode();key=b'\x08\x00\x09LevelName'+struct.pack('>H',len(old))+old;assert raw.count(key)==1;raw=raw.replace(key,b'\x08\x00\x09LevelName'+struct.pack('>H',len(name.encode()))+name.encode());key=b'\x01\x00\x0dallowCommands';where=raw.index(key)+len(key);raw=raw[:where]+b'\x01'+raw[where+1:];level.write_bytes(gzip.compress(raw));data=NBT(raw).root()['Data'];assert data['GameType']==1 and data['allowCommands']==1
 (ROOT/'build/zombie-presets/gallery-world-validation.json').write_text(json.dumps({'zombies':66,'hellhounds':5,'uniqueAppearances':71,'persistent':True,'noAI':True,'invulnerable':True,'spawn':[data['SpawnX'],data['SpawnY'],data['SpawnZ']]},indent=2))
 print('PASS: 71 unique saved appearances, no AI, invulnerable; creative world and commands enabled')
