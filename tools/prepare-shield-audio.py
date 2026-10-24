"""Read only five original TranZit shield clips from local T6 banks; preserve provenance."""
from pathlib import Path
import sys,io,struct,json,hashlib,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np
import soundfile as sf
GAME=Path('D:/Jeux/pluto_t6_full_game/sound')
KEYS={0x1E85B383:'hit_1',0xEB0DD084:'hit_2',0xB795ED85:'hit_3',0x841E0A86:'hit_4',0x1C4E3BF9:'break'}
names={int(e.attrib['Hash'],16):e.attrib['Path'] for e in ET.parse(ROOT/'build/dev-26.7/Names.xml').getroot() if int(e.attrib['Hash'],16) in KEYS}
RATES=[8000,12000,16000,24000,32000,44100,48000,96000,192000]
found={}
for bank in sorted(GAME.glob('*.sab*'),key=lambda p:('zmb_classic_transit' not in p.name,p.name)):
    with bank.open('rb') as f:
        header=f.read(72)
        if len(header)<48 or struct.unpack_from('<II',header)!=(0x23585532,14):continue
        count=struct.unpack_from('<I',header,20)[0];f.seek(struct.unpack_from('<Q',header,40)[0])
        for _ in range(count):
            record=f.read(20)
            if len(record)!=20:break
            key,size,offset,samples,rate,ch,loop,fmt=struct.unpack('<IIIIBBBB',record)
            if key in KEYS and key not in found:found[key]=(bank,size,offset,RATES[rate],ch,fmt)
    if len(found)==len(KEYS):break
assert len(found)==5,found
roots=[ROOT/'src/main/resources',ROOT/'resources',ROOT]
manifest=[]
for key,(bank,size,offset,rate,ch,fmt) in found.items():
    with bank.open('rb') as f:f.seek(offset);raw=f.read(size)
    if fmt==0:audio=np.frombuffer(raw,dtype='<i2').reshape(-1,ch).astype(np.float64)/32768
    else:
        decoded=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i','pipe:0','-f','wav','pipe:1'],input=raw,capture_output=True,check=True).stdout
        audio,rate=sf.read(io.BytesIO(decoded),always_2d=True)
    mono=audio.mean(axis=1);fade=min(int(rate*.005),len(mono)//2);mono[:fade]*=np.linspace(0,1,fade);mono[-fade:]*=np.linspace(1,0,fade)
    buf=io.BytesIO();sf.write(buf,mono,rate,format='OGG',subtype='VORBIS');encoded=buf.getvalue()
    for root in roots:
        p=root/f'assets/zombierool/sounds/shield/{KEYS[key]}.ogg';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encoded)
    manifest.append({'event':'shield_break' if KEYS[key]=='break' else 'shield_hit','alias':'wpn_riotshield_zm_destroy' if KEYS[key]=='break' else 'fly_riotshield_zm_impact_zombies','source':str(bank),'asset':names[key],'hash':f'{key:08X}','source_sha256':hashlib.sha256(raw).hexdigest(),'ogg_sha256':hashlib.sha256(encoded).hexdigest(),'seconds':len(mono)/rate})
for root in roots:
    p=root/'assets/zombierool/sounds.json';n=json.loads(p.read_text(encoding='utf8'))
    for event,clips in [('shield_hit',['hit_1','hit_2','hit_3','hit_4']),('shield_break',['break'])]:n[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':[{'name':'zombierool:shield/'+clip,'stream':False} for clip in clips]}
    p.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for lang in ['en_us','fr_fr']:
        p=root/f'assets/zombierool/lang/{lang}.json';n=json.loads(p.read_text(encoding='utf8'));n['subtitles.zombierool.shield_hit']='Bouclier frappé' if lang=='fr_fr' else 'Shield struck';n['subtitles.zombierool.shield_break']='Bouclier brisé' if lang=='fr_fr' else 'Shield breaks';p.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
p=ROOT/'assets-source/26.7/audio/shield-manifest.json';p.parent.mkdir(exist_ok=True,parents=True);p.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
print(json.dumps(manifest,indent=2))
