"""Build the single-file offline editor from the real Forge registry and rendered icons."""
from pathlib import Path
import base64, hashlib, io, json, sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/video-python'))
from PIL import Image

source = json.loads((ROOT / 'build/inventory-catalog/catalog.json').read_text(encoding='utf8'))
fr = json.loads((ROOT / 'src/main/resources/assets/zombierool/lang/fr_fr.json').read_text(encoding='utf8'))
draft = '--draft' in sys.argv
atlases = {}
for item in source['items']:
    if item['id'] == '@weapons':
        item['nameFr'] = 'Armes (toutes)'
    else:
        item['nameFr'] = fr.get(item.get('key'), item['nameEn'])
        if item['nameFr'].startswith(('item.zombierool.', 'block.zombierool.')):
            item['nameFr'] = item['id'].split(':', 1)[1].replace('_', ' ').capitalize()
    if not draft:
        index = item['iconIndex']
        page = index // 400
        if page not in atlases:
            atlas = Image.open(ROOT / f'build/inventory-catalog/atlas-{page}.png').convert('RGB')
            assert atlas.size == (1024, 1024), atlas.size
            assert atlas.getpixel((4, 4)) == (25, 29, 36), 'Export screen not ready: preserve the catalogue and rerun the icon export.'
            atlases[page] = atlas
        index %= 400
        x, y = (index % 20) * 48, (index // 20) * 48
        output = io.BytesIO()
        atlases[page].crop((x, y, x + 48, y + 48)).save(output, format='PNG', optimize=True)
        item['icon'] = 'data:image/png;base64,' + base64.b64encode(output.getvalue()).decode('ascii')
    item.pop('iconIndex', None)

source['modVersion'] = '26.7-dev'
source['fingerprint'] = hashlib.sha256(json.dumps([(i['id'], i.get('members', [])) for i in source['items']], sort_keys=True).encode()).hexdigest()
payload = json.dumps(source, ensure_ascii=False, separators=(',', ':')).replace('<', '\\u003c')
template = (Path(__file__).parent / 'editor.template.html').read_text(encoding='utf8')
result = template.replace('__CATALOG__', payload)
target = Path(__file__).parent / 'index.html'
target.write_text(result, encoding='utf8')
print(f'{len(source["items"])} catalogue entries, {source["weaponCount"]} grouped weapons. {target.stat().st_size:,} bytes. {target}')
