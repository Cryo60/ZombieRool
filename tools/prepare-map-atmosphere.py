"""Convert a local T6 cubemap and selected ambience clips; leave the installation read-only.
Run with the bundled Python runtime (Pillow, NumPy and soundfile).
The original aliases and hashes are retained only as provenance, not product identifiers.
"""
from pathlib import Path
import csv, hashlib, io, json, struct, subprocess
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
RAW=ROOT/'build/map-atmosphere/raw/zm_nuked'
OUT=ROOT/'src/main/resources/assets/zombierool'
manifest={'sky':{},'audio':[]}
dds=RAW/'images/skybox_zm_nuked_ft.dds';data=dds.read_bytes()
height,width=struct.unpack_from('<II',data,12);assert data[84:88]==b'DXT5' and struct.unpack_from('<I',data,28)[0]==1
size=((width+3)//4)*((height+3)//4)*16;assert len(data)==128+6*size
atlas=Image.new('RGB',(width*3,height*2));header=bytearray(data[:128]);struct.pack_into('<I',header,112,0)
for face in range(6):
    image=Image.open(io.BytesIO(bytes(header)+data[128+face*size:128+(face+1)*size])).convert('RGB')
    atlas.paste(image,((face%3)*width,(face//3)*height))
target=OUT/'textures/environment/ashfall_sky.png';target.parent.mkdir(parents=True,exist_ok=True);atlas.save(target)
manifest['sky']={'source':str(dds),'source_sha256':hashlib.sha256(data).hexdigest(),'output':'textures/environment/ashfall_sky.png','layout':'3x2, +X -X +Y / -Y +Z -Z; source coordinates Z-up','output_sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
rows=list(csv.DictReader((RAW/'soundbank/zmb_nuked_real.all.aliases.csv').open(encoding='utf-8-sig')))
aliases={'amb_air_stereo':'ambient_ash_wind','amb_nuclear_bomb':'ambient_cloud_rumble','amb_thunder':'ambient_distant_thunder','zmb_perks_incoming_alarm':'ambient_distant_alarm','amb_nuked_wind_gust':'ambient_wind_gust'}
events={};counts={}
for row in rows:
    if row['Name'] not in aliases:continue
    event=aliases[row['Name']];index=counts.get(event,0);counts[event]=index+1
    source=RAW/row['FileSource'].replace('\\','/').removeprefix('raw/')
    assert source.exists(),source
    name=f'environment/{event}_{index}';dest=OUT/f'sounds/{name}.ogg';dest.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-y','-i',str(source),'-ac','1','-ar','44100','-c:a','libvorbis','-q:a','5','-af','afade=t=in:d=0.015,areverse,afade=t=in:d=0.015,areverse',str(dest)],check=True)
    pcm=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i',str(dest),'-f','f32le','pipe:1'],capture_output=True,check=True).stdout
    duration=len(pcm)/(4*44100)
    events.setdefault(event,{'subtitle':'subtitles.zombierool.'+event,'sounds':[]})['sounds'].append({'name':'zombierool:'+name,'stream':row['Looping']=='looping','attenuation_distance':16384})
    manifest['audio'].append({'event':event,'alias':row['Name'],'source':str(source),'loop':row['Looping'],'duration':duration,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'output_sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
p=OUT/'sounds.json';sounds=json.loads(p.read_text(encoding='utf-8'));sounds.update(events);p.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
p=ROOT/'assets-source/26.7/environment/ashfall-manifest.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print('Converted six sky faces and',len(manifest['audio']),'original ambience clips:',counts)
