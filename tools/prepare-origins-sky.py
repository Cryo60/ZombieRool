"""Convert the original six-face sky without changing the game installation."""
from pathlib import Path
import struct,io,json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
source=ROOT/'assets-source/26.7/environment/trenches-original.dds'
data=source.read_bytes();h,w=struct.unpack_from('<II',data,12);mips=max(1,struct.unpack_from('<I',data,28)[0]);fmt=data[84:88]
block=8 if fmt==b'DXT1' else 16;compressed=fmt in (b'DXT1',b'DXT3',b'DXT5');bits=struct.unpack_from('<I',data,88)[0]
def size(m):
 x,y=max(1,w>>m),max(1,h>>m)
 return ((x+3)//4)*((y+3)//4)*block if compressed else x*y*bits//8
stride=sum(size(m) for m in range(mips));assert struct.unpack_from('<I',data,112)[0]&512,'Not a cubemap'
header=bytearray(data[:128]);struct.pack_into('<I',header,112,0);struct.pack_into('<I',header,28,1)
atlas=Image.new('RGB',(w*3,h*2))
for face in range(6):
 # This exporter writes all six top faces first, followed by each smaller mip level.
 atlas.paste(Image.open(io.BytesIO(bytes(header)+data[128+face*size(0):128+(face+1)*size(0)])).convert('RGB'),((face%3)*w,(face//3)*h))
relative=Path('textures/environment/trenches_sky.png')
for root in ['src/main/resources','resources','','build/resources/main']:
 target=ROOT/root/'assets/zombierool'/relative;target.parent.mkdir(parents=True,exist_ok=True);atlas.save(target)
 target.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}')
(ROOT/'assets-source/26.7/environment/origins-manifest.json').write_text(json.dumps({'source':'zm_tomb:skybox_zm_tomb_ft','sha256':hashlib.sha256(data).hexdigest(),'size':[w,h],'layout':'cube','coordinates':'z_up','output':str(relative)},indent=2)+'\n')
print('Origins sky:',atlas.size)
