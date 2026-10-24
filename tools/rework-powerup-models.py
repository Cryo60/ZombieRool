"""Block-built power-up sculptures, palette textures, and reproducible model output."""
from pathlib import Path
import json, random
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/zombierool'
PALETTE = {'gold': (175,126,39), 'bright': (223,183,87), 'shade': (111,76,27),
           'dark': (35,30,20), 'wood': (80,58,27), 'red': (122,18,15), 'label': (222,211,163)}
for name, color in PALETTE.items():
    rng = random.Random(name)
    image = Image.new('RGBA', (16,16))
    for y in range(16):
        for x in range(16):
            grain = rng.randrange(-5,6)
            image.putpixel((x,y), tuple(max(0,min(255,c+grain)) for c in color)+(255,))
    image.save(ROOT / f'textures/item/powerup_{name}.png')

def box(a,b,material='gold'):
    faces={}
    for side in ('north','south','east','west','up','down'):
        tex = ('bright' if side=='up' else 'shade' if side in ('east','west','down') else 'gold') if material=='gold' else material
        faces[side]={'uv':[0,0,16,16], 'texture':'#'+tex}
    return {'from':a,'to':b,'faces':faces}

def save(name,elements):
    center=[(min(e['from'][axis] for e in elements)+max(e['to'][axis] for e in elements))/2 for axis in range(3)]
    for e in elements:
        for key in ('from','to'):e[key]=[round(e[key][axis]-center[axis]+8,4) for axis in range(3)]
    data={'ambientocclusion' :False,'textures':{k:f'zombierool:item/powerup_{k}' for k in PALETTE},'elements':elements}
    data['textures']['particle']='zombierool:item/powerup_gold'
    (ROOT/f'models/bonus/{name}.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

# Ammo can: ridged cover, hinges, latch, handle and an exposed belt of rounds.
e=[box([1,3,3],[15,11,13]),box([.5,10,2.5],[15.5,12,13.5]),box([1.5,3,2.7],[14.5,9.5,3],'wood')]
for x in (2,12):
    e.extend([box([x,2.5,2.4],[x+2,12.3,3.1]),box([x,3,12.7],[x+2,12,13.6])])
e.extend([box([6.1,7,2],[9.9,10,2.6],'dark'),box([7,7.3,1.7],[9,9.5,2.1]),box([5,12,6],[6,14,10]),box([10,12,6],[11,14,10]),box([5,14,6],[11,15,10])])
for x in range(3,14,2):
    e.extend([box([x,5,2],[x+1,9,2.6]),box([x+.2,9,2.1],[x+.8,10,2.5],'bright')])
for z in (4,7,10):e.append(box([1,11.9,z],[15,12.3,z+.6],'bright'))
save('max_ammo',e)

# Skull: recessed sockets enclosed by brows/cheeks, nose cavity, individual teeth.
e=[box([4,5,5],[12,13,12]),box([3,9,5],[13,14,11]),box([4,14,5],[12,15,11]),box([5,15,6],[11,15.6,10]),box([3,7,4.7],[5,10,7]),box([11,7,4.7],[13,10,7])]
e += [box([4,10,3.7],[7.3,12.7,4.8],'dark'),box([8.7,10,3.7],[12,12.7,4.8],'dark'),box([3.5,12.5,3.2],[7.5,13.5,5]),box([8.5,12.5,3.2],[12.5,13.5,5]),box([7.2,8,3.4],[8.8,10.4,4.8],'dark'),box([4,6,4],[12,7.8,6]),box([4.5,3.5,5],[11.5,5,10]),box([4,4.5,4],[12,5.5,5.2])]
for x in (4.2,6.2,8.2,10.2):
    e.extend([box([x,5.4,3.5],[x+1.3,6.8,4.6],'bright'),box([x,4.6,3.6],[x+1.3,5.3,4.5])])
save('insta_kill',e)

# Bomb: stepped shell, nose, a dark collar and four stabilizing tail fins.
e=[box([5,3,4],[11,13,12]),box([4,5,5],[12,12,11]),box([6,1.5,5],[10,4,11]),box([7,.6,6],[9,2,10]),box([4.8,12.5,3.8],[11.2,13.5,12.2],'dark'),box([6,13.5,5],[10,16,11])]
e += [box([3,14,7],[6,18,9]),box([10,14,7],[13,18,9]),box([7,14,2.5],[9,18,5]),box([7,14,11],[9,18,13.5])]
e += [box([6,6,3.7],[10,10,4],'dark'),box([7,7,3.5],[9,9,3.8],'bright'),box([6,10,4],[10,11,4.4],'bright')]
save('nuke',e)

# Raised 2x with a contrasting backing following each glyph, no cube enclosing it.
two=['1111','0001','0001','1111','1000','1000','1111']
cross=['1001','1001','0110','0110','0110','1001','1001']
e=[]
for offset,glyph in ((0,two),(9,cross)):
    for y,line in enumerate(glyph):
        for x,c in enumerate(line):
            if c=='1':
                px=1+offset+x*1.2;py=12-y*1.2
                e.append(box([px-.18,py-.18,6],[px+1.38,py+1.38,10],'dark'))
                e.append(box([px,py,5.5],[px+1.2,py+1.2,10.5],'bright'))
save('double_points',e)

# Claw hammer with beveled head and wrapped handle.
e=[box([7,1,7],[9,12,9],'wood'),box([3,11,5.5],[12,15,10.5]),box([4,15,6],[11,15.6,10]),box([11,10.5,5],[15,15.5,11]),box([14.7,11,5.5],[15.4,15,10.5],'bright'),box([1,11,5.5],[4,14.5,7]),box([1,11,9],[4,14.5,10.5])]
for y in (2,4,6,8):e.append(box([6.7,y,6.7],[9.3,y+.7,9.3],'dark'))
save('carpenter',e)

# Gold Rush is explicitly a stack of stamped gold ingots.
e=[]
for x,y,z in ((1,2,2),(8,2,2),(4.5,5.5,3.5)):
    e.extend([box([x,y,z],[x+6.5,y+2.3,z+10]),box([x+.6,y+2.3,z+.8],[x+5.9,y+3,z+9.2],'bright'),box([x+2,y+3.02,z+3],[x+4.5,y+3.12,z+6],'shade')])
save('gold_rush',e)

# Perk bottle with shoulders, cap, label and an inset medallion.
e=[box([4,1.5,5],[12,9,11]),box([3.5,3,5.5],[12.5,8.5,10.5]),box([5,9,5.5],[11,11,10.5]),box([6,11,6],[10,15.5,10]),box([5.5,15,5.5],[10.5,17,10.5],'bright'),box([4.5,4,4.7],[11.5,8,5],'label'),box([7,4.8,4.5],[9,7.2,4.8],'dark')]
save('on_the_house',e)

# Wish: a lamp, with a thin spout and a genuinely hollow handle.
e=[box([4,3,5],[12,7,11]),box([3,4,6],[13,6.5,10]),box([5,2,6],[11,3,10]),box([4,1,5],[12,2,11]),box([5,7,6],[11,8,10]),box([7,8,7],[9,10,9]),box([12,5.5,7],[15,7.5,9]),box([14.5,7,7],[17,8.5,9]),box([16,8,7],[18,9,9])]
e.extend([box([1,4,6.5],[3,5,9.5]),box([0,4,6.5],[1,9,9.5]),box([1,8,6.5],[4,9,9.5])])
save('wish',e)

# Blood bag, golden fittings and readable red contents, with a curled tube.
e=[box([4.5,4,5.5],[11.5,13,10.5],'red'),box([4,4,5],[5,13,11]),box([11,4,5],[12,13,11]),box([4,13,5],[12,14,11]),box([4,3,5],[12,4,11]),box([7,14,7],[9,16,9]),box([6,15.5,7],[10,16.5,9]),box([6,9,5],[10,12,5.5],'label'),box([7.5,1,7],[8.5,3,8],'dark'),box([8,0,7],[13,1,8],'dark'),box([12,1,7],[13,4,8],'dark')]
save('zombie_blood',e)
