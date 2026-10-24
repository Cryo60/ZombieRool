"""Register original Cerberus and shared objective-checker models and translated configuration."""

from pathlib import Path

import json

ROOT=Path(__file__).resolve().parents[1]

ROOTS=[ROOT/'src/main/resources',ROOT/'resources',ROOT]

def write(path,data):

    for root in ROOTS:

        p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

model={'ambientocclusion':False,'render_type':'minecraft:cutout','textures':{'particle':'zombierool:block/cerberus_mark','mark':'zombierool:block/cerberus_mark'},'elements':[{'from':[0,0,15.95],'to':[16,16,16],'faces':{'north':{'texture':'#mark'}}}]}

write('assets/zombierool/models/block/cerberus.json',model)

variants={}

for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:

    for completed in [False,True]:

        for opened in [False,True]:variants[f'completed={str(completed).lower()},facing={facing},open={str(opened).lower()}']={'model':'zombierool:block/cerberus','y':rotation}

write('assets/zombierool/blockstates/cerberus.json',{'variants':variants})

write('assets/zombierool/models/item/cerberus.json',{'parent':'minecraft:item/generated','textures':{'layer0':'zombierool:block/cerberus_mark'}})

for active in [False,True]:

    name='soul_completion_checker'+('_on' if active else '')

    write('assets/zombierool/models/block/'+name+'.json',{'parent':'minecraft:block/cube_column','textures':{'end':'minecraft:block/redstone_lamp_on' if active else 'minecraft:block/redstone_lamp','side':'minecraft:block/chiseled_polished_blackstone'}})

write('assets/zombierool/blockstates/soul_completion_checker.json',{'variants':{'powered=false':{'model':'zombierool:block/soul_completion_checker'},'powered=true':{'model':'zombierool:block/soul_completion_checker_on'}}})

write('assets/zombierool/models/item/soul_completion_checker.json',{'parent':'zombierool:block/soul_completion_checker'})

