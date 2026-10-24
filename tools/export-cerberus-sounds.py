"""Export named MOTD wolf-head sounds for the map maker to audition; game files stay read-only."""
from pathlib import Path
import sys, struct, subprocess, io, json, xml.etree.ElementTree as ET
sys.path.insert(0, 'build/audio-python')
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
GAME = Path('D:/Jeux/pluto_t6_full_game/sound')
OUT = Path('C:/Users/ecorange1/Desktop/Sons Cerberus')
RATES = [8000,12000,16000,24000,32000,44100,48000,96000,192000]
names = {int(e.attrib['Hash'],16):e.attrib['Path'] for e in ET.parse(ROOT/'build/dev-26.7/Names.xml').getroot() if '.pc.snd' in e.attrib.get('Path','')}
targets = {k:v for k,v in names.items() if '\\wolf_head\\' in v or '\\decapitate\\' in v or '\\dog\\dog_bite_' in v or '\\dog\\growl_' in v}
found = {}
for bank in sorted(GAME.glob('*.sab*'), key=lambda p:('alcatraz' not in p.name,p.name)):
    with bank.open('rb') as f:
        header=f.read(72)
        if len(header)<48 or struct.unpack_from('<II',header)!=(0x23585532,14):continue
        count=struct.unpack_from('<I',header,20)[0];table=struct.unpack_from('<Q',header,40)[0]
        f.seek(table)
        for _ in range(count):
            record=f.read(20)
            if len(record)!=20:break
            key,size,offset,samples,rate,ch,loop,fmt=struct.unpack('<IIIIBBBB',record)
            if key in targets and key not in found:found[key]=(bank,size,offset,RATES[rate],ch,fmt)
OUT.mkdir(parents=True,exist_ok=True)
manifest=[]
chews=[]
for key,(bank,size,offset,rate,ch,fmt) in sorted(found.items(),key=lambda e:targets[e[0]]):
    with bank.open('rb') as f:f.seek(offset);raw=f.read(size)
    if fmt==0:audio=np.frombuffer(raw,dtype='<i2').reshape(-1,ch).astype(np.float64)/32768
    else:
        decoded=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i','pipe:0','-f','wav','pipe:1'],input=raw,capture_output=True,check=True).stdout
        audio,rate=sf.read(io.BytesIO(decoded),always_2d=True)
    # Reduce excessive levels, never boost quiet clips or combine left/right takes.
    peak=float(np.max(np.abs(audio)));audio*=min(1,.65/max(.001,peak))
    stem=targets[key].split('\\')[-1].removesuffix('.pc.snd')
    filename=f'{len(manifest)+1:02d}_{stem}.wav'
    sf.write(OUT/filename,audio,rate,subtype='PCM_16')
    manifest.append({'file':filename,'source':str(bank),'asset':targets[key],'hash':f'{key:08X}','seconds':round(len(audio)/rate,3),'channels':audio.shape[1]})
    if '\\decapitate\\' in targets[key]:
        mono=audio.mean(axis=1);mono=np.interp(np.arange(0,len(mono),rate/44100),np.arange(len(mono)),mono)
        encoded=io.BytesIO();sf.write(encoded,mono,44100,format='OGG',subtype='VORBIS')
        name=stem.split('.')[0]
        for resource in [ROOT/'src/main/resources',ROOT/'resources',ROOT]:
            path=resource/'assets/zombierool/sounds/cerberus'/f'{name}.ogg';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encoded.getvalue())
        chews.append({'name':'zombierool:cerberus/'+name,'stream':False})
assert len(chews)==2, 'Both requested decapitate chewing variants must be found'
for resource in [ROOT/'src/main/resources',ROOT/'resources',ROOT]:
    path=resource/'assets/zombierool/sounds.json';data=json.loads(path.read_text(encoding='utf8'))
    data['cerberus_chew']={'subtitle':'subtitles.zombierool.cerberus_chew','sounds':chews}
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(OUT/'index.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(OUT/'Liste des sons.txt').write_text('\n'.join(f"{e['file']} — {e['seconds']} s" for e in manifest)+'\n',encoding='utf8')
print(json.dumps({'folder':str(OUT),'count':len(manifest),'sounds':manifest},ensure_ascii=False,indent=2))
