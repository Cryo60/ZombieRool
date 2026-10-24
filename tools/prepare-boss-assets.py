"""Prepare spawner textures and selected T6 sounds. Original game models are not substituted here."""
from pathlib import Path
import sys,json,io,shutil
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'build/audio-python'))
sys.path.insert(0,str(ROOT/'build/video-python'))
import soundfile as sf
from PIL import Image,ImageDraw
DESKTOP=Path.home()/'Desktop'
selection={
    'panzer_spawn':['Panzer/incoming_alarm.SN50_F662ED90.wav'],
    'panzer_grab':['Panzer/grab.LN50_AB74F930.wav'],
    'panzer_flame':['Panzer/fire_00.LN50_B0B136D1.wav','Panzer/fire_01.LN50_7D3953D2.wav','Panzer/fire_02.LN50_49C170D3.wav'],
    'panzer_melee':['Panzer/melee_a.LN50_5C7F0687.wav','Panzer/melee_b.LN50_29072388.wav'],
    'panzer_death':['Panzer/death_00.LN50_27F0C3E1.wav'],
    'brutus_spawn':['Brutus/brutus_spawn_2d.SN65_9E97E54E.wav'],
    'brutus_melee':['Brutus/brutus_swing_00.LN65_E8A01594.wav','Brutus/brutus_swing_01.LN65_B5283295.wav','Brutus/brutus_swing_02.LN65_81B04F96.wav'],
    'brutus_death':['Brutus/death.LN65_49CA8513.wav'],
}
manifest=[]
for resource in [ROOT/'src/main/resources',ROOT/'resources',ROOT]:
    assets=resource/'assets/zombierool'
    sounds=json.loads((assets/'sounds.json').read_text(encoding='utf8'))
    for event,files in selection.items():
        entries=[]
        for i,file in enumerate(files):
            source=DESKTOP/'Sons Mini Boss T6'/file
            audio,rate=sf.read(source,always_2d=True)
            # Arrival cues are stereo/global; positional attacks are mono.
            if not event.endswith('_spawn'):audio=audio.mean(axis=1)
            path=assets/'sounds/mini_boss'/f'{event}_{i}.ogg';path.parent.mkdir(parents=True,exist_ok=True)
            sf.write(path,audio,rate,format='OGG',subtype='VORBIS')
            entries.append({'name':f'zombierool:mini_boss/{event}_{i}','stream':False})
            if resource==ROOT/'src/main/resources':manifest.append({'event':event,'source':str(source),'seconds':round(len(audio)/rate,3),'output':str(path)})
        sounds[event]={'subtitle':f'subtitles.zombierool.{event}','sounds':entries}
    (assets/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    for category in ['boss','minion']:
        shutil.copy2(DESKTOP/f'spawner_{category}.png',assets/'textures/block'/f'spawner_{category}.png')
    state=json.loads((ROOT/'src/main/resources/assets/zombierool/blockstates/universal_spawner.json').read_text())
    (assets/'blockstates').mkdir(exist_ok=True);(assets/'models/block').mkdir(parents=True,exist_ok=True)
    for kind in ['panzer','brutus','napalm_minion','skeleton_minion']:
        category='boss' if kind in ['panzer','brutus'] else 'minion'
        for active in [False,True]:
            name=f'universal_spawner_{kind}'+('_active' if active else '')
            model={'parent':'block/cube_all','render_type':'minecraft:translucent','textures':{'all':f'zombierool:block/spawner_{category}'}}
            (assets/'models/block'/f'{name}.json').write_text(json.dumps(model,indent=2)+'\n')
            state['variants'][f'active={str(active).lower()},mob_type={kind}']={'model':f'zombierool:block/{name}'}
    (assets/'blockstates/universal_spawner.json').write_text(json.dumps(state,indent=2)+'\n')
    # Native, provisional Minecraft textures paired with the code-generated cube models.
    for kind in ['napalm_minion']:
        base={'panzer':(68,73,58),'brutus':(40,47,65),'napalm_minion':(71,34,20),'skeleton_minion':(197,188,155)}[kind]
        image=Image.new('RGBA',(128,128),base+(255,));draw=ImageDraw.Draw(image)
        for y in range(128):
            for x in range(128):
                delta=((x*17+y*29)%11)-5
                image.putpixel((x,y),tuple(max(0,min(255,c+delta)) for c in base)+(255,))
        draw.rectangle((0,64,127,95),fill=(95,98,86,255) if kind=='panzer' else (29,31,36,255))
        if kind in ['panzer','brutus']:
            draw.rectangle((8,10,15,12),fill=(230,105,16,255));draw.rectangle((8,13,15,15),fill=(20,22,20,255))
        if kind=='napalm_minion':
            for x in range(0,64,6):draw.line((x,0,x+18,63),fill=(245,112,18,255),width=2)
        if kind=='skeleton_minion':
            draw.rectangle((8,10,10,12),fill=(18,17,14,255));draw.rectangle((13,10,15,12),fill=(18,17,14,255));draw.rectangle((10,14,13,15),fill=(44,40,30,255))
        folder=assets/'textures/entities';folder.mkdir(exist_ok=True);image.save(folder/f'{kind}_provisional.png')
    names={'panzer':'Panzer','brutus':'Brutus','napalm_minion':'Zombie brasero','skeleton_minion':'Squelette'}
    for lang in ['fr_fr','en_us']:
        path=assets/'lang'/f'{lang}.json';path.parent.mkdir(exist_ok=True);data=json.loads((ROOT/'src/main/resources/assets/zombierool/lang'/f'{lang}.json').read_text(encoding='utf8'))
        for kind,name in names.items():
            if lang=='en_us':name={'napalm_minion':'Napalm minion','skeleton_minion':'Skeleton minion'}.get(kind,name)
            data['mob.zombierool.'+kind]=name;data['entity.zombierool.'+kind]=name
        for event in selection:data['subtitles.zombierool.'+event]=event.replace('_',' ').capitalize()
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
path=ROOT/'assets-source/26.7/audio/mini-boss-manifest.json';path.write_text(json.dumps(manifest,indent=2)+'\n')
print('Prepared textures and 8 original T6 sound events, including the exact requested spawn cues.')