for block in ['cerberus','soul_completion_checker']:

    write('data/zombierool/loot_tables/blocks/'+block+'.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'zombierool:'+block}]}]})

strings={

    'gui.zombierool.transport.checker.enableChannels.help':('Comma-separated spawner channels to unlock once ALL required objectives are completed. Example: 6,7.','Canaux de spawners à débloquer quand TOUS les objectifs sont complétés. Sépare les numéros par des virgules, exemple : 6,7.'),

    'gui.zombierool.transport.checker.disableChannels.help':('Comma-separated spawner channels to lock on completion. Disable wins if a channel appears in both lists.','Canaux de spawners à bloquer à la complétion. La désactivation est prioritaire si un canal apparaît dans les deux listes.'),

    'gui.zombierool.transport.checker.channelDuration.help':('0: keep the channel changes until the match ends. Positive seconds: temporarily change channels, then restore their original states. Applied once per completion, including across chunk reloads.','0 : conserve les changements jusqu’à la fin de partie. Une durée positive restaure les états précédents après ce nombre de secondes. Application unique par complétion, même après rechargement du chunk.'),

    'block.zombierool.cerberus':('Cerberus','Cerberus'),

    'block.zombierool.soul_completion_checker':('Soul Completion Checker','Soul Completion Checker'),

    'gui.zombierool.transport.cerberus':('Cerberus','Cerberus'),

    'gui.zombierool.transport.soul_checker':('Soul Completion Checker','Soul Completion Checker'),

    'gui.zombierool.transport.objectiveId':('Objective ID','Objective ID'),

    'gui.zombierool.transport.objectiveId.help':('Short automatic ID: S1, S2 for chests; C1, C2 for Cerberus. Choose your own letters and numbers if preferred. Older IDs remain compatible.','ID court automatique : S1, S2 pour les coffres, C1, C2 pour les Cerberus. Tu peux choisir ton propre ID avec des lettres et des chiffres. Les anciens ID restent compatibles.'),

    'gui.zombierool.transport.objectiveIds':('Required IDs','Required IDs'),

    'gui.zombierool.transport.objectiveIds.help':('Separate IDs with commas: S1, S2, C1. Every ID must exist once and be completed. An empty list, missing ID or duplicate ID keeps redstone OFF.','Sépare les ID par des virgules : S1, S2, C1. Chaque ID doit exister une seule fois et être complété. Une liste vide, un ID absent ou dupliqué garde la redstone OFF.'),

    'gui.zombierool.transport.chewTime':('Chewing time (seconds)','Chewing time (seconds)'),

    'gui.zombierool.transport.chewTime.help':('Time spent chewing one corpse before accepting another nearby kill. The kill is counted after chewing.','Durée de mastication avant de pouvoir accepter un nouveau cadavre. La victime est comptée après la mastication.'),

    'gui.zombierool.transport.helper.cerberus':('Place the mark on a wall. Nearby kills feed one corpse at a time; kills while pulling or chewing are ignored. Assign a unique Objective ID to use a completion checker.','Place la marque sur un mur. Un seul cadavre est absorbé à la fois ; les victimes pendant le repas sont ignorées. Assigne un Objective ID unique pour le bloc logique.'),

    'gui.zombierool.transport.helper.soul_checker':('Enter the IDs of any Soul Chests and Cerberus. The block emits redstone 15 only when ALL are completed. Use Enable/Disable Channels for optional spawner actions. Resets automatically each match.','Entre les ID des Soul Chests et Cerberus. Le bloc émet une redstone de force 15 seulement quand TOUS sont complétés. Remise à zéro à chaque partie.'),

    'message.zombierool.soul.duplicate':('Duplicate objective ID: %s. Give every chest and Cerberus a different ID.','Objective ID dupliqué : %s. Donne un ID différent à chaque coffre et Cerberus.'),

    'subtitles.zombierool.cerberus_emerge':('Cerberus barks','Cerberus aboie'),

    'subtitles.zombierool.cerberus_pull':('Cerberus devours a corpse','Cerberus aspire un cadavre'),

    'subtitles.zombierool.cerberus_chew':('Cerberus chews','Cerberus mastique'),
    'subtitles.zombierool.cerberus_idle':('Cerberus barks','Cerberus aboie'),
    'subtitles.zombierool.cerberus_bite':('Cerberus bites','Cerberus mord'),
    'subtitles.zombierool.cerberus_next':('Cerberus finishes a meal','Cerberus finit son repas'),

    'subtitles.zombierool.cerberus_complete':('Cerberus howls','Cerberus hurle'),

}

for root in ROOTS:

    for p in (root/'assets/zombierool/lang').glob('*.json'):

        n=json.loads(p.read_text(encoding='utf8'));n.update({k:v[p.stem=='fr_fr'] for k,v in strings.items()});p.write_text(json.dumps(n,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

metadata={'tool':'image_gen.imagegen','source':'wall-mark-source.png','reference':'C:/Users/ECORAN~1/AppData/Local/Temp/codex-clipboard-71c98f72-c728-4981-8857-5f772a0fc8b9.png','final':'src/main/resources/assets/zombierool/textures/block/cerberus_mark.png','size':[128,128],'integration':'Nearest-neighbor resize, transparency preserved','prompt':'Create an ORIGINAL Minecraft-compatible hellhound wall marking inspired by the attached reference, NOT a copy of the game image. A single frontal snarling wolf head emblem, ears, charcoal jagged fur, glowing burnt-red eyes, open mouth with pointed teeth. Ominous occult red chalk / scorched paint stencil, rough broken red strokes, dark crimson and ember orange accents, compact readable silhouette. Pixel-art, crisp squares, low-resolution Minecraft texture look, intended as a wall decal. No background whatsoever, transparent background. No border, no text, no scenery. Centered head occupies 85% of square canvas, fully inside image. Every exterior pixel transparent. Strong recognisable angular wolf muzzle. Keep it sober and sinister, not cartoony or cute.'}

(ROOT/'assets-source/26.7/cerberus/wall-mark-source.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

print('Cerberus and checker assets/languages prepared.')
