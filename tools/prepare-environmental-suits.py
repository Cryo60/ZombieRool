"""Author small original pixel textures and cuboid equipment; no extracted game assets."""
from pathlib import Path
import json, shutil
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
A = RES / 'assets/zombierool'
written = []
def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    written.append(path)
def png(path, image):
    path.parent.mkdir(parents=True, exist_ok=True); image.save(path); written.append(path)
colors = {'white':(214,221,221),'dark':(29,39,46),'gray':(86,109,119),'gold':(193,125,36),
          'glass':(36,102,120),'yellow':(220,174,35),'cyan':(45,173,188),'red':(171,60,48)}
for name, color in colors.items():
    image=Image.new('RGB',(16,16)); p=image.load()
    for y in range(16):
        for x in range(16):
            delta=(x*7+y*11)%5-2
            p[x,y]=tuple(max(0,min(255,c+delta)) for c in color)
    d=ImageDraw.Draw(image); d.line((0,0,15,0),fill=tuple(min(255,c+12) for c in color))
    png(A/f'textures/entity/suits/{name}.png',image)
    # Block/item atlas discovery only includes block/ and item/ texture directories.
    png(A/f'textures/block/suits/{name}.png',image)

def box(lo,hi,material): return {'from':lo,'to':hi,'material':material}
def equipment(style):
    main={'lunar':'white','diving':'dark','hazmat':'yellow'}[style]
    helmet=[box([-4.65,-8.65,-4.65],[4.65,.45,4.65],main),box([-4.9,-.3,-4.9],[4.9,.9,4.9],'gray')]
    if style=='lunar':
        helmet += [box([-4.05,-7.05,-5.05],[4.05,-1.2,-4.7],'dark'),box([-3.65,-6.7,-5.2],[3.65,-1.6,-5.06],'gold'),box([-3,-6.4,-5.24],[-.5,-6,-5.2],'white'),box([-5.25,-5,-2],[-4.6,-1.5,2],'gray'),box([4.6,-5,-2],[5.25,-1.5,2],'gray')]
        back=[box([-3.7,.7,2.1],[3.7,10.8,5.6],'white'),box([-3,2,5.6],[3,5,5.9],'gray'),box([-3,7,5.6],[3,8,5.9],'red'),box([-2,2.2,-2.8],[2,6.8,-2.1],'gray'),box([-1.4,3,-3.1],[1.4,4.2,-2.8],'cyan')]
    elif style=='diving':
        helmet += [box([-4.1,-6.7,-5.1],[4.1,-2.9,-4.65],'gray'),box([-3.7,-6.3,-5.2],[-.3,-3.3,-5.1],'glass'),box([.3,-6.3,-5.2],[3.7,-3.3,-5.1],'glass'),box([-1.8,-2.8,-5.5],[1.8,-.5,-4.7],'dark'),box([-1,-2.3,-5.65],[1,-1,-5.5],'gray')]
        back=[]
        for x in [-3.4,.4]:
            back += [box([x,1,2.3],[x+3,10,5.5],'yellow'),box([x,3,2.25],[x+3,4,5.6],'dark'),box([x,8,2.25],[x+3,9,5.6],'dark'),box([x+.8,-.3,3],[x+2.2,1,4.6],'gray')]
        back += [box([-3.7,0,-2.6],[-2.6,10,-2.05],'gray'),box([2.6,0,-2.6],[3.7,10,-2.05],'gray'),box([-4,8.8,-2.7],[4,10.2,-2.1],'gray')]
    else:
        helmet += [box([-4,-6.9,-5],[4,-1.7,-4.65],'dark'),box([-3.5,-6.4,-5.1],[3.5,-2.2,-5],'glass'),box([-2.5,-2.1,-5.5],[2.5,.4,-4.7],'dark')]
        for x in [-3.8,1.5]:
            helmet += [box([x,-1.5,-5.7],[x+2.3,.8,-4.3],'gray'),box([x+.25,-1.2,-5.8],[x+2.05,.5,-5.7],'dark')]
        back=[box([-3,1,2.1],[3,9.5,5],'dark'),box([-2.5,2,5],[2.5,8.5,5.3],'yellow'),box([-.3,0,-2.65],[.3,11.8,-2.05],'dark')]
    return {'helmet':helmet,'back':back}

