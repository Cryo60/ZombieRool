"""Apply the map maker's explicitly selected desktop clips to Cerberus phases."""
from pathlib import Path
import sys, json, io
sys.path.insert(0,'build/audio-python')
import numpy as np, soundfile as sf
ROOT=Path(__file__).resolve().parents[1]
OUT=Path('C:/Users/ecorange1/Desktop/Sons Cerberus')
FILES=json.loads((OUT/'index.json').read_text(encoding='utf8'))
ROOTS=[ROOT/'src/main/resources',ROOT/'resources',ROOT]
def read(number):
    audio,rate=sf.read(OUT/FILES[number-1]['file'],always_2d=True)
    mono=audio.mean(axis=1)
    return np.interp(np.arange(0,len(mono),rate/44100),np.arange(len(mono)),mono)
def encode(name,audio):
    audio*=min(1,.6/max(.001,np.max(np.abs(audio))))
    buf=io.BytesIO();sf.write(buf,audio,44100,format='OGG',subtype='VORBIS')
    for root in ROOTS:
        p=root/'assets/zombierool/sounds/cerberus'/f'{name}.ogg';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(buf.getvalue())
    return {'name':'zombierool:cerberus/'+name,'stream':False}
left,right=read(10),read(11)
length=max(len(left),len(right))
spawn=(np.pad(left,(0,length-len(left)))+np.pad(right,(0,length-len(right))))*.5
events={
    'cerberus_emerge':[encode('spawn_howl',spawn)],
    'cerberus_idle':[encode(f'idle_bark_{i}',read(i)) for i in range(4,10)],
    'cerberus_chew':[encode(f'decapitate_{i-1:02d}',read(i)) for i in (1,2)],
    'cerberus_bite':[encode('dog_bite_00',read(17))],
    'cerberus_next':[encode('meal_finished',read(14))],
    'cerberus_complete':[encode('departure_howl',read(16))],
}
for root in ROOTS:
    p=root/'assets/zombierool/sounds.json';data=json.loads(p.read_text(encoding='utf8'))
    for event,sounds in events.items():data[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':sounds}
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
manifest={'selection':{'appearance':[10,11],'idle':[4,5,6,7,8,9],'chewing':[1,2,17],'meal_finished':[14],'departure':[16]},'clips':FILES,'processing':'44.1 kHz mono; appearance L/R summed together; other variations randomized; peak reduced only'}
(ROOT/'assets-source/26.7/audio/cerberus-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Applied user-selected Cerberus sounds.')
