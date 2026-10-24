"""Validate development resources without opening or modifying any Minecraft world."""
from pathlib import Path
from PIL import Image
import json
import math
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/zombierool'
VANILLA = zipfile.ZipFile('C:/Users/ecorange1/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar')
checks = 0


def check(condition, description):
    global checks
    if not condition:
        raise AssertionError(description)
    checks += 1


names = ['jump_link', 'zipline', 'crawler_wall_link', 'one_way_gate', 'soul_chest', 'cerberus', 'soul_completion_checker',
         'secret_weapon_rack', 'iron_defense_door', 'teleporter', 'traversal_node', 'activator']
channels = ['alpha', 'beta', 'omega', 'ultima', 'gamma', 'delta', 'epsilon', 'zeta',
            'eta', 'theta', 'iota', 'kappa', 'lambda', 'sigma']
names += [channel + '_' + role for channel in channels for role in ['activator', 'receptor']]
seen = set()


def model(identifier):
    namespace, location = identifier.split(':', 1)
    if identifier in seen:
        return
    seen.add(identifier)
    if namespace == 'minecraft':
        check('assets/minecraft/models/' + location + '.json' in VANILLA.namelist(), identifier)
        return
    check(namespace == 'zombierool', 'Unexpected model namespace: ' + identifier)
    path = ASSETS / 'models' / (location + '.json')
    check(path.exists(), 'Missing model ' + identifier)
    data = json.loads(path.read_text())
    if 'parent' in data:
        model(data['parent'])
    for texture in data.get('textures', {}).values():
        if texture.startswith('zombierool:'):
            check((ASSETS / 'textures' / (texture.split(':')[1] + '.png')).exists(), texture)
    for element in data.get('elements', []):
        check(all(math.isfinite(v) and -16 <= v <= 32 for v in element['from'] + element['to']),
              'Illegal cuboid bounds in ' + identifier)
        check(all(a <= b for a, b in zip(element['from'], element['to'])), identifier)
        if 'rotation' in element:
            check(element['rotation']['angle'] in [-45, -22.5, 0, 22.5, 45], identifier)
        for face in element['faces'].values():
            if 'uv' in face:
                check(all(math.isfinite(v) and 0 <= v <= 16 for v in face['uv']), identifier)


for name in names:
    path = ASSETS / 'blockstates' / (name + '.json')
    data = json.loads(path.read_text())
    for variant in data['variants'].values():
        for entry in variant if isinstance(variant, list) else [variant]:
            model(entry['model'])
    model('zombierool:item/' + name)

for name in ['logic_iron_barricade', 'teleporter_radar', 'teleporter_radar_on']:
    im = Image.open(ASSETS / 'textures/block' / (name + '.png')).convert('RGBA')
    alpha = im.getchannel('A')
    check(alpha.getextrema()[0] == 0 and alpha.getextrema()[1] >= 250, name + ' must preserve opaque surfaces and transparent gaps')
    if name.startswith('teleporter_'):
        meta = json.loads((ASSETS / 'textures/block' / (name + '.png.mcmeta')).read_text())['animation']
        check(im.size == (96, 96 * 32) and meta['width'] == meta['height'] == 96, name)
        check(meta['frametime'] == 2, name)
        r, g, b, a = im.getpixel((10, 48))
        check(g > r and g > b and a == 255, name + ' must have a green background')
    else:
        check(im.size==(16,16),'Iron barricade must use a 16x16 texture')

def inherited(identifier):
    path=ASSETS/'models'/(identifier+'.json')
    data=json.loads(path.read_text())
    if data.get('parent','').startswith('zombierool:'):
        parent=inherited(data['parent'].split(':',1)[1])
        for key,value in data.items():
            if key in ['textures','display']:parent.setdefault(key,{}).update(value)
            else:parent[key]=value
        return parent
    return data

# Project inventory cuboid corners with the actual GUI transform; tall icons must fit one slot.
import itertools
for name in ['zipline','secret_weapon_rack','blind_buy_cabinet','iron_defense_door']:
    item=inherited('item/'+name);gui=item['display']['gui']
    rx,ry,rz=[math.radians(v) for v in gui['rotation']]
    for element in item['elements']:
        for corner in itertools.product(*zip(element['from'],element['to'])):
            x,y,z=[(v-8)*s for v,s in zip(corner,gui['scale'])]
            x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
            x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
            y=y*math.cos(rx)-z*math.sin(rx)
            check(abs(x+gui['translation'][0])<=8 and abs(y+gui['translation'][1])<=8,name+' icon exceeds its inventory slot')

for lower in (ASSETS/'models/block').glob('iron_defense_door_bottom_*.json'):
    data=json.loads(lower.read_text())
    check(data['parent']=='zombierool:block/'+lower.stem.replace('iron_defense_door','defense_door'),'Iron door lower half must retain the original defense door texture and geometry')
