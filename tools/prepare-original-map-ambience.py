"""Resolve every environmental alias from local map banks, including shared sound assets.
Reads game installations only. Keeps provenance and a complete per-alias inventory.
"""
from pathlib import Path
from collections import defaultdict
import csv, json, re, struct, subprocess, hashlib, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
GAME=Path('D:/Jeux/pluto_t6_full_game/sound')
ZONES={
 'nuketown_zombies': ROOT/'build/machine-delivery-source/t6/zm_nuked/soundbank/zmb_nuked_real.all.aliases.csv',
 'die_rise': ROOT/'build/atmosphere-audio-source/t6/zm_highrise/soundbank/zmb_highrise.all.aliases.csv',
 'origins': ROOT/'build/atmosphere-audio-source/t6/zm_tomb/soundbank/zmb_tomb.all.aliases.csv',
}
ORIGINS_ALIASES={'amb_aa_gun','amb_plane_close_loop','amb_plane_explode','exp_flak_dist','amb_weather_2d_default_outdoor','amb_weather_2d_rain_outdoor','amb_weather_2d_snow_outdoor','amb_thunder_clap_zm'}
def normalized(path):
 return path.lower().replace('/','\\').removeprefix('devraw\\').removeprefix('raw\\').removesuffix('.wav').removesuffix('.flac')
aliases={};wanted=set()
for preset,path in ZONES.items():
 groups=defaultdict(list)
 for row in csv.DictReader(path.open(encoding='utf-8-sig',newline='')):
  if preset=='origins' and row['Name'] not in ORIGINS_ALIASES:continue
  if row['Name'].startswith('amb_') or row['Name'] in ('zmb_highrise_wind_l','zmb_highrise_wind_r','exp_flak_dist') or ('\\amb\\' in row['FileSource'].lower() and '\\vox\\' not in row['FileSource'].lower()):
   groups[row['Name']].append(row)
   if row['FileSource']:wanted.add(normalized(row['FileSource']))
 aliases[preset]=groups
hashes={}
for _,element in ET.iterparse(ROOT/'build/dev-26.7/Names.xml',events=('end',)):
 path=element.attrib.get('Path','')
 if normalized(path) in wanted:hashes[int(element.attrib['Hash'],16)]=normalized(path)
 element.clear()
RATES=[8000,12000,16000,24000,32000,44100,48000,96000,192000]
found={}
for bank in sorted(GAME.glob('*.sab*')):
 with bank.open('rb') as f:
  h=f.read(72)
  if len(h)<48 or struct.unpack_from('<II',h)!=(0x23585532,14):continue
  count=struct.unpack_from('<I',h,20)[0];table=struct.unpack_from('<Q',h,40)[0];f.seek(table)
  for _ in range(count):
   record=f.read(20)
   if len(record)!=20:break
   key,size,offset,samples,rate,ch,loop,fmt=struct.unpack('<IIIIBBBB',record)
   if key in hashes and hashes[key] not in found and rate<len(RATES):found[hashes[key]]=(bank,size,offset,samples,RATES[rate],ch,fmt,key)
