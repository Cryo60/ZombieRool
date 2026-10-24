"""Targeted read-only extraction of original T5 loaded/streamed audio and WoW sounds.
T5 snd_asset layout: OpenAssetTools T5_Assets.h (GPL-3.0 format reference).
Intermediate decompressed fastfiles and WAVs are disposable, never game edits.
"""
from pathlib import Path
import os,sys,struct,zlib,re,json,subprocess,zipfile,hashlib
from local_casc import LocalCasc
ROOT=Path(__file__).resolve().parents[2]
WORK=Path(os.environ['LOCALAPPDATA'])/'Temp/ZombieRool-enemy-work'
OUT=ROOT/'src/main/resources/assets/zombierool'
FFMPEG=ROOT/'build/audio-tools/ffmpeg.exe'
COEFF=[(256,0),(512,-256),(0,0),(192,64),(240,0),(460,-208),(392,-232)]
report={};events={}

def convert(key,index,data,source,duration=None):
    if data[:4]==b'\x01\0\0\0':
        h=struct.unpack_from('<14I',data);data=wrap(data[h[4]:h[4]+h[12]],h[1],h[2],h[3],h[7],h[10])[0]
    name=f'encounters/{key}_{index}';dest=OUT/'sounds'/(name+'.ogg');dest.parent.mkdir(parents=True,exist_ok=True)
    args=[str(FFMPEG),'-v','error','-y','-i','pipe:0','-ac','1','-ar','44100']
    if duration:args+=['-t',str(duration)]
    subprocess.run(args+['-c:a','libvorbis','-q:a','4',str(dest)],input=data,check=True,capture_output=True)
    events.setdefault(key,{'sounds':[]})['sounds'].append({'name':'zombierool:'+name,'attenuation_distance':32})
    report.setdefault(key,[]).append({'source':source,'sha256':hashlib.sha256(data).hexdigest()})

