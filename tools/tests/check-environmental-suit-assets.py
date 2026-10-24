"""Catch station surface overlaps that cause visible z-fighting."""
from pathlib import Path
import itertools,json

ROOT=Path(__file__).resolve().parents[2]
checks=0
for style in ['lunar','diving','hazmat']:
    for half in ['lower','upper']:
        path=ROOT/f'src/main/resources/assets/zombierool/models/block/{style}_helmet_station_{half}.json'
        elements=json.loads(path.read_text(encoding='utf-8'))['elements']
        for (i,a),(j,b) in itertools.combinations(enumerate(elements),2):
            for axis in range(3):
                others=[v for v in range(3) if v!=axis]
                for side in ['from','to']:
                    if abs(a[side][axis]-b[side][axis])>1e-6:continue
                    overlap=all(min(a['to'][v],b['to'][v])-max(a['from'][v],b['from'][v])>1e-6 for v in others)
                    assert not overlap,f'{style} {half}: overlapping face {i}/{j}, axis={axis}, side={side}'
        checks+=1
        for base in ['resources','build/resources/main','.']:
            mirror=ROOT/base/path.relative_to(ROOT/'src/main/resources')
            assert mirror.read_bytes()==path.read_bytes(),f'Stale model mirror: {mirror}'
print(f'SUIT ASSET PASS: {checks} station halves without overlapping coplanar outer faces; mirrors verified.')
