"""Build authored station models, player keyframes, and pneumatic helmet foley."""

from pathlib import Path

import json,math,wave,struct,random,subprocess,shutil

ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/zombierool'

def save(path,value):

    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def cube(lo,hi,tex):return {'from':lo,'to':hi,'faces':{face:{'texture':'#'+tex} for face in ['north','south','east','west','up','down']}}

textures={'metal':'minecraft:block/iron_block','dark':'minecraft:block/polished_deepslate','rim':'minecraft:block/gray_concrete','glass':'minecraft:block/light_blue_stained_glass','lamp':'minecraft:block/sea_lantern','panel':'minecraft:block/black_concrete','button':'minecraft:block/lime_concrete'}

bottom=[cube([1,0,1],[15,2,15],'dark'),cube([2,2,2],[14,14,14],'metal'),cube([1,14,1],[15,16,15],'rim'),cube([4,5,1.8],[12,12,2.1],'panel'),cube([5,6,1.6],[7,8,1.9],'button'),cube([9,6,1.6],[11,8,1.9],'lamp')]

upper=[cube([1,14,1],[15,16,15],'metal'),cube([1,0,1],[3,14,3],'metal'),cube([13,0,1],[15,14,3],'metal'),cube([1,0,13],[3,14,15],'metal'),cube([13,0,13],[15,14,15],'metal'),cube([3,0,12],[13,14,14],'dark'),cube([5,2,5],[11,4,11],'rim'),cube([4,4,4],[12,12,12],'metal'),cube([4,6,3.5],[12,10,4.2],'glass'),cube([6,10,3],[10,12,4],'rim'),cube([4,13.8,4],[12,14,12],'lamp')]

for half,elements in [('lower',bottom),('upper',upper)]:save(A/f'models/block/lunar_helmet_station_{half}.json',{'parent':'block/block','render_type':'minecraft:cutout','textures':{'particle':textures['metal'],**textures},'elements':elements})

variants={}

for direction,rotation in [('north',0),('east',90),('south',180),('west',270)]:

    for half in ['lower','upper']:variants[f'facing={direction},half={half}']={'model':f'zombierool:block/lunar_helmet_station_{half}','y':rotation}

save(A/'blockstates/lunar_helmet_station.json',{'variants':variants})

# Item has the entire two-block silhouette, scaled to fit an inventory frame.

full=bottom+[dict(row,**{'from':[row['from'][0],row['from'][1]+16,row['from'][2]],'to':[row['to'][0],row['to'][1]+16,row['to'][2]]}) for row in upper]

save(A/'models/item/lunar_helmet_station.json',{'parent':'block/block','textures':textures,'elements':full,'display':{'gui':{'rotation':[25,-35,0],'translation':[0,-4,0],'scale':[.38,.38,.38]},'ground':{'scale':[.2,.2,.2]},'thirdperson_righthand':{'scale':[.3,.3,.3]},'firstperson_righthand':{'scale':[.35,.35,.35]}}})

save(A/'models/item/lunar_helmet.json',{'parent':'block/block','textures':textures,'elements':[cube([3,3,3],[13,13,13],'metal'),cube([3,5,2.8],[13,11,3.2],'glass'),cube([2,2,2],[14,4,14],'rim')],'display':{'gui':{'rotation':[25,-35,0],'scale':[.85,.85,.85]},'thirdperson_righthand':{'scale':[.55,.55,.55]}}})

save(ROOT/'src/main/resources/data/zombierool/loot_tables/blocks/lunar_helmet_station.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'zombierool:lunar_helmet_station'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

equip=[(0,[-10,0,0]),(.2,[-55,-10,-12]),(.55,[-115,-15,-24]),(.85,[-157,-12,-20]),(1.05,[-157,-12,-20]),(1.35,[-55,-10,-10]),(1.6,[0,0,0])]

remove=[(0,[0,0,0]),(.25,[-100,-12,-20]),(.55,[-157,-12,-20]),(.7,[-157,-12,-20]),(1.05,[-90,-12,-24]),(1.4,[-20,0,0]),(1.6,[0,0,0])]

for name,keys in [('helmet_equip',equip),('helmet_remove',remove)]:

    bones={}

    for side,sign in [('right_arm',1),('left_arm',-1)]:bones[side]={'rotation':{str(t):{'vector':[x,y*sign,z*sign],'lerp_mode':'catmullrom'} for t,(x,y,z) in keys}}

    bones['head']={'rotation':{'0.0':[0,0,0],'.4':[7,0,0],'1.15':[3,0,0],'1.6':[0,0,0]}}

    save(A/f'player_animation/{name}.json',{'format_version':'1.8.0','animations':{name:{'animation_length':1.6,'bones':bones}}})

sounds=json.loads((A/'sounds.json').read_text(encoding='utf-8'))

scratch=ROOT/'build/lunar-equipment';scratch.mkdir(parents=True,exist_ok=True)

random.seed(491);rate=44100

for name,duration in [('helmet_handle',.65),('helmet_seal',.62),('helmet_unseal',.85),('helmet_breath',2.2)]:

    samples=[];low=0

    for i in range(round(rate*duration)):

        t=i/rate;noise=random.uniform(-1,1);low=.83*low+.17*noise

        if name=='helmet_handle':v=low*.23*math.exp(-7*t)+math.sin(2*math.pi*230*t)*.08*math.exp(-24*t)

        elif name=='helmet_breath':v=low*.15*math.sin(math.pi*t/duration)**2*(.6+.4*math.sin(2*math.pi*t/duration))

        else:

            hiss=(noise-low)*(.13 if name=='helmet_seal' else .21)*min(1,t*35)*math.exp(-t*5)

            latch=sum(math.sin(2*math.pi*f*max(0,t-at))*.2*math.exp(-80*max(0,t-at)) if t>=at else 0 for f,at in [(410,.06),(190,.18 if name=='helmet_seal' else .03)])

            v=hiss+latch

        samples.append(max(-32767,min(32767,round(v*32767))))

    wav=scratch/(name+'.wav')

    with wave.open(str(wav),'wb') as w:w.setparams((1,2,rate,0,'NONE','not compressed'));w.writeframes(struct.pack('<'+'h'*len(samples),*samples))

    dest=A/'sounds/lunar';dest.mkdir(exist_ok=True)

    subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-y','-loglevel','error','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(dest/(name+'.ogg'))],check=True)

    sounds[name]={'subtitle':'subtitles.zombierool.'+name,'sounds':['zombierool:lunar/'+name]}

