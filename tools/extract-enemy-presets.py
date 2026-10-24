"""Extract only local enemy reference zones with OpenAssetTools, then retain selected inputs.
Never writes to a game installation. The extracted assets are not a redistribution license.
"""
from pathlib import Path
import argparse,subprocess,json,shutil,importlib.util
ROOT=Path(__file__).resolve().parents[1]

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--t4',type=Path,default=Path('D:/Jeux/Plutonium/pluto_t4_full_game'));parser.add_argument('--t5',type=Path,default=Path('D:/Jeux/pluto_t5_full_game'));parser.add_argument('--t6',type=Path,default=Path('D:/Jeux/pluto_t6_full_game'));parser.add_argument('--unlinker',type=Path,default=ROOT/'build/boss-assets/oat/Unlinker.exe');args=parser.parse_args()
 scratch=(ROOT/'build/zombie-presets').resolve();scratch.mkdir(exist_ok=True);raw=scratch/'raw';aliases=scratch/'ipak-aliases';aliases.mkdir(exist_ok=True)
 spec=json.loads((ROOT/'assets-source/26.7/enemy-skins/sources.json').read_text(encoding='utf-8'));zones=sorted({v['zone'] for v in spec.values()}|{'t6/zm_nuked','t4/nazi_zombie_factory','t5/zombie_theater'})
 bases={'t4':args.t4/'zone/english','t5':args.t5/'zone/Common','t6':args.t6/'zone/all'}
 for zone,ipak in [('zm_highrise','dlczm1'),('zm_prison','dlczm2'),('zm_tomb','dlczm4'),('so_zclassic_zm_buried','dlczm3')]:
  dest=aliases/(zone+'.ipak')
  if not dest.exists():shutil.copyfile(bases['t6']/(ipak+'.ipak'),dest)
 for zone in zones:
  game,name=zone.split('/');source=bases[game]/(name+'.ff')
  command=[str(args.unlinker),'--include-assets','xmodel,material,image','--model-format','XMODEL_EXPORT','--image-format','DDS','--search-path',str(aliases)+';'+str(bases[game]),'-o',str(raw/'?game?/?zone?'),str(source)]
  with (scratch/(name+'-selected.log')).open('w',encoding='utf-8') as log:subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True)
  print('EXTRACTED',zone,flush=True)
 # Validate source references and prune only files created under this scratch folder.
 module=importlib.util.spec_from_file_location('baker',ROOT/'tools/bake-enemy-skins.py');b=importlib.util.module_from_spec(module);module.loader.exec_module(b)
 jobs=[(v['zone'],row['models']) for v in spec.values() for row in v['variants']]+[('t4/nazi_zombie_factory',['zombie_wolf','zombie_wolf_variant_a']),('t5/zombie_theater',['zombie_wolf']),('t6/zm_nuked',['zombie_wolf'])]
 keep=set()
 for zone,names in jobs:
  b.read_models(raw/zone,names)
  for name in names:
   p=raw/zone/'model_export'/(name+'_lod0.xmodel_export');keep.add(p.resolve());m=b.Model.FromFile_Raw(str(p))
   for mat in m.materials:
    keep.add((p.parent/mat.images['color'].replace('\\','/')).resolve());metadata=raw/zone/'materials'/(mat.name+'.json');keep.add(metadata.resolve())
    if metadata.exists():
     for t in json.loads(metadata.read_text(encoding='utf-8')).get('textures',[]):keep.add((raw/zone/'images'/(t['image']+'.dds')).resolve())
   if zone=='t5/zombie_theater':keep.add((raw/zone/'images/~-gchar_ger_honorguard_body1_c.dds').resolve())
 assert raw.resolve().is_relative_to((ROOT/'build').resolve())
 for p in raw.rglob('*'):
  if p.is_file() and p.resolve() not in keep:
   assert p.resolve().is_relative_to(raw.resolve());p.unlink()
 for p in aliases.glob('*.ipak'):
  assert p.resolve().is_relative_to(scratch);p.unlink()
 print('PASS: selected source models and textures retained; unrelated extraction intermediates removed')
if __name__=='__main__':main()
