"""Export original T6 Panzer/Brutus audio for audition, without modifying game banks."""
from pathlib import Path
import sys, struct, subprocess, io, json, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'build/audio-python'))
import numpy as np
import soundfile as sf
GAME = Path('D:/Jeux/pluto_t6_full_game/sound')
OUT = Path.home()/'Desktop'/'Sons Mini Boss T6'
RATES = [8000,12000,16000,24000,32000,44100,48000,96000,192000]
names = {int(e.attrib['Hash'],16):e.attrib['Path'] for e in ET.parse(ROOT/'build/dev-26.7/Names.xml').getroot() if '.pc.snd' in e.attrib.get('Path','')}
targets = {k:v for k,v in names.items() if '\\mechz\\' in v or '\\brutus\\' in v or '\\brutus_spawn\\' in v or '\\vox_brutus_' in v}
found = {}
for bank in sorted(GAME.glob('*.sab*')):
    with bank.open('rb') as f:
        header=f.read(72)
        if len(header)<48 or struct.unpack_from('<II',header)!=(0x23585532,14):continue
        count=struct.unpack_from('<I',header,20)[0];table=struct.unpack_from('<Q',header,40)[0]
        f.seek(table)
        for _ in range(count):
            record=f.read(20)
            if len(record)!=20:break
            key,size,offset,samples,rate,ch,loop,fmt=struct.unpack('<IIIIBBBB',record)
            if key in targets and key not in found and rate<len(RATES):found[key]=(bank,size,offset,RATES[rate],ch,fmt)
OUT.mkdir(parents=True,exist_ok=True)
manifest=[]
for key,(bank,size,offset,rate,ch,fmt) in sorted(found.items(),key=lambda e:targets[e[0]]):
    try:
        with bank.open('rb') as f:f.seek(offset);raw=f.read(size)
        if fmt==0:audio=np.frombuffer(raw,dtype='<i2').reshape(-1,ch).astype(np.float64)/32768
        else:
            decoded=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i','pipe:0','-f','wav','pipe:1'],input=raw,capture_output=True,check=True).stdout
            audio,rate=sf.read(io.BytesIO(decoded),always_2d=True)
        group='Panzer' if '\\mechz\\' in targets[key] else 'Brutus'
        folder=OUT/group;folder.mkdir(exist_ok=True)
        stem=targets[key].split('\\')[-1].removesuffix('.pc.snd')
        filename=f'{group}/{stem}_{key:08X}.wav'
        sf.write(OUT/filename,audio,rate,subtype='PCM_16')
        manifest.append({'file':filename,'source':str(bank),'asset':targets[key],'hash':f'{key:08X}','seconds':round(len(audio)/rate,3),'channels':audio.shape[1]})
    except Exception as e:print(f'Could not decode {key:08X}: {e}')
(OUT/'index.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(OUT/'Liste des sons.txt').write_text('\n'.join(f"{e['file']} — {e['seconds']} s" for e in manifest)+'\n',encoding='utf8')
print(json.dumps({'folder':str(OUT),'Panzer':sum(e['file'].startswith('Panzer/') for e in manifest),'Brutus':sum(e['file'].startswith('Brutus/') for e in manifest)}))
