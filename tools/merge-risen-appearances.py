"""Keep one role per Risen; embed the female visual/voice as a spawn appearance."""
from pathlib import Path
import json, shutil
ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'examples/stratholme/zombierool/gameplay/entities'
ARCHIVE=ROOT/'assets-source/26.7/map-actors/risen-appearances'
FIELDS=('bones','boxes','animations','texture','textureWidth','textureHeight','scale','sourceMotion','sounds','eyeR','eyeG','eyeB')

def main():
 ARCHIVE.mkdir(parents=True,exist_ok=True)
 for suffix in ('','_swordsman','_axeman','_pikeman','_archer','_mage'):
  path=FOLDER/('scarlet_zombie'+suffix+'.json');female=path.with_stem(path.stem+'_female')
  profile=json.loads(path.read_text(encoding='utf8'))
  if not female.exists():
   assert len(profile.get('appearances',[]))==1
   continue
  for source in (path,female):shutil.copy2(source,ARCHIVE/source.name)
  alternative=json.loads(female.read_text(encoding='utf8'))
  profile['appearances']=[{key:alternative[key] for key in FIELDS if key in alternative}]
  profile['name']=profile['name'].replace(' (Male)','').replace(' (male)','')
  path.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':')),encoding='utf8')
  assert path.stat().st_size<1000000
  female.unlink()
  print('Unified Risen:',path.stem)

if __name__=='__main__':main()
