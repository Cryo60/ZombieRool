"""Extract bone metadata only (no copyrighted mesh/texture copies) for the Java policy audit.

Usage: python tools/tests/audit-tacz-camo-packs.py <instance> <extra-archives> <output.json>
The dependencies.json snapshot must be in extra-archives. Missing versions are reported.
"""
import json, sys, zipfile, hashlib
from pathlib import Path

instance, extra, output = map(Path, sys.argv[1:4])
catalog = json.loads((extra / 'dependencies.json').read_text())['dependencies']
archives = list((instance/'mods').glob('*.jar')) + list((instance/'tacz').glob('*.zip')) + list(extra.glob('*.jar')) + list(extra.glob('*.zip'))
for additional in map(Path,sys.argv[4:]):
    archives.append(additional)
    catalog.append({'id':'installed:'+additional.stem,'filename':additional.name})
result=[]
for dep in catalog:
    matches=[p for p in archives if p.name==dep['filename']]
    note='catalog version'
    if not matches and dep['id']=='gunpack_bo6':
        matches=[p for p in archives if 'Black Ops6 v1.0.2' in p.name]
        note='1.0.2: actual version of catalog CurseForge file 6024155; catalog filename says 1.1pre5'
    if not matches:
        result.append({'id':dep['id'],'status':'missing','requested':dep['filename'],'models':[]});continue
    path=matches[-1]; models=[]; attachments=0
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith('.json') or not any(s in name for s in ['/geo_models/','/models/']):continue
            if '/attachment/' in name:attachments+=1
            if '/gun/' not in name:continue
            data=json.loads(archive.read(name))
            for geo in data.get('minecraft:geometry',[]):
                bones=[{'name':b['name'],'parent':b.get('parent'), 'cubes':len(b.get('cubes',[])),
                        'mesh':bool(b.get('poly_mesh'))} for b in geo.get('bones',[])]
                if bones:models.append({'path':name,'bones':bones})
    result.append({'id':dep['id'],'status':'inspected','archive':path.name,'version_note':note,
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'attachments':attachments,'models':models})
output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
for p in result:print(p['id'],p['status'],len(p['models']),'models',p.get('attachments',0),'attachments')
if any(p['status']=='missing' for p in result):raise SystemExit(1)
