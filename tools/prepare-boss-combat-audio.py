from pathlib import Path
import json,sys,hashlib,shutil
r=Path('D:/Dev/ZombieRool');sys.path.insert(0,str(r/'build/audio-python'));import soundfile as sf
source=Path.home()/'Desktop/Sons Mini Boss T6'
selection={'panzer_step':['Panzer/anim_decepticon_lg_run_'+n+'.LN50_'+h+'.wav' for n,h in [('01','5AD1DCE9'),('02','2759F9EA'),('03','F3E216EB'),('04','C06A33EC')]],'panzer_land':['Panzer/land_00.LN50_F4D193D4.wav','Panzer/land_01.LN50_C159B0D5.wav','Panzer/land_02.LN50_8DE1CDD6.wav'],'panzer_boost':['Panzer/start.LN50_D08059AB.wav'],'panzer_flame':['Panzer/start.LN50_E972D9EA.wav'],'panzer_flame_loop':['Panzer/loop.LL50_C5B6C4E0.wav'],'panzer_flame_end':['Panzer/stop.LN50_9BC9D21C.wav'],'brutus_step':['Brutus/brutus_step_'+n+'.LN65_'+h+'.wav' for n,h in [('00','8ED10010'),('01','5B591D11'),('02','27E13A12'),('03','F4695713'),('04','C0F17414')]],'brutus_slam':['Brutus/clang.LN65_93ACED8A.wav']}
translations={'en_us':['Panzer footsteps','Panzer lands','Panzer thrusters','Panzer ignites flamethrower','Panzer burns','Panzer stops flamethrower','Brutus footsteps','Brutus metal impact'],'fr_fr':['Pas du Panzer','Atterrissage du Panzer','Propulseurs du Panzer','Allumage du lance-flammes du Panzer','Lance-flammes du Panzer','Arrêt du lance-flammes du Panzer','Pas de Brutus','Impact métallique de Brutus'],'de_de':['Panzer-Schritte','Panzer landet','Panzer-Triebwerke','Panzer entzündet Flammenwerfer','Panzer-Flammenwerfer','Panzer stoppt Flammenwerfer','Brutus-Schritte','Brutus-Metallaufprall'],'es_es':['Pasos del Panzer','Aterrizaje del Panzer','Propulsores del Panzer','Panzer enciende el lanzallamas','Lanzallamas del Panzer','Panzer apaga el lanzallamas','Pasos de Brutus','Impacto metálico de Brutus'],'pt_br':['Passos do Panzer','Pouso do Panzer','Propulsores do Panzer','Panzer acende o lança-chamas','Lança-chamas do Panzer','Panzer desliga o lança-chamas','Passos de Brutus','Impacto metálico de Brutus'],'ru_ru':['Шаги Панцера','Приземление Панцера','Двигатели Панцера','Панцер включает огнемёт','Огнемёт Панцера','Панцер выключает огнемёт','Шаги Брутуса','Металлический удар Брутуса']}
assets=r/'src/main/resources/assets/zombierool';sounds=json.loads((assets/'sounds.json').read_text(encoding='utf8'));manifest=[]
for event,files in selection.items():
 entries=[]
 for i,file in enumerate(files):
  src=source/file;audio,rate=sf.read(src,always_2d=True);dest=assets/'sounds/mini_boss'/f'{event}_{i}.ogg';sf.write(dest,audio.mean(axis=1),rate,format='OGG',subtype='VORBIS');entries.append({'name':f'zombierool:mini_boss/{event}_{i}','stream':False});manifest.append({'event':event,'source':str(src),'sha256':hashlib.sha256(src.read_bytes()).hexdigest(),'seconds':len(audio)/rate})
  for mirror in [r/'resources/assets/zombierool',r/'assets/zombierool']:shutil.copy2(dest,mirror/'sounds/mini_boss'/dest.name)
 sounds[event]={'subtitle':'subtitles.zombierool.'+event,'sounds':entries}
(assets/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
for lang,labels in translations.items():
 p=assets/'lang'/f'{lang}.json';data=json.loads(p.read_text(encoding='utf8'));data.update({'subtitles.zombierool.'+key:value for key,value in zip(selection,labels)});p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 for mirror in [r/'resources/assets/zombierool',r/'assets/zombierool']:shutil.copy2(p,mirror/'lang'/p.name)
for mirror in [r/'resources/assets/zombierool',r/'assets/zombierool']:shutil.copy2(assets/'sounds.json',mirror/'sounds.json')
(r/'assets-source/26.7/audio/boss-combat-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Original T6 combat audio converted, with subtitles in six languages')
