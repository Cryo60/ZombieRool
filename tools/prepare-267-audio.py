"""Prepare mono positional Vorbis audio from Kenney's CC0 packs (no runtime dependencies)."""
from pathlib import Path
import json,sys,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np
import soundfile as sf
ART=ROOT/'assets-source/26.7/audio'
RATE=44100
def read(pack,name,speed=1):
    samples,rate=sf.read(ART/pack/'Audio'/(name+'.ogg'),always_2d=True)
    mono=samples.mean(axis=1)
    return np.interp(np.arange(0,len(mono),speed*rate/RATE),np.arange(len(mono)),mono)
def mix(*layers):
    result=np.zeros(max(offset+len(data) for offset,data in layers))
    for offset,data in layers:result[offset:offset+len(data)]+=data
    return result
field=lambda name,speed=1:read('sci-fi-sounds',name,speed)
sounds={
 'soul_release':field('forceField_002',.9),
 'soul_absorb':field('forceField_000',1.4)[::-1].copy(),
 'soul_chest_open':read('rpg-audio','metalLatch'),
 'soul_complete':mix((0,field('forceField_003')),(int(RATE*.18),field('forceField_003',1.25)*.6),(int(RATE*.38),field('forceField_003',1.5)*.6)),
 'zipline_attach':read('rpg-audio','metalClick'),
 'zipline_arrive':read('impact-sounds','impactMetal_light_000'),
 'zipline_loop':mix((0,field('engineCircular_002')),(0,field('thrusterFire_002')*.3)),
}
subtitles={
 'soul_release':('Soul escapes','Une âme s’échappe'),
 'soul_absorb':('Soul absorbed','Âme absorbée'),
 'soul_chest_open':('Soul Chest opens','Le coffre d’âmes s’ouvre'),
 'soul_complete':('Soul Chest completed','Coffre d’âmes rempli'),
 'zipline_attach':('Zipline hooks on','Accrochage à la tyrolienne'),
 'zipline_arrive':('Zipline stops','Arrêt de la tyrolienne'),
 'zipline_loop':('Zipline cable whirs','Le câble de la tyrolienne vibre'),
}
roots=[ROOT/p for p in ['src/main/resources/assets/zombierool','assets/zombierool','resources/assets/zombierool']]
manifest={}
for name,data in sounds.items():
    data*=.8/max(.001,np.max(np.abs(data)))
    if name=='zipline_loop':
        # Overlap the last/first 50 ms so replay has no discontinuity at the cable loop seam.
        length=int(RATE*.05);blend=np.linspace(0,1,length)
        data=np.concatenate((data[length:-length],data[-length:]*(1-blend)+data[:length]*blend))
    # Remove clicks at one-shot boundaries. The loop sources are authored as 5-second loops.
    if name!='zipline_loop':
        length=min(int(RATE*.012),len(data)//2);data[:length]*=np.linspace(0,1,length);data[-length:]*=np.linspace(1,0,length)
    encoded=None
    for root in roots:
        out=root/'sounds/26_7'/(name+'.ogg');out.parent.mkdir(parents=True,exist_ok=True)
        if encoded is None:
            sf.write(out,data,RATE,format='OGG',subtype='VORBIS');encoded=out.read_bytes()
        else:out.write_bytes(encoded)
    manifest[name]={'duration':len(data)/RATE,'channels':1,'sha256':hashlib.sha256(out.read_bytes()).hexdigest()}
for root in roots:
    definitions=json.loads((root/'sounds.json').read_text())
    for name in sounds:definitions[name]={'subtitle':'subtitles.zombierool.'+name,'sounds':[{'name':'zombierool:26_7/'+name,'stream':False}]}
    (root/'sounds.json').write_text(json.dumps(definitions,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    for lang in (root/'lang').glob('*.json'):
        data=json.loads(lang.read_text(encoding='utf-8'))
        for name,(en,fr) in subtitles.items():data['subtitles.zombierool.'+name]=fr if lang.stem=='fr_fr' else en
        lang.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    for pack in ['rpg-audio','impact-sounds','sci-fi-sounds']:
        license=next((ART/pack).rglob('License.txt'))
        target=root/'licenses'/('kenney-'+pack+'.txt');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(license,target)
(ART/'prepared-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
