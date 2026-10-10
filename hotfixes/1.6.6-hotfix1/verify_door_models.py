"""Check the rendered door plane and front UV against Minecraft's own door assets."""
from pathlib import Path
from zipfile import ZipFile
import itertools
import json

ROOT = Path(__file__).resolve().parents[2]
args = (ROOT / 'build/verify-fixes/javac.args').read_text().splitlines()
classpath = args[args.index('-classpath') + 1].strip('"')
extra = next(Path(path) for path in classpath.split(';') if path.endswith('client-extra.jar'))

def rotate(point, degrees):
    x, y, z = point
    for _ in range(degrees // 90):
        x, z = 16 - z, x
    return x, y, z

def bounds(element, degrees):
    vertices = [rotate(point, degrees) for point in itertools.product(*zip(element['from'], element['to']))]
    return tuple(min(point[i] for point in vertices) for i in range(3)), tuple(max(point[i] for point in vertices) for i in range(3))

def front_uvs(element, degrees):
    directions = ['north', 'east', 'south', 'west']
    return {directions[(directions.index(face) + degrees // 90) % 4]: data['uv'][2] > data['uv'][0]
            for face, data in element['faces'].items()
            if face in directions and abs(data['uv'][2] - data['uv'][0]) == 16}

with ZipFile(extra) as vanilla, ZipFile(ROOT / 'build/libs/zombierool-1.6.6-hotfix1.jar') as mod:
    vanilla_states = json.loads(vanilla.read('assets/minecraft/blockstates/iron_door.json'))['variants']
    states = json.loads(mod.read('assets/zombierool/blockstates/glass_defense_door.json'))['variants']
    checked = 0
    for key, entry in states.items():
        props = dict(prop.split('=') for prop in key.split(','))
        if props['stage'] != '7' or props['breached'] != 'false':
            continue
        vkey = ','.join(f'{prop}={props[prop]}' for prop in ('facing', 'half', 'hinge', 'open'))
        expected = vanilla_states[vkey]
        expected_model = json.loads(vanilla.read('assets/' + expected['model'].replace(':block/', '/models/block/') + '.json'))
        while 'elements' not in expected_model:
            expected_model = json.loads(vanilla.read('assets/' + expected_model['parent'].replace(':block/', '/models/block/') + '.json'))
        actual_model = json.loads(mod.read('assets/' + entry['model'].replace(':block/', '/models/block/') + '.json'))
        actual = actual_model['elements'][0]
        template = expected_model['elements'][0]
        actual_bounds = bounds(actual, entry.get('y', 0))
        expected_bounds = bounds(template, expected.get('y', 0))
        if props['centered'] == 'true':
            low, high = [list(point) for point in expected_bounds]
            for axis in (0, 2):
                if high[axis] - low[axis] == 3:
                    low[axis], high[axis] = 6.5, 9.5
            expected_bounds = tuple(low), tuple(high)
        assert actual_bounds == expected_bounds, (key, actual_bounds, expected_bounds)
        assert front_uvs(actual, entry.get('y', 0)) == front_uvs(template, expected.get('y', 0)), key
        checked += 1
    assert checked == 64, checked
print(f'PASS: {checked} glass-door render states match vanilla door geometry and front texture direction.')
