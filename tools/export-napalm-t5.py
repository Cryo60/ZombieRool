"""Read Shangri-La's T5 fastfile/IWD audio without modifying the installation.

T5 snd_asset layout: OpenAssetTools src/Common/Game/T5/T5_Assets.h.
IWD MS-ADPCM uses 262-byte blocks per channel (512 samples).
Resident WMA packets are wrapped as RIFF/XWMA before FFmpeg decoding.
"""
from pathlib import Path
import re,zlib,struct,subprocess,json,sys,io,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
import soundfile as sf
game=Path('D:/Jeux/pluto_t5_full_game')
out=Path.home()/'Desktop/Sons Napalm T5';out.mkdir(exist_ok=True)
ff=game/'zone/Common/zombie_temple.ff'
data=zlib.decompress(ff.read_bytes()[12:])
def chunk(name,body):return name+struct.pack('<I',len(body))+body+(b'\0' if len(body)%2 else b'')
def decode(payload,frames,rate,channels,fmt,packets):
    if fmt==6:
        block=262*channels
        coefs=[(256,0),(512,-256),(0,0),(192,64),(240,0),(460,-208),(392,-232)]
        header=struct.pack('<HHIIHHHHH',2,channels,rate,rate*block//512,block,4,32,512,7)+b''.join(struct.pack('<hh',*c) for c in coefs)
        body=b'WAVE'+chunk(b'fmt ',header)+chunk(b'fact',struct.pack('<I',frames))+chunk(b'data',payload)
    elif fmt==7:
        assert packets and len(payload)%packets==0
        header=struct.pack('<HHIIHH',0x161,channels,rate,12000,len(payload)//packets,16)
        body=b'XWMA'+chunk(b'fmt ',header)+chunk(b'data',payload)
    else:raise ValueError(fmt)
    wrapped=b'RIFF'+struct.pack('<I',len(body))+body
    decoded=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i','pipe:0','-f','wav','pipe:1'],input=wrapped,capture_output=True,check=True)
    if decoded.stderr:raise ValueError(decoded.stderr.decode())
    pcm,samplerate=sf.read(io.BytesIO(decoded.stdout),always_2d=True)
    assert abs(len(pcm)-frames)<=2048,(len(pcm),frames)
    return pcm[:frames],samplerate
assets={}
for match in re.finditer(rb'sound\\[^\x00]{5,160}\.wav\x00',data):
    path=match.group()[:-1].decode('ascii',errors='ignore')
    if '\\napalm\\' not in path:continue
    h=struct.unpack_from('<15I',data,match.start()-60)
    if h[0]!=0xffffffff or h[1]!=1 or not 4000<=h[3]<=96000 or h[4] not in (1,2) or h[8] not in (6,7):continue
    at=match.end()+h[11]*4
    pcm,rate=decode(data[at:at+h[13]],h[2],h[3],h[4],h[8],h[11])
    stem=Path(path.replace('\\','/')).stem
    sf.write(out/(stem+'.wav'),pcm,rate,subtype='PCM_16')
    assets[stem]=(pcm,rate,{'source':str(ff),'asset':path,'frames':len(pcm),'rate':rate,'channels':h[4]})
with zipfile.ZipFile(game/'main/iw_35.iwd') as pack:
    name='sound/raven/evt/zmb_temple/napalm/evt_searing_flesh_l.wav';raw=pack.read(name);h=struct.unpack_from('<14I',raw)
    pcm,rate=decode(raw[h[4]:h[4]+h[12]],h[1],h[2],h[3],h[7],h[10])
    assets['evt_searing_flesh_l']=(pcm,rate,{'source':str(game/'main/iw_35.iwd'),'asset':name,'frames':len(pcm),'rate':rate,'channels':h[3]})
    sf.write(out/'evt_searing_flesh_l.wav',pcm,rate,subtype='PCM_16')
events={'napalm_spawn':['evt_napalm_zombie_spawn'],'napalm_death':['evt_napalm_zombie_explo'],'napalm_charge':['evt_napalm_zombie_charge'],'napalm_step':['step_00','step_01','step_02'],'napalm_idle':['zmb_napalm_ambient01','zmb_napalm_ambient02','zmb_napalm_ambient03','zmb_napalm_ambient04'],'napalm_attack':['zmb_napalm_attack01','zmb_napalm_attack02','zmb_napalm_attack03'],'napalm_burn':['evt_napalm_fire_l'],'napalm_loop':['evt_napaln_zombie_loop_l']}
for resource in [ROOT/'src/main/resources',ROOT/'resources',ROOT]:
    base=resource/'assets/zombierool';sounds=json.loads((base/'sounds.json').read_text(encoding='utf-8'))
    for event,names in events.items():
        entries=[]
        for stem in names:
            pcm,rate,_=assets[stem]
            if event in ('napalm_burn','napalm_loop'):pcm=pcm[:rate*2]
            target=base/'sounds/napalm'/f'{stem}.ogg';target.parent.mkdir(exist_ok=True)
            sf.write(target,pcm.mean(axis=1),rate,format='OGG',subtype='VORBIS')
            entries.append({'name':f'zombierool:napalm/{stem}','stream':False})
        sounds[event]={'sounds':entries,'subtitle':f'subtitles.zombierool.{event}'}
    (base/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
manifest=[{'file':stem+'.wav',**meta} for stem,(_,_,meta) in assets.items()]
(out/'index.json').write_text(json.dumps(manifest,indent=2)+'\n')
target=ROOT/'assets-source/26.7/audio/napalm-t5-manifest.json';target.write_text(json.dumps(manifest,indent=2)+'\n')
print(f'Exported {len(assets)} original Shangri-La sounds; {len(events)} events prepared.')