out=ROOT/'src/main/resources/assets/zombierool';sounds=json.loads((out/'sounds.json').read_text(encoding='utf-8'));library={};report={};missing=[];converted={}
for preset,groups in aliases.items():
 rows=[];inventory=[]
 for alias,variants in sorted(groups.items()):
  clips=[];details=[]
  for variant in variants:
   path=normalized(variant['FileSource'])
   if not path:continue
   if path not in found:missing.append((preset,alias,path));continue
   bank,size,offset,samples,rate,ch,fmt,key=found[path]
   stem=re.sub('[^a-z0-9_]+','_',Path(path.replace('\\','/')).name.removesuffix('.pc.snd'))+'_'+f'{key:08x}'
   asset='environment/original/'+stem
   if asset not in converted:
    with bank.open('rb') as f:f.seek(offset);raw=f.read(size)
    target=out/'sounds'/f'{asset}.ogg';target.parent.mkdir(parents=True,exist_ok=True)
    command=[str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-y']
    if fmt==0:command+=['-f','s16le','-ar',str(rate),'-ac',str(ch)]
    command+=['-i','pipe:0','-ac','1','-c:a','libvorbis','-q:a','4',str(target)]
    subprocess.run(command,input=raw,check=True)
    converted[asset]={'bank':str(bank),'asset':path,'hash':f'{key:08X}','sha256':hashlib.sha256(raw).hexdigest(),'seconds':samples/rate,'channels':1}
   clip={'name':'zombierool:'+asset,'stream':False}
   if clip not in clips:clips.append(clip);details.append(converted[asset])
  if not clips:
   inventory.append({'alias':alias,'status':'empty_alias' if not any(v['FileSource'] for v in variants) else 'missing','variants':[]});continue
  event='ambient_'+preset+'_'+alias.removeprefix('amb_').removeprefix('zmb_')
  sounds[event]={'sounds':clips,'subtitle':'subtitles.zombierool.'+event}
  first=variants[0];loop=first['Looping']=='looping'
  # All aliases are available; stagger one-shots and keep local loops quieter than the broad wind bed.
  wind=any(k in alias for k in ['wind','air_stereo']);distant=any(k in alias for k in ['nuclear','thunder','meteor'])
  radius=1600 if distant else 384 if wind else 128
  pos=[-180,160,-220] if distant else [0,80,0] if wind else [25+(len(rows)%4)*14,82,-20-(len(rows)//4)*12]
  volume=.24 if wind else .48 if distant else .12 if loop else .32
  entry={'id':alias,'sound':'zombierool:'+event,'enabled':True,'loop':loop,'global':wind,'x':pos[0],'y':pos[1],'z':pos[2],'volume':volume,'radius':radius,'intervalMin':900 if distant else 600,'intervalMax':2800 if distant else 2200,'pitch':1,'pitchVariation':.025,'fadeIn':40,'fadeOut':30}
  if 'thunder' in alias:entry['flash']=.55;entry['soundSpeed']=343
  if preset=='origins':
   if 'weather' in alias:
    entry.update({'global':True,'volume':.3,'enabled':alias=='amb_weather_2d_default_outdoor'})
   else:
    directions={'amb_aa_gun':[-180,130,-240],'exp_flak_dist':[240,170,-200],'amb_plane_explode':[-230,180,190],'amb_plane_close_loop':[160,210,260],'amb_thunder_clap_zm':[-280,200,-300]}
    entry.update(dict(zip(['x','y','z'],directions[alias])))
    entry.update({'radius':1600,'volume':.32 if loop else .6,'intervalMin':120 if alias=='amb_aa_gun' else 180 if alias=='exp_flak_dist' else 360,'intervalMax':400 if alias=='amb_aa_gun' else 600 if alias=='exp_flak_dist' else 1200})
    if alias=='amb_plane_close_loop':
     # A brief distant flyover, with silence between passes rather than a permanent engine bed.
     entry.update({'loop':False,'volume':.14,'x':420,'y':260,'z':680,'intervalMin':600,'intervalMax':1400,'fadeIn':20})
  rows.append(entry);inventory.append({'alias':alias,'status':'included','event':event,'loop':loop,'variants':details})
 library[preset]=rows;report[preset]={'aliases':inventory,'source':str(ZONES[preset]),'sources':len(rows),'clips':sum(len(v['variants']) for v in inventory)}
if missing:raise RuntimeError('Unresolved environmental sounds: '+repr(missing))
(out/'original-map-ambience.json').write_text(json.dumps(library,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(out/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
manifest=ROOT/'assets-source/26.7/audio/original-map-ambience-manifest.json';manifest.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
for prefix in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool',ROOT/'build/resources/main/assets/zombierool']:
 for relative in ['sounds.json','original-map-ambience.json']+[f'sounds/{asset}.ogg' for asset in converted]:
  target=prefix/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((out/relative).read_bytes())
print(json.dumps({'presets':{k:{'sources':v['sources'],'clips':v['clips']} for k,v in report.items()},'unique_clips':len(converted),'missing':missing},indent=2))