chest=json.loads((ASSETS/'models/block/soul_chest_open.json').read_text())
lid=next(e for e in chest['elements'] if 'rotation' in e)
check(lid['rotation']['angle']>0,'The lid must lift upward around its rear hinge')
finished=json.loads((ASSETS/'models/block/soul_chest_complete.json').read_text())
check(not any('rotation' in e for e in finished['elements']),'Completed Soul Chest must have a closed lid')
check(finished['textures']!=chest['textures'],'Completed Soul Chest must have distinct textures')
chest_states=json.loads((ASSETS/'blockstates/soul_chest.json').read_text())['variants']
check(all(chest_states[f'completed=true,facing={facing},open={opened}']['model'].endswith('soul_chest_complete') for opened in ['true','false'] for facing in ['north','east','south','west']),'Completed appearance must override any old open state in every facing')

# Damage uses a complete texture per stage, preserving the original door hinge and UVs.
iron_textures=[]
for stage in range(6):
    texture='logic_iron_barricade'+(f'_stage{stage}' if stage<5 else '')
    im=Image.open(ASSETS/'textures/block'/(texture+'.png')).convert('RGBA')
    check(im.size==(16,16),'Every iron damage stage must be 16x16')
    check(im.getchannel('A').getextrema()[1]==0 if stage==0 else im.getchannel('A').getextrema()[1]>250,'Damaged bars retain opaque metal and empty stage is transparent')
    iron_textures.append(im.tobytes())
    for hinge in ['left','right','left_open','right_open']:
        path=ASSETS/'models/block'/f'iron_defense_door_top_{hinge}_stage{stage}.json'
        if not path.exists():continue
        data=json.loads(path.read_text())
        original=json.loads((ASSETS/'models/block'/path.name.replace('iron_defense_door','defense_door')).read_text())
        check(data['parent']==original['parent'] and 'elements' not in data,'Damage must preserve complete original door geometry, not clipped bands')
        check(data['textures']['top']=='zombierool:block/'+texture,'Each damage stage has its own bar texture')
check(len(set(iron_textures))==6,'All six iron damage textures must be visibly distinct')
full=Image.open(ASSETS/'textures/block/logic_iron_barricade.png').convert('RGBA')
stage3=Image.open(ASSETS/'textures/block/logic_iron_barricade_stage3.png').convert('RGBA')
check(any(stage3.getpixel((x,y))[3]>200 for y in range(2) for x in range(16)) and any(stage3.getpixel((x,y))[3]>200 for y in range(12,16) for x in range(16)),'Intermediate damage keeps metal at top and bottom instead of a horizontal wipe')

sounds=json.loads((ASSETS/'sounds.json').read_text())
for name in ['soul_release','soul_absorb','soul_chest_open','soul_complete','zipline_attach','zipline_loop','zipline_arrive']:
    check(sounds[name]['subtitle']=='subtitles.zombierool.'+name,'Audio has a localized subtitle')
    path=ASSETS/'sounds/26_7'/(name+'.ogg');raw=path.read_bytes();vorbis=raw.index(b'\x01vorbis')
    check(raw[:4]==b'OggS' and raw[vorbis+11]==1,'Positional audio must be mono Ogg Vorbis')
    for mirror in [ROOT/'assets/zombierool',ROOT/'resources/assets/zombierool']:
        check((mirror/'sounds/26_7'/(name+'.ogg')).read_bytes()==raw,'Audio resource mirrors agree')

for other in ['assets/zombierool', 'resources/assets/zombierool']:
    mirror = ROOT / other
    for identifier in seen:
        if identifier.startswith('zombierool:'):
            relative = Path('models') / (identifier.split(':')[1] + '.json')
            check((mirror / relative).read_bytes() == (ASSETS / relative).read_bytes(), str(relative))
    for name in names:
        relative = Path('blockstates') / (name + '.json')
        check((mirror / relative).read_bytes() == (ASSETS / relative).read_bytes(), str(relative))

english = json.loads((ASSETS / 'lang/en_us.json').read_text(encoding='utf-8'))
for lang in (ASSETS / 'lang').glob('*.json'):
    data = json.loads(lang.read_text(encoding='utf-8'))
    for name in names:
        if name != 'traversal_node':
            check('block.zombierool.' + name in data or 'block.zombierool.' + name in english, lang.name + ': ' + name)
    for key in ['gui.zombierool.transport.section.channels', 'gui.zombierool.transport.helper.wall',
                'gui.zombierool.creative.navigation', 'message.zombierool.zipline.use']:
        check(key in data, lang.name + ': ' + key)

check((ASSETS/'textures/item/creative_icon.png').read_bytes()==(ASSETS/'icons/icon_32x32.png').read_bytes(),'Creative icon is the exact Windows application icon')
for root in [ASSETS,ROOT/'assets/zombierool',ROOT/'resources/assets/zombierool']:
    check(json.loads((root/'models/item/creative_icon.json').read_text())['textures']['layer0']=='zombierool:item/creative_icon','Creative icon uses its dedicated item texture')
    check(json.loads((root/'lang/en_us.json').read_text(encoding='utf8'))['item_group.zombierool.zb_rct']=='ZombieRool','Creative tab is named ZombieRool')
print(f'26.7 RESOURCE PASS: {checks} checks, {len(seen)} referenced models')