save(A/'sounds.json',sounds)

fr={'block.zombierool.lunar_helmet_station':'Station de casque lunaire','item.zombierool.lunar_helmet':'Casque lunaire','key.zombierool.lunar_helmet':'Mettre / enlever le casque lunaire','message.zombierool.helmet_acquired':'Casque récupéré — [%s] pour le retirer ou le remettre','message.zombierool.helmet_owned':'Vous possédez déjà le casque lunaire','hud.zombierool.helmet_on':'Casque : équipé [%s]','hud.zombierool.helmet_off':'Casque : retiré [%s]','hud.zombierool.oxygen':'Oxygène : %s s','config.zombierool.lunar.gravity':'Gravité (1 = Terre, 0.167 = Lune)','config.zombierool.lunar.vacuum':'Vide : casque nécessaire','config.zombierool.lunar.oxygenSeconds':'Réserve d’air sans casque (secondes)','subtitles.zombierool.helmet_handle':'Manipulation du casque','subtitles.zombierool.helmet_seal':'Casque verrouillé','subtitles.zombierool.helmet_unseal':'Dépressurisation du casque','subtitles.zombierool.helmet_breath':'Respiration dans le casque','message.zombierool.helmet_take':'[%s] Récupérer le casque lunaire','config.zombierool.value.infernal':'Infernal','config.zombierool.lunar':'Environnement lunaire','config.zombierool.hellhound_skin_preset.infernal':'Infernal'}

en={'block.zombierool.lunar_helmet_station':'Lunar helmet station','item.zombierool.lunar_helmet':'Lunar helmet','key.zombierool.lunar_helmet':'Equip / remove lunar helmet','message.zombierool.helmet_acquired':'Helmet acquired — [%s] to remove or equip','message.zombierool.helmet_owned':'You already own a lunar helmet','hud.zombierool.helmet_on':'Helmet: sealed [%s]','hud.zombierool.helmet_off':'Helmet: removed [%s]','hud.zombierool.oxygen':'Oxygen: %s s','config.zombierool.lunar.gravity':'Gravity (1 = Earth, 0.167 = Moon)','config.zombierool.lunar.vacuum':'Vacuum: helmet required','config.zombierool.lunar.oxygenSeconds':'Air without helmet (seconds)','subtitles.zombierool.helmet_handle':'Helmet handling','subtitles.zombierool.helmet_seal':'Helmet seals','subtitles.zombierool.helmet_unseal':'Helmet depressurizes','subtitles.zombierool.helmet_breath':'Helmet breathing','message.zombierool.helmet_take':'[%s] Take lunar helmet','config.zombierool.value.infernal':'Infernal','config.zombierool.lunar':'Lunar environment','config.zombierool.hellhound_skin_preset.infernal':'Infernal'}

for p in (A/'lang').glob('*.json'):

    lang=json.loads(p.read_text(encoding='utf-8'));lang.update(fr if p.stem=='fr_fr' else en)

    # Match the existing menu prefix, independently of older translations.

    for key in list(lang):

        if key.endswith('.t5') and ('hound' in key):lang[key.rsplit('.',1)[0]+'.infernal']='Infernal'

    save(p,lang)

# Original Moon visor overlay, decoded without painting or approximating its silhouette.
import struct,io
from zipfile import ZipFile
import sys
sys.path[:0]=[str(ROOT/'build/video-python')]
from PIL import Image
with ZipFile('D:/Jeux/pluto_t5_full_game/main/iw_36.iwd') as archive:
 raw=archive.read('images/zom_hazmatmask_001.iwi')
w,h=struct.unpack_from('<HH',raw,6);assert raw[4]==13
size=w*h;header=struct.pack('<I6I11I',124,0x81007,h,w,size,0,1,*([0]*11))+struct.pack('<II4s5I',32,4,b'DXT5',0,0,0,0,0)+struct.pack('<5I',0x1000,0,0,0,0)
Image.open(io.BytesIO(b'DDS '+header+raw[-size:])).save(A/'textures/gui/moon_visor.png')
import runpy
runpy.run_path(str(ROOT/'tools/retarget-lunar-helmet-animation.py'))['main']()
print('Prepared station, retargeted player animations, helmet foley, translations')
