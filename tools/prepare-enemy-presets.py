"""Bake selected local enemy assets. Run extract-enemy-presets.py first.
Conversions run in isolated worker processes; source game files stay read-only.
"""
from pathlib import Path
from concurrent.futures import ProcessPoolExecutor,as_completed
import importlib.util,json,hashlib,shutil,os
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('skin_baker',ROOT/'tools/bake-enemy-skins.py');b=importlib.util.module_from_spec(s);s.loader.exec_module(b)
SPECS=ROOT/'assets-source/26.7/enemy-skins/sources.json'
BAKER_HASH=hashlib.sha256((ROOT/'tools/bake-enemy-skins.py').read_bytes()).hexdigest()

def convert(job):
 preset,i,zone,names,extras,hound=job
 target=b.DEST/'textures/entities/presets'/('hounds/'+preset if hound else preset)/(str(i)+'.png')
 sources=[b.RAW/zone/'model_export'/(name+'_lod0.xmodel_export') for name in names]
 inputs=[{'file':p.relative_to(b.RAW).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]
 recipe={'baker':BAKER_HASH,'sources':inputs,'extras':extras,'hound':hound,'repeat_uv':preset=='mob_of_dead' and i==2,'texture_size':256 if preset=='mob_of_dead' and i==2 else 128,'shadow_lift':.72 if preset=='mob_of_dead' and i==2 else 1}
 cache=ROOT/'build/zombie-presets/bake-cache'/('hound_'+preset if hound else preset)/(str(i)+'.json');cache.parent.mkdir(parents=True,exist_ok=True)
 if cache.exists() and target.exists():
  old=json.loads(cache.read_text(encoding='utf-8'))
  if old.get('recipe')==recipe:return old['record']
 coverage,gear=b.bake(b.RAW/zone,names,target,hound,extra_models=extras,texture_size=256 if preset=='mob_of_dead' and i==2 else 128,shadow_lift=.72 if preset=='mob_of_dead' and i==2 else 1,repeat_uv=preset=='mob_of_dead' and i==2)
 record={'preset':'hound_'+preset if hound else preset,'variant':i,'coverage':coverage,'gearSurfaces':gear,'sources':inputs,'texture':target.relative_to(b.DEST).as_posix()}
 if gear:record['gear']=target.with_suffix('.json.gz').relative_to(b.DEST).as_posix()
 cache.write_text(json.dumps({'recipe':recipe,'record':record},indent=2)+'\n',encoding='utf-8')
 return record

def main():
 specs=json.loads(SPECS.read_text(encoding='utf-8'));jobs=[]
 for preset,settings in specs.items():
  jobs.extend((preset,i,settings['zone'],v['models'],v.get('extras',[]),False) for i,v in enumerate(settings['variants']))
 for preset,zone,names in [('t4','t4/nazi_zombie_factory',['zombie_wolf','zombie_wolf_variant_a']),('t5','t5/zombie_theater',['zombie_wolf']),('t6','t6/zm_nuked',['zombie_wolf'])]:
  jobs.extend((preset,i,zone,[name],[],True) for i,name in enumerate(names))
 records={};errors=[]
 with ProcessPoolExecutor(max_workers=4) as pool:
  pending={pool.submit(convert,job):job for job in jobs}
  for task in as_completed(pending):
   job=pending[task]
   try:
    row=task.result();records[(row['preset'],row['variant'])]=row;print('BAKED',row['preset'],row['variant'],flush=True)
   except Exception as error:errors.append((job[0],job[1],str(error)));print('FAILED',job[0],job[1],str(error),flush=True)
 report={'variants':list(records.values()),'errors':errors}
 (ROOT/'assets-source/26.7/enemy-skins/conversion-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 if errors:raise SystemExit(str(errors))
 catalog={'zombies':{},'hounds':{}}
 for preset,settings in specs.items():
  catalog['zombies'][preset]={'hound':settings['hound'],'variants':[{k:v for k,v in records[(preset,i)].items() if k in ['texture','gear']} for i in range(len(settings['variants']))]}
 for preset,settings in specs.items():
  for i,v in enumerate(settings['variants']):
   if v.get('headgear_kind'):catalog['zombies'][preset]['variants'][i]['headgear']={'kind':v['headgear_kind'],'texture':'zombierool:textures/entities/presets/headgear/'+v['headgear_kind']+'.png'}
 catalog['zombies']['original']={'hound':'original','variants':[{'texture':'textures/entities/zombie_32_'+str(i)+'.png'} for i in range(5)]}
 for preset,count in [('t4',2),('t5',1),('t6',1)]:
  catalog['hounds'][preset]={'variants':[{'texture':records[('hound_'+preset,i)]['texture']} for i in range(count)]}
 catalog['hounds']['original']={'variants':[{'texture':'textures/entities/carrie-the-dark-puppy.png'}]}
 import runpy
 catalog=runpy.run_path(str(ROOT/'tools/consolidate-enemy-presets.py'))['consolidate'](catalog)
 (b.DEST/'skins').mkdir(exist_ok=True);(b.DEST/'skins/enemy_presets.json').write_text(json.dumps(catalog,indent=2)+'\n',encoding='utf-8')
 for other in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool']:
  shutil.copytree(b.DEST/'textures/entities/presets',other/'textures/entities/presets',dirs_exist_ok=True);shutil.copytree(b.DEST/'skins',other/'skins',dirs_exist_ok=True)
 print('PASS:',len(records),'appearances, native pixel scale and source gear converted',flush=True)
if __name__=='__main__':main()
