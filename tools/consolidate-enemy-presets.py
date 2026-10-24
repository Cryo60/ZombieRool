"""Keep legacy appearance IDs readable while presenting the consolidated presets."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
def consolidate(catalog):
    zombies=catalog['zombies'];hounds=catalog['hounds']
    # The base recipes always regenerate the three original Front variants.
    zombies['origins']['variants']=zombies['origins']['variants'][:3]+zombies['templars']['variants']
    for row in zombies['origins']['variants'][3:]:
        if row.get('headgear',{}).get('kind')=='crusader':row['headgear']={'kind':'steel','texture':'zombierool:textures/entities/presets/headgear/steel.png'}
    # One visible infernal appearance. Old t4/t5/t6 catalog entries are retained for saved entities.
    hounds['infernal']={'variants':[hounds['t5']['variants'][0]]}
    for preset,row in zombies.items():row['hound']='original' if preset=='original' else 'infernal'
    return catalog
def main():
    assets=ROOT/'src/main/resources/assets/zombierool';p=assets/'skins/enemy_presets.json'
    p.write_text(json.dumps(consolidate(json.loads(p.read_text())),indent=2)+'\n',encoding='utf-8')
    for root in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool']:shutil.copy2(p,root/'skins/enemy_presets.json')
if __name__=='__main__':main()