for style in ['lunar','diving','hazmat']:
    main={'lunar':'white','diving':'dark','hazmat':'yellow'}[style]
    image=Image.new('RGBA',(64,64),(*colors[main],255));d=ImageDraw.Draw(image)
    # Standard player UV layout: padding and secondary skin layers remain transparent.
    for rect in [(0,32,63,47),(0,48,15,63),(48,48,63,63)]:d.rectangle(rect,fill=(0,0,0,0))
    # Restore the left leg and arm lower-half base islands.
    d.rectangle((16,48,47,63),fill=(*colors[main],255))
    accent=colors['cyan' if style=='diving' else 'gray']; dark=colors['dark']
    for rect in [(16,30,39,31),(0,26,15,31),(16,58,31,63),(40,26,55,31),(32,58,47,63)]:d.rectangle(rect,fill=(*dark,255))
    for rect in [(4,24,7,25),(20,56,23,57),(44,24,47,25),(36,56,39,57)]:d.rectangle(rect,fill=(*accent,255))
    d.line((23,20,23,29),fill=(*dark,255));d.rectangle((20,21,22,24),fill=(*accent,255));d.rectangle((25,21,27,24),fill=(*accent,255))
    if style=='lunar':
        d.rectangle((20,26,27,27),fill=(*colors['red'],255));d.rectangle((44,20,47,21),fill=(*colors['red'],255));d.point((21,22),fill='white')
        d.rectangle((0,0,63,15),fill=(0,0,0,0))  # Original face/hat are rendered inside the existing lunar helmet.
    if style=='diving':
        for x in [20,26]:d.line((x,20,x,29),fill=(*accent,255))
    if style=='hazmat':
        d.rectangle((25,22,27,25),fill=(*dark,255));d.point((26,23),fill=(*colors['yellow'],255))
    png(A/f'textures/entity/suits/{style}_suit.png',image)
    data=equipment(style);save(A/f'models/equipment/{style}.json',data)
    textures={key:f'zombierool:block/suits/{key}' for key in colors}
    textures['particle']=textures[main]
    def element(c, transform=lambda v:v):
        lo,hi=transform(c['from']),transform(c['to'])
        return {'from':[min(lo[i],hi[i]) for i in range(3)],'to':[max(lo[i],hi[i]) for i in range(3)],'faces':{face:{'texture':'#'+c['material'],'uv':[0,0,16,16]} for face in ['north','south','east','west','up','down']}}
    icon=[element(c,lambda v:[v[0]+8,4-v[1],8-v[2]]) for c in data['helmet']]
    display={'gui':{'rotation':[20,-35,0],'scale':[.85,.85,.85]},'thirdperson_righthand':{'scale':[.55,.55,.55]},'firstperson_righthand':{'scale':[.7,.7,.7]}}
    if style!='lunar':
        save(A/f'models/item/{style}_helmet.json',{'parent':'block/block','textures':textures,'elements':icon,'display':display})
    # Open-front wall cabinet, recessed back, colored fascia and a recognizable helmet display.
    lower=[box([1,0,2],[15,2,15],'dark'),box([2,2,4],[14,12,15],'gray'),box([2,12,3],[14,16,15],main),box([4,6,3.6],[12,10,4],'dark'),box([5,7,3.4],[7,9,3.6],'cyan'),box([9,7,3.4],[11,9,3.6],main)]
    upper=[box([3,0,12],[13,14,15],'dark'),box([1,0,2],[3,14,15],main),box([13,0,2],[15,14,15],main),box([1,14,2],[15,16,15],'gray'),box([3,13.5,4],[13,14,12],'cyan'),box([3,0,2],[13,1,12],'gray')]
    helmet_display=[element(c,lambda v:[8+v[0]*.72,3-v[1]*.85,7+v[2]*.72]) for c in data['helmet']]
    if style=='lunar':
        original=json.loads((A/'models/item/lunar_helmet.json').read_text(encoding='utf-8'))
        textures.update({'legacy_'+key:value for key,value in original['textures'].items()})
        helmet_display=[]
        for cube in original['elements']:
            cube=json.loads(json.dumps(cube))
            # Only the cabinet display: inset the old glass edges so they do not
            # share side planes with its shell. The actual lunar item stays intact.
            if any(face['texture']=='#glass' for face in cube['faces'].values()):
                cube['from'][0]+=.03;cube['to'][0]-=.03
            for corner in ['from','to']:
                x,y,z=cube[corner];cube[corner]=[8+(x-8)*.72,3+(y-3)*.85,7+(z-8)*.72]
            for face in cube['faces'].values():face['texture']='#legacy_'+face['texture'].lstrip('#')
            helmet_display.append(cube)
    for half, cubes in [('lower',lower),('upper',upper)]:
        save(A/f'models/block/{style}_helmet_station_{half}.json',{'parent':'block/block','render_type':'minecraft:cutout','textures':textures,'elements':[element(c) for c in cubes]+(helmet_display if half=='upper' else [])})
    variants={f'facing={face},half={half}':{'model':f'zombierool:block/{style}_helmet_station_{half}','y':rotation} for face,rotation in [('north',0),('east',90),('south',180),('west',270)] for half in ['lower','upper']}
    save(A/f'blockstates/{style}_helmet_station.json',{'variants':variants})
    full=[element(c) for c in lower]+[element(c,lambda v:[v[0],v[1]+16,v[2]]) for c in upper]
    for row in helmet_display:
        row=dict(row);row['from']=[row['from'][0],row['from'][1]+16,row['from'][2]];row['to']=[row['to'][0],row['to'][1]+16,row['to'][2]];full.append(row)
    save(A/f'models/item/{style}_helmet_station.json',{'parent':'block/block','textures':textures,'elements':full,'display':{'gui':{'rotation':[20,145,0],'translation':[0,-4,0],'scale':[.4,.4,.4]},'ground':{'scale':[.25,.25,.25]}}})
    save(RES/f'data/zombierool/loot_tables/blocks/{style}_helmet_station.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'zombierool:{style}_helmet_station'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

for path in (A/'lang').glob('*.json'):
    n=json.loads(path.read_text(encoding='utf-8'));fr=path.stem=='fr_fr'
    for style,en,fr_name in [('lunar','Lunar helmet','Casque lunaire'),('diving','Diving gear','Équipement de plongée'),('hazmat','Hazmat suit','Combinaison hazmat')]:
        n[f'item.zombierool.{style}_helmet']=fr_name if fr else en
        n[f'block.zombierool.{style}_helmet_station']=('Station — '+fr_name if fr else en+' station')
    n.update({'message.zombierool.suit_take':'[%s] Récupérer : %s' if fr else '[%s] Take: %s',
              'message.zombierool.suit_owned':'%2$s déjà possédé — [%1$s]' if fr else '%2$s already owned — [%1$s]',
              'message.zombierool.suit_acquired':'%2$s récupéré — [%1$s] pour équiper / retirer' if fr else '%2$s acquired — [%1$s] to equip / remove'})
    save(path,n)
for path in written:
    rel=path.relative_to(RES)
    for base in [ROOT/'resources', ROOT/'build/resources/main']+([ROOT] if rel.parts[0] in ['assets','data'] else []):
        target=base/rel;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,target)
print(f'Authored and mirrored {len(written)} suit/station resources.')
