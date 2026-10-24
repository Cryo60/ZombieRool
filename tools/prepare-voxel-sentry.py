"""Minecraft cuboid adaptation of the provided sentry silhouette; no runtime OBJ parsing."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1];A=ROOT/'src/main/resources/assets/zombierool'
textures={'body':'minecraft:block/green_terracotta','metal':'minecraft:block/polished_deepslate','dark':'minecraft:block/black_concrete','steel':'minecraft:block/gray_concrete','ammo':'minecraft:block/green_concrete','brass':'minecraft:block/yellow_terracotta','particle':'minecraft:block/gray_concrete'}
def box(a,b,t,rotation=None):
 e={'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
 if rotation:e['rotation']=rotation
 return e
def rot(axis,angle,origin):return {'axis':axis,'angle':angle,'origin':origin}
base=[box([1,0,2],[5,1,6],'metal'),box([11,0,2],[15,1,6],'metal'),box([6,0,12],[10,1,16],'metal'),box([6,1,4],[8,12,6],'body',rot('z',-22.5,[8,12,5])),box([8,1,4],[10,12,6],'body',rot('z',22.5,[8,12,5])),box([7,1,8],[9,12,10],'body',rot('x',-22.5,[8,12,8])),box([5,11,5],[11,14,11],'metal'),box([6,14,6],[10,16,10],'steel')]
head=[box([5,15,5],[11,22,14],'body'),box([4,17,5],[5,21,13],'metal'),box([11,17,5],[12,21,13],'metal'),box([5,18,2],[11,23,7],'metal'),box([6,23,7],[10,24,11],'dark'),box([7,24,10],[9,25,12],'steel'),box([12,15,8],[18,21,14],'ammo'),box([12,21,8],[18,22,14],'metal'),box([13,18,7.8],[17,19,8],'brass'),box([11,21,8],[13,22,10],'brass'),box([10,22,7],[12,23,9],'brass')]
for x,y in [(6,18),(9,18),(6,21),(9,21)]:head.append(box([x,y,-9],[x+1,y+1,4],'dark'))
head.extend([box([5.5,17.5,-10],[10.5,22.5,-8],'metal'),box([5.5,17.5,-4],[10.5,22.5,-2],'metal'),box([6,18,-10.2],[10,22,-10],'dark')])
display={'gui':{'rotation':[25,35,0],'translation':[0,-3,0],'scale':[.60,.60,.60]},'ground':{'scale':[.35,.35,.35]},'thirdperson_righthand':{'scale':[.45,.45,.45]}}
def save(rel,elements):
 p=A/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'textures':textures,'elements':elements,'display':display},indent=2)+'\n')
save('models/block/deployed_sentry_turret.json',base);save('models/item/sentry_turret.json',base+head);save('models/item/sentry_tripod.json',base);save('models/item/sentry_receiver.json',head);save('models/item/sentry_ammo.json',[box([3,2,5],[13,13,11],'ammo'),box([2,13,4],[14,15,12],'metal'),box([4,8,4.9],[12,10,5],'brass')])
for basepath in [ROOT/'resources/assets/zombierool',ROOT/'assets/zombierool',ROOT/'build/resources/main/assets/zombierool']:
 for rel in ['models/block/deployed_sentry_turret.json']+['models/item/'+name+'.json' for name in ['sentry_turret','sentry_tripod','sentry_receiver','sentry_ammo']]:
  p=basepath/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(A/rel,p)
print('Voxel sentry:',len(base)+len(head),'cuboids /',(len(base)+len(head))*6,'faces')
