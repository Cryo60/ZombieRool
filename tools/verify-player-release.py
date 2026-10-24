"""Check player JAR contents without rejecting runtime models or compressed data."""
from pathlib import Path
from collections import Counter
import gzip
import json
import re
import sys
import zipfile

AUTHORING_SUFFIXES = {'.py', '.pyc', '.ps1', '.bat', '.cmd', '.blend', '.blend1',
                      '.bbmodel', '.fbx', '.psd', '.xcf', '.aseprite', '.jfr', '.log', '.tmp', '.bak'}

def placeholders(s):
    result = Counter()
    implicit = 0
    for match in re.finditer(r'%(?:(\d+)\$)?([a-zA-Z%])', s):
        index, kind = match.groups()
        if kind == '%':
            continue
        if not index:
            implicit += 1
            index = str(implicit)
        result[(index, kind)] += 1
    return result

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        assert key not in result, 'Duplicate JSON key: ' + key
        result[key] = value
    return result

def verify(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None, 'Corrupt ZIP entry'
        names = z.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP entries'
        locales = {}
        compressed = 0
        for name in names:
            parts = Path(name).parts
            assert not set(parts) & {'.git', '__pycache__', 'node_modules'}, name
            assert Path(name).suffix.lower() not in AUTHORING_SUFFIXES, name
            data = z.read(name)
            if name.startswith('me/cryo/zombierool/') and name.endswith('.class'):
                assert b'org/luaj/' not in data, name + ': unrelocated Lua dependency'
            if name in {'me/cryo/zombierool/gameplay/BossJumpLinkGoal.class',
                        'me/cryo/zombierool/gameplay/HiddenActorSpawn.class'}:
                for member in (b'getTarget', b'setTarget', b'getNavigation', b'setOnGround', b'setPos'):
                    constant = b'\x01' + len(member).to_bytes(2, 'big') + member
                    assert constant not in data, name + ': unmapped Minecraft call ' + member.decode()
            if name.endswith('.gz'):
                gzip.decompress(data)
                compressed += 1
            if name.startswith('assets/zombierool/') and name.endswith('.json'):
                obj = json.loads(data, object_pairs_hook=unique_object)
                assert not (isinstance(obj, dict) and str(obj.get('credit', '')).startswith('Made with Blockbench')), name
                if '/lang/' in name:
                    locales[Path(name).stem] = obj
            if name.startswith(('assets/', 'data/')):
                assert not re.search(rb'(?:[CD]:[\\/](?:Users|Dev)[\\/])', data, re.I), name
        assert set(locales) == {'en_us', 'fr_fr', 'de_de', 'es_es', 'pt_br', 'ru_ru'}
        reference = locales['en_us']
        for lang, values in locales.items():
            assert set(values) == set(reference), lang + ': keys differ'
            for key, value in values.items():
                assert isinstance(value, str) and value.strip() and '\ufffd' not in value, (lang, key)
                assert placeholders(value) == placeholders(reference[key]), (lang, key, 'format')
        assert 'assets/zombierool/textures/camo/resprune.png' not in names
        assert 'me/cryo/zombierool/scripting/LuaSandbox.class' in names
        assert not any(n.startswith('org/luaj/') for n in names), 'LuaJ must remain relocated'
        assert b'${mod_version}' not in z.read('META-INF/mods.toml')
        print(json.dumps({'jar': str(path), 'languages': len(locales), 'keys_per_language': len(reference),
                          'validated_compressed_runtime_assets': compressed, 'entries': len(names)}))

if __name__ == '__main__':
    verify(Path(sys.argv[1]))
