"""Build curated-family paint masks for the existing 2D weapon sprites. Requires Pillow."""
from pathlib import Path
import base64,json,math,gzip,struct
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/zombierool'
PISTOLS=set('357magnum beretta93r deagle fiveseven glock m1911 magnum mauserc96 star1858 usp45'.split())
LONG=set('arisaka barret dragunov gewehr43 intervention kar98k m14 m1garand m40a3 mosinnagant sniper springfield svt40'.split())
ENERGY=set('covenantcarbine hydra ma5d needler percepteur plasmapistol raygun raygunmarkii storm thundergun whisper wunderwaffedg2 vr11 winters_howl'.split())
BLADE=set('energysword oldsword superbow oldcrossbow'.split())
def prepare():
 result={}
 for p in sorted((ASSETS/'textures/item/weapons').glob('*.png')):
  im=Image.open(p).convert('RGBA');im.thumbnail((256,256),Image.Resampling.LANCZOS);w,h=im.size;pix=im.load();bounds=im.getbbox();x0,y0,x1,y1=bounds
  ident=p.stem;root=ident.split('_empty')[0];family='pistol' if root in PISTOLS else 'long' if root in LONG else 'energy' if root in ENERGY else 'blade' if root in BLADE else 'rifle'
  # Sprites are photographed along the same descending axis. Projecting into
  # that axis protects the muzzle without blanking a diagonal stripe of receiver.
  axis=.58 if family!='pistol' else .40
  opaque=[(x,y) for y in range(h) for x in range(w) if pix[x,y][3]>=192]
  positions=[x+axis*y for x,y in opaque];low,high=min(positions),max(positions)
  lumas=sorted(.2126*pix[x,y][0]+.7152*pix[x,y][1]+.0722*pix[x,y][2] for x,y in opaque)
  shadow=lumas[int(len(lumas)*.13)];highlight=max(1,lumas[int(len(lumas)*.92)])
  mask=bytearray(w*h)
  for x,y in opaque:
   r,g,b,a=pix[x,y];v=max(r,g,b);chroma=v-min(r,g,b);luma=.2126*r+.7152*g+.0722*b;t=(x+axis*y-low)/max(1,high-low)
   muzzle={'pistol':.10,'long':.30,'rifle':.19,'energy':.11,'blade':0}[family]
   if t<muzzle:continue
   # Keep the original contour, engraving, screws and the deepest seams.
   if luma<=shadow:continue
   edge=any(x+dx<0 or x+dx>=w or y+dy<0 or y+dy>=h or pix[x+dx,y+dy][3]<192 for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)))
   if edge:continue
   # Bright ammunition, energy coils and glass stay legible on special weapons.
   if family=='energy' and chroma>45 and v>75 and (g>r*1.2 or b>r*1.25 or r>g*1.5 and b>g*1.5):continue
   if family=='blade' and root=='energysword' and b>r*1.25:continue
   mask[y*w+x]=255
  result['item/weapons/'+ident]=dict(width=w,height=h,family=family,highlight=round(highlight,3),paint=base64.b64encode(mask).decode())
 packed=bytearray(b'ZRSM\x01'+struct.pack('>H',len(result)))
 for name,row in result.items():
  raw=base64.b64decode(row['paint']);bits=bytearray((len(raw)+7)//8)
  for i,value in enumerate(raw):
   if value:bits[i//8]|=1<<(i%8)
  name=name.encode('utf-8');packed+=struct.pack('>H',len(name))+name+struct.pack('>HHd',row['width'],row['height'],row['highlight'])+bits
 target=ROOT/'src/main/resources/META-INF/zombierool/visuals/sprite-masks.bin.gz';target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(gzip.compress(bytes(packed),compresslevel=9,mtime=0))
 print('Prepared',len(result),'weapon/variant masks')
if __name__=='__main__':prepare()
