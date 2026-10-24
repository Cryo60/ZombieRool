"""Consolidate legacy zr_scripts into zombierool/scripts with hash-verified backups."""
from pathlib import Path
import argparse, datetime, hashlib, json, shutil

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def migrate(world,backup,apply=False):
    world=world.resolve(strict=True);source=world/'zr_scripts';destination=world/'zombierool/scripts'
    if source.is_symlink() or destination.is_symlink():raise ValueError('Script roots must not be symlinks')
    rows=[]
    for file in sorted(source.rglob('*')):
        if file.is_symlink() or not file.resolve().is_relative_to(world):raise ValueError('Script outside save: '+str(file))
        if not file.is_file():continue
        target=destination/file.relative_to(source)
        if not target.resolve().is_relative_to(world):raise ValueError('Target outside save')
        digest=sha(file)
        if target.exists() and sha(target)!=digest:raise ValueError('Conflicting script; originals preserved: '+str(target))
        rows.append((file,target,digest))
    guide=world/'zombierool/ENTITY_WORKSHOP.md'
    if guide.is_file():
        target=world/'zombierool/docs/ENTITY_WORKSHOP.md';digest=sha(guide)
        if guide.is_symlink() or not target.resolve().is_relative_to(world):raise ValueError('Guide outside save')
        if target.exists() and sha(target)!=digest:raise ValueError('Conflicting guide; originals preserved')
        rows.append((guide,target,digest))
    if apply:
        backup.mkdir(parents=True,exist_ok=False)
        for file,target,digest in rows:
            saved=backup/file.relative_to(world);saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,saved)
            if sha(saved)!=digest:raise ValueError('Backup hash mismatch')
            target.parent.mkdir(parents=True,exist_ok=True)
            if not target.exists():shutil.copy2(file,target)
            if sha(target)!=digest:raise ValueError('Migration hash mismatch')
        for file,target,digest in rows:
            if sha(target)!=digest or sha(file)!=digest:raise ValueError('Script changed during migration')
            file.unlink()
        for directory in sorted((p for p in source.rglob('*') if p.is_dir()),key=lambda p:len(p.parts),reverse=True):
            try:directory.rmdir()
            except OSError:pass
        try:source.rmdir()
        except OSError:pass
        (backup/'migration.json').write_text(json.dumps([{'from':str(f),'to':str(t),'sha256':h} for f,t,h in rows],indent=2),encoding='utf-8')
    return rows

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('world',type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    backup=Path(__file__).resolve().parents[1]/'build/map-actors/backup'/('script-migration-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    rows=migrate(args.world,backup,args.apply)
    print(('Migrated' if args.apply else 'Would migrate'),len(rows),'files. Backup:',backup if args.apply else 'created on --apply')
