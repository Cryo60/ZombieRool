"""Import selected English boss voices from the local cache or public English CDN."""
from pathlib import Path
import sys,json,re,hashlib,subprocess,urllib.request
from local_casc import LocalCasc
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'examples/stratholme/zombierool'

def main():
    names=list(dict.fromkeys((int(a),b) for file in ['voice-names.csv','source-names.csv'] for a,b in (line.split(';',1) for line in (ROOT/'build/map-actors/revision'/file).read_text(encoding='utf-8').splitlines())))
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow',locale='enUS');classic=LocalCasc('C:/Program Files (x86)/World of Warcraft',product='wow_classic_era',locale='enUS')
    report=[]
    for id,folder in [('baron_rivendare','baronrivendare'),('balnazzar','balnazzar'),('crypt_fiend','cryptfiend')]:
        p=OUT/'entities'/(id+'.json');profile=json.loads(p.read_text(encoding='utf-8'));sounds={}
        rules={'aggro':r'rivendareaggro|vo_70_balnazzar_0[13]\.ogg|cryptfiendaggro','attack':r'attack(0[1-3]|[ab])\.ogg|_attack_0[1-3]\.ogg','hurt':r'wound0[12]\.ogg|_wound_0[12]\.ogg|wound[ab]\.ogg','death':r'rivendaredeath|_death_01\.ogg|cryptfienddeath','slay':r'rivendareslay','cast':r'rivendarespecial|cryptfiendpreaggro'}
        for action,pattern in rules.items():
            selected=[(fd,name) for fd,name in names if name.lower().endswith('.ogg') and '/'+folder+'/' in name.lower() and re.search(pattern,name.lower())][:3]
            for fd,name in selected:
                origin='local CASC enUS'
                try:raw=casc.read(fd)
                except Exception:
                    try:raw=classic.read(fd)
                    except Exception:
                        url=f"https://wow.zamimg.com/sound-ids/live/enus/{fd%256}/{fd}/{name.split('/')[-1]}"
                        raw=urllib.request.urlopen(url,timeout=20).read();origin=url
                assert raw[:4]==b'OggS'
                filename=f'{id}_{action}_{len(sounds.get(action,[]))}'
                dest=OUT/'audio/custom/stratholme'/(filename+'.ogg');dest.parent.mkdir(parents=True,exist_ok=True)
                subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-y','-i','pipe:0','-ac','1','-ar','44100','-c:a','libvorbis','-q:a','4',str(dest)],input=raw,check=True)
                sounds.setdefault(action,[]).append('zombierool:custom/stratholme/'+filename)
                report.append(dict(profile=id,action=action,fileDataID=fd,source=name,locale='enUS',retrievedFrom=origin,sourceSha256=hashlib.sha256(raw).hexdigest(),file=dest.relative_to(OUT).as_posix()))
        # Classic demon combat vocals complement the available named Balnazzar speech.
        if id=='balnazzar':
            for action,fd in [('cast',547770),('death',547766)]:
                if sounds.get(action):continue
                raw=classic.read(fd);dest=OUT/'audio/custom/stratholme'/f'{id}_{action}_0.ogg';dest.write_bytes(raw);sounds[action]=['zombierool:custom/stratholme/'+dest.stem]
                report.append(dict(profile=id,action=action,fileDataID=fd,source='Classic DreadLord combat vocal',locale='enUS',sourceSha256=hashlib.sha256(raw).hexdigest(),file=dest.relative_to(OUT).as_posix()))
        profile['sounds']=sounds;p.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':')),encoding='utf-8');print(id,{a:len(v) for a,v in sounds.items()})
    (ROOT/'assets-source/26.7/map-actors/stratholme-audio-provenance.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

if __name__=='__main__':main()