def wrap(data,frames,rate,channels,codec,packets,seek=b''):
    def chunk(name,data):return name+struct.pack('<I',len(data))+data+(b'\0' if len(data)%2 else b'')
    if codec==6:
        align=262*channels;spb=512
        assert all(data[p]<=6 for p in range(0,len(data),align)), 'Invalid ADPCM predictor'
        fmt=struct.pack('<HHIIHHHHH',2,channels,rate,rate*align//spb,align,4,32,spb,7)+b''.join(struct.pack('<hh',*c) for c in COEFF)
        body=b'WAVE'+chunk(b'fmt ',fmt)+chunk(b'fact',struct.pack('<I',frames))+chunk(b'data',data)
    else:
        assert codec==7 and packets>0 and len(data)%packets==0
        fmt=struct.pack('<HHIIHH',0x161,channels,rate,12000,len(data)//packets,16)
        body=b'XWMA'+chunk(b'fmt ',fmt)+(chunk(b'dpds',seek) if seek else b'')+chunk(b'data',data)
    return b'RIFF'+struct.pack('<I',len(body))+body,frames/rate

def loaded(r,p):
    h=struct.unpack_from('<15I',r,p-60)
    assert h[0]==0xffffffff and h[1]==1 and h[8] in (6,7) and h[4] in [1,2],h
    frames,rate,channels=h[2:5];size=h[-2];start=r.index(0,p)+1
    seek=r[start:start+h[11]*4];start+=len(seek);data=r[start:start+size];assert len(data)==size
    return wrap(data,frames,rate,channels,h[8],h[11],seek)

def fastfile(zone,selections):
    path=WORK/'extracted'/(zone+'.ff.dec')
    if not path.exists():path.write_bytes(zlib.decompress((Path('D:/Jeux/pluto_t5_full_game/zone/Common')/(zone+'.ff')).read_bytes()[12:]))
    r=path.read_bytes()
    for key,pattern in selections.items():
        matches=[m for m in re.finditer(rb'sound[\\/][ -~]{5,160}?\.wav\x00',r) if re.search(pattern,m[0].decode().replace('\\','/'))]
        for i,m in enumerate(matches):
            data,duration=loaded(r,m.start());convert(key,i,data,zone+':'+m[0].decode().rstrip('\0'),duration)
        assert matches,(zone,key)

if __name__=='__main__':
    fastfile('common_zombie',{'thief_idle':r'/thief/ambient/','thief_sprint':r'/thief/sprint/','thief_steal':r'/thief/steal/','thief_death':r'/thief/death/','thief_anger':r'/thief/anger/'})
    fastfile('zombie_pentagon',{'thief_alarm':r'/thief/thief_alarm_l.wav','thief_teleport':r'/teleporter/pentagon/evt_teleport.wav','thief_teleport_out':r'/teleporter/pentagon/evt_teleport_out.wav'})
    fastfile('zombie_moon',{'astronaut_loop':r'/zombie_moon/evt_astronaut_loop_l.wav','astronaut_death':r'/astronaut/evt_astro_pop.wav','astronaut_attack':r'/gersh/gersh_teleport.wav'})
    fastfile('zombie_coast',{'romero_step':r'/director/step/zmb_director_step_','romero_water_step':r'/director/step/water/','romero_lamp_calm':r'/director/zmb_light_docile_loop_l.wav','romero_lamp_rage':r'/director/zmb_light_loop_l.wav','romero_lamp_start':r'/director/zmb_lamp_start.wav','romero_slam':r'/director/ground_attack/','romero_melee':r'/director/light_hit/','romero_swing':r'/director/light_swing/','romero_zort':r'/director/damage_zort/'})
    with zipfile.ZipFile('D:/Jeux/pluto_t5_full_game/main/iw_32.iwd') as z:
        for source,target in [('angered','romero_anger'),('die','romero_death'),('electric_buff','romero_electric'),('exert','romero_idle'),('laugh','romero_laugh'),('pain_yell','romero_hurt'),('slam','romero_roar'),('speed_buff','romero_speed')]:
            names=[n for n in z.namelist() if '/director/'+source+'/' in n and n.endswith('.wav')]
            for i,n in enumerate(names):convert(target,i,z.read(n),'iw_32.iwd:'+n)
    with zipfile.ZipFile('D:/Jeux/pluto_t5_full_game/main/localized_English_iw04.iwd') as z:
        for key,file in [('thief_escape','thief_end_bad.wav'),('thief_defeated','thief_end_good.wav')]:
            n=next(n for n in z.namelist() if n.endswith('/pentagon_ann/'+file));convert(key,0,z.read(n),'localized_English_iw04.iwd:'+n)
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft')
    files=list(dict.fromkeys((int(line.split(';',1)[0]),line.split(';',1)[1]) for file in ['wow-enemy-files.txt','wow-more-audio.txt'] for line in (WORK/'tools'/file).read_text(encoding='utf-8').splitlines()))
    for kind,prefix in [('abomination','fleshgolem'),('ghoul','ghoul'),('geist','northrendgeist'),('scourge_skeleton','skeleton'),('scourge_zombie','zombie')]:
        for action,pattern in [('idle',r'(aggro|preaggro|ready|what|fidget)'),('attack',r'(attack|yesattack)'),('hurt',r'(wound|hurt)'),('death',r'death')]:
            selected=[(fd,n) for fd,n in files if n.startswith('sound/creature/'+prefix+'/') and re.search(pattern,n.split('/')[-1]) and n.endswith('.ogg')][:4]
            for i,(fd,n) in enumerate(selected):convert(kind+'_'+action,i,casc.read(fd),'WoW FileDataID '+str(fd)+':'+n)
    sounds=OUT/'sounds.json';defs=json.loads(sounds.read_text(encoding='utf-8'));defs.update(events);sounds.write_text(json.dumps(defs,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    dest=ROOT/'assets-source/26.7/enemy-skins/source-audio.json';dest.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print({k:len(v['sounds']) for k,v in events.items()})
