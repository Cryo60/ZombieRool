"""Map-owned plate and weapons on the existing WoW zombie rig and palm axes."""
from pathlib import Path
import json
import build_blocky_enemies as art
ROOT=Path(__file__).resolve().parents[2]

def main():
    for path in (ROOT/'examples/stratholme/zombierool/entities').glob('scarlet_zombie*.json'):
        profile=json.loads(path.read_text(encoding='utf-8'));parts=[]
        def cube(bone,material,*box,rx=0):
            parts.append(dict(bone=bone,material=art.MATERIALS.index(material),box=[float(v) for v in box],rx=float(rx)))
        cube('torso','metal',-4.5,-.6,-2.6,9,10,5.2)
        cube('torso','white',-3.5,.5,-3,7,11.5,.6)
        cube('torso','red',-.7,1.4,-3.6,1.4,7,.6);cube('torso','red',-2.5,3,-3.6,5,1.5,.6)
        cube('torso','strap',-4.6,9,-2.8,9.2,1.8,5.6);cube('torso','gold',-1.2,9,-3.5,2.4,1.8,.7)
        cube('head','metal',-4.5,-8.8,-3.8,9,2.4,8.3)
        cube('head','red',-.7,-10.2,-3.8,1.4,1.5,8)
        for side in ['left','right']:
            cube(side+'_arm','metal',-2.8,-1.7,-2.8,5.6,4,5.6)
            cube(side+'_arm','red',-2.9,-.9,-2.9,5.8,1.4,5.8)
            cube(side+'_forearm','metal',-2.3,1.5,-2.3,4.6,3.5,4.6)
            cube(side+'_shin','metal',-2.3,0,-2.4,4.6,4.5,4.8)
        if path.stem.endswith('swordsman'):
            cube('right_hand','strap',-.7,-1,-.7,1.4,4,1.4,rx=90)
            cube('right_hand','gold',-3.6,-2,-1.1,7.2,1.3,2.2,rx=90)
            cube('right_hand','metal',-1.2,-20,-.6,2.4,18,1.2,rx=90)
            cube('right_hand','lens',-.4,-19,-.8,.8,16,.3,rx=90)
            cube('left_forearm','metal',-5,-3,-4.5,10,16,1.5)
            cube('left_forearm','red',-4.2,-2.2,-5,8.4,14.4,.6)
            cube('left_forearm','white',-.7,-1.5,-5.7,1.4,12,.7)
            cube('left_forearm','white',-3.3,1.5,-5.7,6.6,1.4,.7)
        elif path.stem.endswith('axeman'):
            cube('right_hand','strap',-.8,-16,-.8,1.6,21,1.6,rx=90)
            cube('right_hand','metal',-1,-17,-1.2,2,5,2.4,rx=90)
            cube('right_hand','metal',1,-18,-1,6,7,2,rx=90)
            cube('right_hand','metal',-6,-17,-1,5,5,2,rx=90)
        elif path.stem.endswith('pikeman'):
            cube('right_hand','strap',-.7,-27,-.7,1.4,36,1.4,rx=90)
            cube('right_hand','metal',-1,-35,-1,2,8,2,rx=90)
            cube('right_hand','metal',1,-28,-1.2,6,7,2.4,rx=90)
            cube('right_hand','gold',-1.3,-22,-1.3,2.6,2,2.6,rx=90)
        profile.update(armorBoxes=parts,knockbackResistance=1)
        path.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    print('Scarlet plate, larger shield and palm-aligned weapons updated.')

if __name__=='__main__':main()
