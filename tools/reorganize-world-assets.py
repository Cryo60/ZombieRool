"""Migrate authoring files with a verified backup; leave Minecraft world data untouched."""
from pathlib import Path
import argparse,hashlib,shutil,json,datetime
LAYOUT={'config.json':'config/settings.json','entities':'gameplay/entities','weapons':'gameplay/weapons','weapon_packs':'gameplay/weapon_packs','custom_blocks':'assets/blocks','audio':'assets/audio','skins':'assets/textures','particles/custom':'assets/particles','assets/chalks':'assets/textures/chalks','assets/skyboxes':'assets/textures/skyboxes'}
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def migrate(world,apply=False):
 root=world/'zombierool';rows=[]
 for old,new in LAYOUT.items():
  source=root/old
  for file in ([source] if source.is_file() else sorted(source.rglob('*')) if source.exists() else []):
   if not file.is_file():continue
   target=root/new/(file.relative_to(source) if source.is_dir() else Path())
   if target.exists() and digest(target)!=digest(file):raise ValueError('Conflicting destination: '+str(target))
   rows.append((file,target,digest(file)))
 backup=Path(__file__).resolve().parents[1]/'build/map-actors/backup'/('asset-layout-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
 if apply:
  for source,target,sha in rows:
   saved=backup/source.relative_to(world);saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,saved)
   assert digest(saved)==sha
   target.parent.mkdir(parents=True,exist_ok=True)
   if not target.exists():shutil.copy2(source,target)
   assert digest(target)==sha
  for source,target,sha in rows:
   assert digest(source)==sha and digest(target)==sha
   source.unlink()
  for old in sorted(LAYOUT,key=len,reverse=True):
   source=root/old
   if source.is_dir():
    for directory in sorted((p for p in source.rglob('*') if p.is_dir()),key=lambda p:len(p.parts),reverse=True):
     try:directory.rmdir()
     except OSError:pass
    try:source.rmdir()
    except OSError:pass
  backup.mkdir(parents=True,exist_ok=True);(backup/'manifest.json').write_text(json.dumps([{'from':str(a),'to':str(b),'sha256':c} for a,b,c in rows],indent=2),encoding='utf-8')
 print(('Migrated' if apply else 'Would migrate'),len(rows),'files; backup:',backup)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('world',type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args();migrate(args.world,args.apply)
