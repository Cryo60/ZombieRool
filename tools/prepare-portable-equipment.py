"""Create native Minecraft geometry for portable equipment; preserve existing language entries."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/zombierool'
generated={}
def write(path,value):
    generated[path]=value
    p=ASSETS/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def box(a,b,texture):
    return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['north','south','east','west','up','down']}}
textures={'metal':'minecraft:block/iron_block','dark':'minecraft:block/gray_concrete','door':'minecraft:block/oxidized_copper','glass':'minecraft:block/light_blue_concrete','wood':'minecraft:block/stripped_birch_log','black':'minecraft:block/black_concrete','gold':'minecraft:block/gold_block','particle':'minecraft:block/iron_block'}
textures.update({'lock_body':'minecraft:block/polished_blackstone','lock_steel':'minecraft:block/netherite_block'})
door=[box([1,4,7],[15,15,9],'door'),box([2,15,7],[3,24,9],'door'),box([13,15,7],[14,24,9],'door'),box([2,23,7],[14,24,9],'door'),box([4,7,6],[7,8,7],'metal')]
truck=[box([4,1,9],[5,21,10],'metal'),box([11,1,9],[12,21,10],'metal'),box([4,20,9],[12,21,10],'metal'),box([3,1,3],[13,2,11],'dark'),box([2,0,9],[5,4,13],'black'),box([11,0,9],[14,4,13],'black')]
mannequin=[box([5,1,5],[11,2,11],'dark'),box([7,2,7],[9,9,9],'metal'),box([4,9,5],[12,18,11],'wood'),box([2,15,6],[14,18,10],'wood'),box([7,18,7],[9,21,9],'wood')]
tail=[box([7,8,10],[9,9,22],'metal'),box([7,9,18],[9,17,22],'door')]
fan=[box([6,6,7],[10,10,9],'gold'),box([7,9,7],[10,16,8],'metal'),box([0,7,7],[7,10,8],'metal'),box([6,0,7],[9,7,8],'metal'),box([9,6,7],[16,9,8],'metal')]
fan_assembled=[box([a+off for a,off in zip(e['from'],[0,16,-4])],[a+off for a,off in zip(e['to'],[0,16,-4])],'metal') for e in fan]
def model(elements):return {'textures':textures,'elements':elements}
write(Path('models/block/deployed_zombie_shield.json'),model(door+truck))
write(Path('models/block/deployed_turbine.json'),model(mannequin+tail))
display={'gui':{'rotation':[20,35,0],'translation':[0,-3,0],'scale':[.55,.55,.55]},'ground':{'translation':[0,2,0],'scale':[.35,.35,.35]},'firstperson_righthand':{'rotation':[0,180,0],'translation':[-3,-1,-5],'scale':[.65,.65,.65]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,1,1],'scale':[.6,.6,.6]}}
standalone_tail=[box([6,0,2],[10,2,14],'metal'),box([6,2,9],[10,12,14],'door'),box([5,11,9],[11,13,14],'metal')]
for name,elements in [('zombie_shield',door+truck),('turbine',mannequin+tail+fan_assembled),('shield_car_door',door),('shield_hand_truck',truck),('turbine_mannequin',mannequin),('turbine_fan',fan),('turbine_tail',standalone_tail)]:
    m=model(elements);m['display']=display;write(Path(f'models/item/{name}.json'),m)
for name in ['deployed_zombie_shield','deployed_turbine']:
    write(Path(f'blockstates/{name}.json'),{'variants':{f'facing={f}':{'model':f'zombierool:block/{name}','y':rot} for f,rot in [('north',0),('east',90),('south',180),('west',270)]}})
lock=[box([4,3,13],[12,10,16],'lock_body'),box([4,9,14],[6,14,16],'lock_steel'),box([10,9,14],[12,14,16],'lock_steel'),box([4,13,14],[12,15,16],'lock_steel'),box([7,5,12],[9,8,13],'black')]
write(Path('models/block/warden_lock.json'),model(lock))
write(Path('models/block/warden_lock_open.json'),model([e for i,e in enumerate(lock) if i!=2]))
write(Path('models/item/warden_lock.json'),{'parent':'zombierool:block/warden_lock'})
write(Path('models/item/warden_key.json'),model([box([3,10,7],[9,12,9],'gold'),box([3,14,7],[9,16,9],'gold'),box([3,10,7],[5,16,9],'gold'),box([7,10,7],[9,16,9],'gold'),box([5,1,7],[7,11,9],'gold'),box([7,1,7],[11,3,9],'gold'),box([7,5,7],[10,7,9],'gold')]))
for offset in range(14):
    for opened in [False,True]:
        shifted=[]
        for i,e in enumerate(lock):
            if opened:continue # Saved lock anchor remains; the visible model falls away client-side.
            shifted.append(box([e['from'][0],e['from'][1],e['from'][2]+offset],[e['to'][0],e['to'][1],e['to'][2]+offset],'black' if i==4 else 'lock_body' if i==0 else 'lock_steel'))
        write(Path(f'models/block/warden_lock_{offset}'+('_open' if opened else '')+'.json'),model(shifted))
write(Path('blockstates/warden_lock.json'),{'variants':{f'facing={f},open={str(opened).lower()},offset={offset}':{'model':f'zombierool:block/warden_lock_{offset}'+('_open' if opened else ''),'y':rot} for f,rot in [('north',0),('east',90),('south',180),('west',270),('up',0),('down',0)] for opened in [False,True] for offset in range(14)}})
en={'item.zombierool.zombie_shield':'Zombie Shield','item.zombierool.turbine':'Turbine','item.zombierool.shield_car_door':'Car door','item.zombierool.shield_hand_truck':'Hand truck','item.zombierool.turbine_mannequin':'Mannequin torso','item.zombierool.turbine_fan':'Fan','item.zombierool.turbine_tail':'Tail fin','item.zombierool.warden_key':"Warden’s Key",'block.zombierool.warden_lock':"Warden’s Lock",'block.zombierool.deployed_turbine':'Turbine','block.zombierool.deployed_zombie_shield':'Zombie Shield','tooltip.zombierool.turbine':'Right click a surface to deploy. Right click to retrieve. Local power: adjacent blocks.','tooltip.zombierool.zombie_shield':'Carried: protects your back. Held: protects your front. Attack: bash. Sneak + right click: place.','message.zombierool.equipment.occupied':'You already carry or have deployed a shield or turbine.','message.zombierool.warden.need_key':"Find the Warden’s Key dropped by Brutus.",'message.zombierool.warden.unlocked':'Warden lock opened.','message.zombierool.warden.acquired':"The team obtained the Warden’s Key. It survives death."}
fr=dict(zip(en.keys(),['Bouclier anti-zombies','Turbine','Portière de voiture','Diable de manutention','Buste de mannequin','Ventilateur','Aileron','Clé du gardien','Verrou du gardien','Turbine','Bouclier anti-zombies','Clic droit sur une surface : déployer. Clic droit : récupérer. Alimente les blocs voisins.','Transporté : protège le dos. En main : protège devant. Attaque : frapper. Maj + clic droit : poser.','Vous portez ou avez déjà déployé un bouclier ou une turbine.','Trouvez la clé du gardien lâchée par Brutus.','Verrou du gardien ouvert.','L’équipe a obtenu la clé du gardien. Elle est conservée après la mort.']))
en.update({'key.zombierool.lunar_helmet':'Secondary equipment','gui.zombierool.buildable.preset_shield':'Preset: Zombie Shield','gui.zombierool.buildable.preset_turbine':'Preset: Turbine','gui.zombierool.buildable.copies_visible':'Total copies (0 = unlimited)','config.zombierool.enemies.wardenKeyLoot':"Warden’s Key — selected boss",'config.zombierool.enemies.wardenKeyLoot.tooltip':'Drop the team key while a closed warden lock remains. Enabled by default for Brutus.','tooltip.zombierool.turbine':'Secondary equipment. Equipment key: hold / stow. Right click: confirm placement preview.','tooltip.zombierool.zombie_shield':'Secondary equipment. Carried on your back. Equipment key: hold / stow. Attack: bash. Sneak + right click: place.','message.zombierool.warden.need_key':"Find the Warden’s Key.",'hud.zombierool.equipment.place':'Right click to place · equipment key to stow'})
fr.update({'key.zombierool.lunar_helmet':'Équipement secondaire','gui.zombierool.buildable.preset_shield':'Preset : Zombie Shield','gui.zombierool.buildable.preset_turbine':'Preset : Turbine','gui.zombierool.buildable.copies_visible':'Exemplaires (0 = illimité)','config.zombierool.enemies.wardenKeyLoot':'Clé du gardien — boss sélectionné','config.zombierool.enemies.wardenKeyLoot.tooltip':'Lâche la clé partagée tant qu’un verrou du gardien reste fermé. Activé par défaut pour Brutus.','tooltip.zombierool.turbine':'Équipement secondaire. Touche équipement : prendre / ranger. Clic droit : confirmer l’aperçu de placement.','tooltip.zombierool.zombie_shield':'Équipement secondaire porté dans le dos. Touche équipement : prendre / ranger. Attaque : frapper. Maj + clic droit : poser.','message.zombierool.warden.need_key':'Trouvez la clé du gardien.','hud.zombierool.equipment.place':'Clic droit pour poser · touche équipement pour ranger'})
en.update({'gui.zombierool.creative.components':'Buildable components','gui.zombierool.buildable.map_recipes':'Map recipes','gui.zombierool.buildable.presets_library':'Preset library','gui.zombierool.buildable.presets_help':'Choose a template to create a new, independent map recipe.','gui.zombierool.buildable.components_inventory':'Parts are available in Creative inventory > ZombieRool > Buildable components.','message.zombierool.warden.loot':'Press [%s] to collect the Warden’s Key','message.zombierool.warden.unlock_hint':'Press [%s] to unlock — Warden’s Key','message.zombierool.warden.locked_hint':'Warden’s Lock — find the Warden’s Key','message.zombierool.warden.team_unlocked':'Warden’s Key unlocked for the team','hud.zombierool.equipment.place':'Left or right click to place · equipment key to stow','tooltip.zombierool.turbine':'Secondary equipment. Equipment key: show / hide placement preview. Left or right click: confirm placement.'})
fr.update({'gui.zombierool.creative.components':'Composants','gui.zombierool.buildable.map_recipes':'Recettes de la carte','gui.zombierool.buildable.presets_library':'Bibliothèque de presets','gui.zombierool.buildable.presets_help':'Choisissez un modèle pour créer une recette indépendante sur votre carte.','gui.zombierool.buildable.components_inventory':'Pièces disponibles dans Inventaire créatif > ZombieRool > Composants.','message.zombierool.warden.loot':'Appuyez sur [%s] pour ramasser la clé du gardien','message.zombierool.warden.unlock_hint':'Appuyez sur [%s] pour déverrouiller — clé du gardien','message.zombierool.warden.locked_hint':'Verrou du gardien — trouvez la clé du gardien','message.zombierool.warden.team_unlocked':'Clé du gardien débloquée pour l’équipe','hud.zombierool.equipment.place':'Clic gauche ou droit pour poser · touche équipement pour ranger','tooltip.zombierool.turbine':'Équipement secondaire. Touche équipement : afficher / masquer l’aperçu. Clic gauche ou droit : confirmer le placement.'})
en['message.zombierool.equipment.placement_blocked']='Cannot place here: aim at solid ground within 4.5 blocks and clear two blocks above it.'
fr['message.zombierool.equipment.placement_blocked']='Pose impossible : visez un sol solide à moins de 4,5 blocs et libérez deux blocs au-dessus.'
en['tooltip.zombierool.turbine']='Equipment key: preview, then click to place. Starts in 2.5 s. F/right click: retrieve; machines switch off 1.5 s later.'
fr['tooltip.zombierool.turbine']='Touche équipement : aperçu, puis clic pour poser. Démarrage : 2,5 s. F/clic droit : récupérer ; extinction des machines 1,5 s après.'
for lang,entries in [('en_us',en),('fr_fr',fr)]:
    p=ASSETS/f'lang/{lang}.json';n=json.loads(p.read_text(encoding='utf8'));n.update(entries);write(Path(f'lang/{lang}.json'),n)
for other in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool']:
    for path,value in generated.items():
        p=other/path;p.parent.mkdir(parents=True,exist_ok=True)
        if path.parts[0]=='lang':
            n=json.loads(p.read_text(encoding='utf8')) if p.exists() else {};n.update(en if path.stem=='en_us' else fr);value=n
        p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Portable equipment models and translations generated.')
