"""Keep hand transforms and wall projections matched to each authored model's axes."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'examples/stratholme/zombierool/gameplay/weapon_packs/stratholme'

def main():
    for path in (PACK / 'weapons').glob('*.json'):
        weapon = json.loads(path.read_text(encoding='utf-8'))
        kind = weapon.get('weapon_kind', '')
        name = path.stem
        for model_path in (PACK / 'assets/zombierool/models/item').glob(name + '*.json'):
            model = json.loads(model_path.read_text(encoding='utf-8'))
            display = model.setdefault('display', {})
            # Minecraft's hand layer contributes -90 X and 180 Y before these transforms.
            # Long guns and bolts point along model -Z; blades and wand shafts along +Y.
            for hand in ('right', 'left'):
                if kind in ('pistol', 'musket', 'blunderbuss', 'rifle', 'crossbow'):
                    display['thirdperson_' + hand + 'hand'] = {'rotation':[0,0,0], 'translation':[0,2,1], 'scale':[.8,.8,.8]}
                elif kind == 'wand':
                    display['thirdperson_' + hand + 'hand'] = {'rotation':[0,0,0], 'translation':[0,2,0], 'scale':[.8,.8,.8]}
                elif kind == 'bow':
                    display['thirdperson_' + hand + 'hand'] = {'rotation':[0,-90 if hand == 'right' else 90,0], 'translation':[0,2,0], 'scale':[.8,.8,.8]}
                elif kind in ('axe', 'halberd'):
                    display.setdefault('thirdperson_' + hand + 'hand', {})['rotation'] = [0,90 if hand == 'right' else -90,0]
            if kind in ('pistol', 'musket', 'blunderbuss', 'rifle', 'crossbow'):
                display['fixed'] = {'rotation':[0,90,0], 'scale':[.6,.6,.6]}
            if kind == 'bow' and '_draw_' in model_path.stem:
                # The bow lies in XY, with its string behind it on +X. Its arrow
                # must run through the grip along -X, not sideways along -Z.
                shaft, tip = model['elements'][-2:]
                nock = max(shaft['from'][0], shaft['to'][0])
                shaft['from'], shaft['to'] = [-11,6.85,7.85],[nock,7.15,8.15]
                tip['from'], tip['to'] = [-12,6.5,7.35],[-10,7.5,8.65]
            model_path.write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n', encoding='utf-8')
        animation = weapon.setdefault('animation', {})
        if kind in ('pistol','musket','blunderbuss','rifle','crossbow'):
            animation.update(fpX=[-.10,0,0],fpY=[.22,-.10,0],fpZ=[-.05,.10,.07],fpScale=[.82,0,0])
        if kind in ('halberd','greatsword'):
            animation.update(fpX=[-.12,-.04,-.16], fpY=[.32,.12,-.10], fpZ=[-.08,.05,-.16],
                             fpRx=[-18,-45,65],fpRy=[-8,-8,16],fpRz=[-12,12,-24],fpScale=[.72,0,0],
                             tpX=[-.8,-.55,1.0],tpY=[-.25,0,.12],tpZ=[-.12,-.10,.15])
            if kind == 'greatsword':
                animation['fpY'] = [.46,.12,-.10]
        path.write_text(json.dumps(weapon,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__ == '__main__':
    main()
