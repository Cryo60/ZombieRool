"""Validate actual shipped appearance resources, geometry and native UV coverage."""
from pathlib import Path
import sys,json,gzip,math
ROOT=Path(__file__).resolve().parents[2];sys.path[:0]=[str(ROOT/'build/video-python'),str(ROOT/'build/audio-python')]
from PIL import Image
assets=ROOT/'src/main/resources/assets/zombierool';catalog=json.loads((assets/'skins/enemy_presets.json').read_text(encoding='utf-8'));count=gear=checks=0
for group in ['zombies','hounds']:
 for preset,settings in catalog[group].items():
  if preset!='original':assert settings['variants'],preset
  for index,row in enumerate(settings['variants']):
   path=assets/row['texture'];image=Image.open(path).convert('RGBA');assert preset=='original' or image.size==((256,256) if group=='zombies' and preset=='mob_of_dead' and index==2 else (128,128) if group=='zombies' else (128,64)),str(path)
   if group=='zombies':
    scale=image.width/128;face=image.crop(tuple(round(v*scale) for v in (16,16,32,32))).resize((16,16)).convert('RGB');colors=face.getcolors();assert len(colors)>=4,('Flat head mask',path)
    mean=sum(sum(c)*n for n,c in colors)/(256*3);assert 12<mean<235,('Wrong shader mask used as diffuse',path,mean)
   if 'headgear' in row:
    assert (assets/row['headgear']['texture'].split(':',1)[1]).is_file()
    assert row['headgear']['kind'] in ['steel','mp','officer','guard','field','beanie','miner','cowboy','pickelhaube','crusader']
   if 'gear' in row:
    data=json.loads(gzip.decompress((assets/row['gear']).read_bytes()));assert data['surfaces']
    for surface in data['surfaces']:
     assert (assets/surface['texture'].split(':',1)[1]).is_file();v=surface['vertices'];assert len(v)%3==0
     assert all(len(p)==8 and all(math.isfinite(x) for x in p) for p in v)
     assert all(abs(p[i])<32 for p in v for i in range(3)),('Misaligned attachment',row['gear'])
     gear+=len(v)//3
   for base in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool']:assert (base/row['texture']).read_bytes()==path.read_bytes()
   count+=1;checks+=1
guard=Image.open(assets/'textures/entities/presets/mob_of_dead/2.png').convert('RGB');assert len(guard.crop((80,80,112,128)).getcolors(100000))>200,'Repeated UVs must preserve guard uniform detail';checks+=1
for color in ['default','red','blue','green']:
 image=Image.open(assets/'textures/entities/presets'/('eyes_'+color+'.png')).convert('RGBA');assert image.size==(128,128);assert image.getbbox()==(20,22,31,23)
 for x in [20,28]:assert max(image.getpixel((x,22))[:3])>=225
 checks+=1
for lang in (assets/'lang').glob('*.json'):
 data=json.loads(lang.read_text(encoding='utf-8'));assert all('config.zombierool.value.'+p in data for group in catalog.values() for p in group)
 checks+=1
report=json.loads((ROOT/'assets-source/26.7/enemy-skins/conversion-report.json').read_text(encoding='utf-8'));assert not report['errors'];assert len(report['variants'])==65 and count==74
print('PASS:',checks,'asset checks;',count,'detailed skins including classics;',gear,'source accessory triangles; visible faces and narrow eye masks')
