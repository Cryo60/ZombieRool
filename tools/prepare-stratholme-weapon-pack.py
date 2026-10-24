"""Collect authored Stratholme weapons into a save-local pack, with native armor atlases."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, shutil, random

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'src/main/resources'
PACK=ROOT/'examples/stratholme/zombierool/gameplay/weapon_packs/stratholme'
WEAPONS=['fire_wand','shadow_wand','longbow','scarlet_crossbow','scarlet_sword','scarlet_axe','scarlet_halberd','scarlet_shield']

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def collect_model(name,seen):
    if name in seen or not name.startswith('zombierool:'):return
    seen.add(name)
    relative='assets/zombierool/models/'+name.split(':',1)[1]+'.json'
    source=PACK/relative if (PACK/relative).exists() else SOURCE/relative
    if not source.exists():raise FileNotFoundError(source)
    target=PACK/relative;target.parent.mkdir(parents=True,exist_ok=True)
    if source!=target:shutil.copy2(source,target)
    data=json.loads(source.read_text(encoding='utf-8'))
    if 'parent' in data:collect_model(data['parent'],seen)
    for row in data.get('overrides',[]):collect_model(row['model'],seen)
    for texture in data.get('textures',{}).values():
        if not texture.startswith('zombierool:'):continue
        asset='assets/zombierool/textures/'+texture.split(':',1)[1]+'.png'
        dest=PACK/asset;dest.parent.mkdir(parents=True,exist_ok=True)
        if not dest.exists():shutil.copy2(SOURCE/asset,dest)

def main():
    armor={'gambeson':.2,'chainmail':.35,'plate':.5}
    metadata={'id':'stratholme','name':'Stratholme Arsenal','format':1,'enabled':True,'armor':armor,'armor_prices':{'gambeson':500,'chainmail':1500,'plate':3000},'armor_repairs':{'gambeson':150,'chainmail':300,'plate':500},'armor_durability':{'gambeson':180,'chainmail':320,'plate':480}}
    write(PACK/'pack.json',metadata)
    write(PACK/'assets/zombierool/map_armor.json',metadata)
    seen=set();sounds=json.loads((PACK/'assets/zombierool/sounds.json').read_text(encoding='utf-8')) if (PACK/'assets/zombierool/sounds.json').exists() else {};sound_source=json.loads((SOURCE/'assets/zombierool/sounds.json').read_text(encoding='utf-8'))
    for id in WEAPONS:
        source=PACK/f'weapons/{id}.json' if (PACK/f'weapons/{id}.json').exists() else SOURCE/f'data/zombierool/gameplay/weapons/{id}.json'
        definition=json.loads(source.read_text(encoding='utf-8'))
        if id.startswith('scarlet_') and id!='scarlet_crossbow':
            kind=id.removeprefix('scarlet_')
            if kind=='halberd':channels={'fpX':[0,-.04,-.12],'fpY':[0,.07,-.08],'fpZ':[0,0,-.17],'fpRy':[-12,6,-12],'fpRz':[12,8,-25],'fpRx':[-35,-30,65],'fpScale':[.8,0,0],'tpX':[-.95,-.6,1.3]}
            elif kind=='axe':channels={'fpX':[0,-.08,-.2],'fpY':[0,.18,-.12],'fpZ':[0,0,-.15],'fpRy':[-25,0,0],'fpRz':[-18,22,-25],'fpRx':[-8,-50,72],'fpScale':[.85,0,0],'tpX':[-1,-.7,1.2]}
            else:channels={'fpX':[0,.07,-.35],'fpY':[0,.08,-.08],'fpZ':[0,0,-.12],'fpRy':[-20,0,0],'fpRz':[-12,-25,70],'fpRx':[-12,-20,35],'fpScale':[.85,0,0],'tpX':[-1.1,-.45,1]}
            channels.update(tpY=[-.15,0,.3],tpZ=[-.1,0,-.3]);definition['animation']=channels
        write(PACK/f'weapons/{id}.json',definition);collect_model('zombierool:item/'+id,seen)
        for event in definition['sounds'].values():
            key=event.removeprefix('zombierool:')
            if key not in sound_source:continue
            sounds[key]=dict(sound_source[key],replace=True)
    collect_model('zombierool:item/scarlet_guard',seen)
    for definition in sounds.values():
        for row in definition.get('sounds',[]):
            if isinstance(row,dict) and row.get('type')=='event':continue
            name=row if isinstance(row,str) else row['name']
            if ':' in name and not name.startswith('zombierool:'):continue
            relative='assets/zombierool/sounds/'+name.removeprefix('zombierool:')+'.ogg'
            target=PACK/relative;target.parent.mkdir(parents=True,exist_ok=True)
            if not target.exists():shutil.copy2(SOURCE/relative,target)
    write(PACK/'assets/zombierool/sounds.json',sounds)
    for locale in ['en_us','fr_fr']:
        source=json.loads((SOURCE/f'assets/zombierool/lang/{locale}.json').read_text(encoding='utf-8'))
        lang=json.loads((PACK/f'assets/zombierool/lang/{locale}.json').read_text(encoding='utf-8')) if (PACK/f'assets/zombierool/lang/{locale}.json').exists() else {}
        lang.update({k:v for k,v in source.items() if any(id in k for id in WEAPONS+['scarlet_guard'])})
        for armor_set,title in [('gambeson','Padded Gambeson'),('chainmail','Lordaeron Chainmail'),('plate','Lordaeron Plate')]:
            if locale=='fr_fr':title={'gambeson':'Gambison','chainmail':'Cotte de mailles de Lordaeron','plate':'Armure de plates de Lordaeron'}[armor_set]
            for piece,name in [('helmet','Helm'),('chestplate','Chestpiece'),('leggings','Leggings'),('boots','Boots')]:
                label=name if locale=='en_us' else {'helmet':'Casque','chestplate':'Plastron','leggings':'JambiÃ¨res','boots':'Bottes'}[piece]
                lang[f'item.zombierool.map_{armor_set}_{piece}']=title+' - '+name
                write(PACK/f'assets/zombierool/models/item/map_{armor_set}_{piece}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'zombierool:item/armor/{armor_set}_{piece}'}})
            lang[f'block.zombierool.map_{armor_set}_station']=title+(' Station' if locale=='en_us' else ' - Armurerie')
        lang.update({'message.zombierool.map_armor.preview':'Buy: %s points · Repair: %s points' if locale=='en_us' else 'Achat : %s points · Réparation : %s points','message.zombierool.map_armor.unavailable':'Armor is unavailable outside a running match.' if locale=='en_us' else 'Armure indisponible hors partie.','message.zombierool.map_armor.funds':'Not enough points.' if locale=='en_us' else 'Points insuffisants.','message.zombierool.map_armor.full':'Your armor is already in perfect condition.' if locale=='en_us' else 'Ton armure est déjà en parfait état.','message.zombierool.map_armor.repaired':'Armor repaired.' if locale=='en_us' else 'Armure réparée.','message.zombierool.map_armor.equipped':'Armor equipped.' if locale=='en_us' else 'Armure équipée.'})
        write(PACK/f'assets/zombierool/lang/{locale}.json',lang)
    palettes={'gambeson':('#796f56','#403d32','#b8a681'),'chainmail':('#777f83','#30383d','#bec3c4'),'plate':('#8e969d','#343b44','#c4ab67')}
    for armor_set,colors in palettes.items():
        station=f'map_{armor_set}_station'
        write(PACK/f'assets/zombierool/blockstates/{station}.json',{'variants':{'':{'model':'zombierool:block/'+station}}})
        write(PACK/f'assets/zombierool/models/item/{station}.json',{'parent':'zombierool:block/'+station})
        elements=[]
        for a,b,texture in [([1,0,2],[15,2,14],'wood'),([7,2,7],[9,7,9],'wood'),([4,5,5],[12,13,11],'armor'),([1,9,5],[4,13,11],'armor'),([12,9,5],[15,13,11],'armor'),([5,13,5],[11,19,11],'armor')]:
            elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+texture,'uv':[16,16,24,28] if texture=='armor' else [0,0,16,16]} for face in ['north','south','east','west','up','down']}})
        write(PACK/f'assets/zombierool/models/block/{station}.json',{'textures':{'wood':'minecraft:block/dark_oak_planks','armor':f'zombierool:models/armor/{armor_set}_layer_1','particle':'minecraft:block/dark_oak_planks'},'elements':elements,'display':{'gui':{'rotation':[25,225,0],'scale':[.65,.65,.65]}}})
        rng=random.Random(armor_set)
        for layer in [1,2]:
            image=Image.new('RGBA',(64,32),colors[0]);draw=ImageDraw.Draw(image)
            for y in range(32):
                for x in range(64):
                    if armor_set=='chainmail':shade=colors[2] if (x+y%2)%3==0 and y%2==0 else colors[1] if (x+y)%3==1 else colors[0]
                    elif armor_set=='gambeson':shade=colors[1] if (x+y)%6==0 or (x-y)%6==0 else colors[0]
                    else:shade=colors[2] if x%8==0 or y%8==0 else colors[1] if x%8==7 else colors[0]
                    draw.point((x,y),fill=shade)
            # Belt and reinforced seams follow the vanilla biped armor UV layout.
            draw.rectangle((16,27,39,29),fill='#453a2e');draw.rectangle((21,27,24,29),fill='#c4aa66')
            target=PACK/f'assets/zombierool/textures/models/armor/{armor_set}_layer_{layer}.png';target.parent.mkdir(parents=True,exist_ok=True);image.save(target)
        for piece in ['helmet','chestplate','leggings','boots']:
            image=Image.new('RGBA',(16,16));draw=ImageDraw.Draw(image)
            if piece=='helmet':draw.polygon([(3,3),(6,1),(10,1),(13,3),(13,11),(10,12),(10,7),(6,7),(6,12),(3,11)],fill=colors[0],outline=colors[1])
            elif piece=='chestplate':draw.polygon([(2,2),(6,2),(7,4),(9,4),(10,2),(14,2),(15,6),(12,7),(12,14),(4,14),(4,7),(1,6)],fill=colors[0],outline=colors[1]);draw.line((5,12,11,12),fill=colors[2])
            elif piece=='leggings':draw.polygon([(4,2),(12,2),(12,14),(9,14),(9,7),(7,7),(7,14),(4,14)],fill=colors[0],outline=colors[1])
            else:draw.rectangle((3,5,6,12),fill=colors[0],outline=colors[1]);draw.rectangle((9,5,12,12),fill=colors[0],outline=colors[1]);draw.rectangle((2,11,6,14),fill=colors[2]);draw.rectangle((9,11,13,14),fill=colors[2])
            target=PACK/f'assets/zombierool/textures/item/armor/{armor_set}_{piece}.png';target.parent.mkdir(parents=True,exist_ok=True);image.save(target)
    print('Save-local Stratholme pack prepared:',len(WEAPONS),'weapons and 12 armor pieces.')

if __name__=='__main__':main()
