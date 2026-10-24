"""Read just two named WoW sounds from the installed CASC; no unpack directory.

Wowhead sound 329390 -> FX_CHAINPULL01, FileDataID 1042882.
Wowhead sound 75097 -> GoblinBreathChili, FileDataID 568346.
The latter sound-kit deliberately references that older named audio file.
"""
from pathlib import Path
import json
from local_casc import LocalCasc
import import_enemy_audio as audio

if __name__ == '__main__':
    casc=LocalCasc('C:/Program Files (x86)/World of Warcraft')
    for key,fd,source in [('abomination_chain',1042882,'https://www.wowhead.com/fr/sound=329390/fx-chainpull01'),('abomination_belch',568346,'https://www.wowhead.com/sound=75097/spell-wyrmtongue-servant-retchedbelch-cast')]:
        audio.convert(key,0,casc.read(fd),f'WoW FileDataID {fd}; {source}')
    path=audio.OUT/'sounds.json';data=json.loads(path.read_text(encoding='utf-8'));data.update(audio.events);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report=audio.ROOT/'assets-source/26.7/enemy-skins/source-audio.json';data=json.loads(report.read_text(encoding='utf-8'));data.update(audio.report);report.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    for rel in ['sounds.json','sounds/encounters/abomination_chain_0.ogg','sounds/encounters/abomination_belch_0.ogg']:
        raw=(audio.OUT/rel).read_bytes()
        for base in ['resources/assets/zombierool','assets/zombierool','build/resources/main/assets/zombierool']:
            dest=audio.ROOT/base/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    print('Imported chain and belch; no temporary extraction files created.')
