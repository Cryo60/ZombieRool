"""Read only named textures from original IWD/IPAK archives; never unpacks a whole archive.
IPAK layout/hash: OpenAssetTools src/ObjLoading/ObjContainer/IPak.
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import sys,struct,json,zlib,hashlib
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'build/sky-originals/deps'))
import lzokay
from PIL import Image

def name_hash(name):
 h=0
 for ch in name.encode():h=((h*33)^(ch|32))&0xffffffff
 return h
class Ipak:
 def __init__(self,path):
  self.path=path;self.entries={}
  with path.open('rb') as f:
   magic,version,size,count=struct.unpack('<4I',f.read(16));assert magic==0x4950414b and version==0x50000
   sections=[struct.unpack('<4I',f.read(16)) for _ in range(count)];self.data=next(row[1] for row in sections if row[0]==2)
   index=next(row for row in sections if row[0]==1);f.seek(index[1])
   for _ in range(index[3]):
    dh,nh,offset,size=struct.unpack('<4I',f.read(16));self.entries.setdefault(nh,[]).append((dh,offset,size))
 def read(self,name):
  matches=self.entries.get(name_hash(name),[])
  for dh,offset,size in matches:
   with self.path.open('rb') as f:
    pos=self.data+offset;end=pos+size;output=bytearray()
    while pos<end:
     pos=(pos+127)//128*128;f.seek(pos);header=f.read(128)
     if len(header)<128:raise ValueError('Truncated IPAK block')
     commands=struct.unpack('<32I',header);count=commands[0]>>24;position=commands[0]&0xffffff;assert count<=31;pos+=128
     for command in commands[1:1+count]:
      length=command&0xffffff;mode=command>>24;f.seek(pos);data=f.read(length);pos+=length
      if mode==0:output.extend(data)
      elif mode==1:
       # lzokay pads its returned buffer: recover the exact output length,
       # otherwise padding between commands corrupts the mip chain.
       low,high=0,0x8000
       while low<high:
        mid=(low+high)//2
        try:lzokay.decompress(data,mid);high=mid
        except lzokay.OutputOverrunError:low=mid+1
       output.extend(lzokay.decompress(data,low))
      elif mode!=0xcf:raise ValueError('Unsupported compression '+str(mode))
    result=bytes(output)
    if result[:4]==b'IWi\x1b':
     exact=struct.unpack_from('<I',result,32)[0]
     if 48<=exact<=len(result):result=result[:exact]
    if (zlib.crc32(result)&0x1fffffff)!=dh:raise ValueError('IPAK CRC does not match: '+name+' '+str((len(result),hex(dh),hex(zlib.crc32(result)),result[:48].hex())))
    return result
  return None

def decode_iwi(data):
 assert data[:3]==b'IWi';version=data[3];fmt=data[4];w,h=struct.unpack_from('<HH',data,6)
 if version==13:pass
 elif version==27:
  # T6 uses a larger header; the largest mip is still stored at the end.
  pass
 else:raise ValueError('IWI version '+str(version))
 if fmt in (11,12,13):
  fourcc={11:b'DXT1',12:b'DXT3',13:b'DXT5'}[fmt];size=((w+3)//4)*((h+3)//4)*(8 if fmt==11 else 16)
  header=b'DDS '+struct.pack('<7I',124,0x81007,h,w,size,0,1)+bytes(44)+struct.pack('<II4s5I',32,4,fourcc,0,0,0,0,0)+struct.pack('<5I',0x1000,0,0,0,0)
  return Image.open(BytesIO(header+data[-size:])).convert('RGBA')
 if fmt==14:
  size=((w+3)//4)*((h+3)//4)*16
  header=b'DDS '+struct.pack('<7I',124,0x81007,h,w,size,0,1)+bytes(44)+struct.pack('<II4s5I',32,4,b'DX10',0,0,0,0,0)+struct.pack('<5I',0x1000,0,0,0,0)+struct.pack('<5I',83,3,0,1,0)
  return Image.open(BytesIO(header+data[-size:])).convert('RGBA')
 if fmt in (1,2):
  bpp=4 if fmt==1 else 3;return Image.frombytes('RGBA' if bpp==4 else 'RGB',(w,h),data[-w*h*bpp:],'raw','RGBA' if bpp==4 else 'RGB').convert('RGBA')
 if fmt==4:return Image.frombytes('L',(w,h),data[-w*h:]).convert('RGBA')
 if fmt==5:
  channel=Image.frombytes('L',(w,h),data[-w*h:]);im=Image.new('RGBA',(w,h),'white');im.putalpha(channel);return im
 raise ValueError('IWI format '+str(fmt))

def main():
 raw=ROOT/'build/sky-originals';materials={}
 for base in [raw/'materials',raw/'t6']:
  for p in base.rglob('*.json'):
   if p.parent.name!='materials' and 'materials' not in p.parts:continue
   try:d=json.loads(p.read_text());game=d.get('_game');
   except:continue
   if d.get('_type')=='material':
    j=p.parts.index('materials',p.parts.index('sky-originals')+2) if p.parts.count('materials')>1 else p.parts.index('materials');rel='/'.join(p.parts[j+1:])[:-5];materials[(game,rel)]=(p,d)
 wanted={};effects=[]
 for folder in ['fx-nuketown','fx-eclipse','fx-origins','fx-highrise']:
  for p in (raw/folder).glob('*.json'):
   d=json.loads(p.read_text());effects.append((p,d));game=d.get('game','t5' if folder=='fx-eclipse' else 't6')
   for e in d['elements']:
    if e['type']<=6:
     for m in e['visuals']:
      if m:wanted[(game,m.lstrip(','))]=None
 for name in ['mc/mtl_veh_t6_dlc_zm_triplane','mc/mtl_veh_t6_dlc_zm_biplane','mc/mtl_veh_t6_dlc_zm_triplane_prop']:wanted[('t6',name)]=None
 # Also retain every original material texture, including normal/specular inputs.
 images={('t5','moon_vista_earth_c'),('t5','moon_vista_earth_destroyed')}
 for (game,name) in wanted:
  item=materials.get((game,name))
  if not item:print('MISSING MATERIAL',game,name);continue
  p,d=item;colors=[t for t in d.get('textures',[]) if t.get('name')=='colorMap' or t.get('semantic')=='colorMap'];colors=colors or d.get('textures',[])[:1]
  allTextures=[t['image'].lstrip(',') for t in d.get('textures',[])];builtin=[name for name in allTextures if name.startswith('$')];portable=[name for name in allTextures if not name.startswith('$')];wanted[(game,name)]={'definition':d,'source':str(p.relative_to(ROOT)),'images':portable,'builtinImages':builtin,'colorImages':[t['image'].lstrip(',') for t in colors]};images.update((game,name) for name in portable)
 images.update(('t6',name) for name in ['~-gveh_t6_dlc_zm_triplane_body_c','~-gveh_t6_dlc_zm_biplane_body_c','~-gmh6_littlebird_blade_spin_c'])
 out=ROOT/'assets-source/26.7/sky-originals';out.mkdir(parents=True,exist_ok=True);(out/'textures').mkdir(exist_ok=True);(out/'effects').mkdir(exist_ok=True)
 packs=[Ipak(p) for p in Path('D:/Jeux/pluto_t6_full_game/zone/all').glob('*.ipak')];records=[]
 for game,name in sorted(images):
  data=None;source=None
  if game=='t5':
   for p in Path('D:/Jeux/pluto_t5_full_game/main').glob('*.iwd'):
    with ZipFile(p) as z:
     key='images/'+name+'.iwi'
     if key in z.namelist():data=z.read(key);source=p.name+'/'+key;break
  else:
   for pack in packs:
    if name_hash(name) in pack.entries:
     data=pack.read(name)
     if data:source=pack.path.name;break
  if data is None:print('MISSING IMAGE',game,name);continue
  key=game+'_'+hashlib.sha256(name.encode()).hexdigest()[:16]
  try:
   image=decode_iwi(data);image.save(out/'textures'/(key+'.png'));(out/'textures'/(key+'.iwi')).write_bytes(data)
   records.append({'game':game,'name':name,'file':key+'.png','source':source,'width':image.width,'height':image.height,'sha256':hashlib.sha256(data).hexdigest()});print('TEXTURE',game,name,image.size,flush=True)
  except Exception as e:print('IMAGE ERROR',name,str(e))
 for p,d in effects:(out/'effects'/p.name).write_text(json.dumps(d,indent=2,allow_nan=False),encoding='utf8')
 (out/'materials.json').write_text(json.dumps([{'game':g,'name':n,**(d or {})} for (g,n),d in wanted.items()],indent=2),encoding='utf8');(out/'textures.json').write_text(json.dumps(records,indent=2),encoding='utf8')
 print('Retained',len(effects),'FX definitions,',len(records),'original textures')
if __name__=='__main__':main()
