"""Prepare source cubemaps and selected T5/T6 ambience without modifying the games."""
from pathlib import Path
import struct,io,json,hashlib,csv,subprocess,re,zlib,zipfile,ast
from PIL import Image

ROOT=Path(__file__).resolve().parents[1];RAW=ROOT/'build/map-atmosphere/raw';OUT=ROOT/'src/main/resources/assets/zombierool';FF=ROOT/'build/audio-tools/ffmpeg.exe';manifest=[]
for zone,sky,name in [('zombie_moon','skybox_zom_moon_ft','lunar'),('zombie_cod5_prototype','rvn_makin1_ft','nightfall'),('zm_highrise','skybox_zm_highrise_ft','highrise')]:
 p=RAW/zone/'images'/(sky+'.dds');data=p.read_bytes();h,w=struct.unpack_from('<II',data,12);mips=max(1,struct.unpack_from('<I',data,28)[0]);fmt=data[84:88];block=8 if fmt==b'DXT1' else 16;compressed=fmt in (b'DXT1',b'DXT3',b'DXT5');bits=struct.unpack_from('<I',data,88)[0];size=((w+3)//4)*((h+3)//4)*block if compressed else w*h*bits//8;stride=sum(max(1,(max(1,w>>m)+3)//4)*max(1,(max(1,h>>m)+3)//4)*block if compressed else max(1,w>>m)*max(1,h>>m)*bits//8 for m in range(mips));assert struct.unpack_from('<I',data,112)[0]&512,(p,'not a cubemap');header=bytearray(data[:128]);struct.pack_into('<I',header,112,0);struct.pack_into('<I',header,28,1);atlas=Image.new('RGB',(w*3,h*2))
 for face in range(6):atlas.paste(Image.open(io.BytesIO(bytes(header)+data[128+face*stride:128+face*stride+size])).convert('RGB'),((face%3)*w,(face//3)*h))
 target=OUT/f'textures/environment/{name}_sky.png';atlas.save(target);target.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}');manifest.append({'asset':str(p),'sha256':hashlib.sha256(data).hexdigest(),'output':str(target.relative_to(OUT))});print('SKY',name,atlas.size,flush=True)
events=json.loads((OUT/'sounds.json').read_text(encoding='utf-8'))
def convert(event,payload,source,stream):
 rows=events.setdefault(event,{'subtitle':'subtitles.zombierool.'+event,'sounds':[]})['sounds'];name=f'environment/{event}_{len(rows)}';target=OUT/f'sounds/{name}.ogg';target.parent.mkdir(parents=True,exist_ok=True)
 subprocess.run([str(FF),'-v','error','-y','-i','pipe:0','-ac','1','-ar','44100','-c:a','libvorbis','-q:a','4',str(target)],input=payload,check=True);rows.append({'name':'zombierool:'+name,'stream':stream,'attenuation_distance':16384});manifest.append({'asset':source,'sha256':hashlib.sha256(payload).hexdigest(),'event':event})
# Reuse the verified T5 ADPCM/XWMA decoder functions, without executing its exporter.
decoder=(ROOT/'tools/export-napalm-t5.py').read_text(encoding='utf-8');decoder=decoder[decoder.index('def chunk('):decoder.index('assets={}')];decoder=decoder[:decoder.index('    pcm,samplerate=')]+"    return decoded.stdout\n";exec(decoder)
game=Path('D:/Jeux/pluto_t5_full_game');selected={}
for zone,event,token in [('zombie_moon','ambient_lunar_signal',r'\amb\oneshots\amb_benburt_'),('zombie_cod5_prototype','ambient_night_whispers',r'\proto\amb_spooky\amb_spooky_')]:
 events[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':[]};data=zlib.decompress((game/'zone/Common'/(zone+'.ff')).read_bytes()[12:])
 for match in re.finditer(rb'sound\\[^\x00]{5,200}\.wav\x00',data):
  path=match.group()[:-1].decode('ascii','ignore')
  if token not in path:continue
  header=struct.unpack_from('<15I',data,match.start()-60)
  if header[0]!=0xffffffff or header[1]!=1 or header[8] not in (6,7):continue
  at=match.end()+header[11]*4;payload=decode(data[at:at+header[13]],header[2],header[3],header[4],header[8],header[11]);convert(event,payload,zone+':'+path,False)
  if len(events[event]['sounds'])>=5:break
 print('RESIDENT',event,len(events[event]['sounds']),flush=True)
# Long atmospheric loops are streamed from T5's IWD archives.
wanted={'sound/evt/zombie_moon/amb/cave_bg_l.wav':'ambient_lunar_base','sound/wawzmb/sfx/levels/zombie/proto/lines/amb_spooky_left_l.wav':'ambient_night_wind'}
for archive in game.glob('main/*.iwd'):
 with zipfile.ZipFile(archive) as pack:
  for path in pack.namelist():
   event=wanted.get(path.lower())
   if not event:continue
   data=pack.read(path);header=struct.unpack_from('<14I',data);payload=decode(data[header[4]:header[4]+header[12]],header[1],header[2],header[3],header[7],header[10]);events[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':[]};convert(event,payload,str(archive)+':'+path,True);print('LOOP',event,flush=True)
rows=list(csv.DictReader((RAW/'zm_highrise/soundbank/zmb_highrise.all.aliases.csv').open(encoding='utf-8-sig')))
for event,token in [('ambient_tower_wind','amb_wind'),('ambient_tower_creak','amb_metal_stress')]:
 events[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':[]}
 for row in rows:
  if not row['Name'].startswith(token):continue
  path=RAW/'zm_highrise'/row['FileSource'].replace('\\','/').removeprefix('raw/')
  if not path.exists():continue
  convert(event,path.read_bytes(),str(path),row['Looping']=='looping')
  if len(events[event]['sounds'])>=3:break
 print('T6',event,len(events[event]['sounds']),flush=True)
for event in ['ambient_lunar_base','ambient_lunar_signal','ambient_night_wind','ambient_night_whispers','ambient_tower_wind','ambient_tower_creak']:assert events.get(event,{}).get('sounds'),event
(OUT/'sounds.json').write_text(json.dumps(events,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');(ROOT/'assets-source/26.7/environment/presets-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
