"""Native Minecraft workbench geometry based on the local T6 p6_zm_work_bench proportions."""
from pathlib import Path
import json,sys,random,hashlib
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'build/video-python'))
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1];ROOTS=[ROOT/'src/main/resources',ROOT/'resources',ROOT]
def write(path,data):
    for root in ROOTS:
        p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
rng=random.Random(62026);im=Image.new('RGB',(64,64));pix=im.load()
for y in range(64):
    for x in range(64):
        grain=rng.randint(-9,9)+(x%16==0)*-22;pix[x,y]=(max(0,137+grain),max(0,111+grain),max(0,77+grain))
d=ImageDraw.Draw(im)
for _ in range(100):
    x=rng.randrange(64);y=rng.randrange(64);d.line((x,y,x,y+rng.randrange(2,16)),fill=(105,83,58))
for root in ROOTS:
    p=root/'assets/zombierool/textures/block/buildable_pine.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
def box(name,a,b,tex='wood'):
    return {'name':name,'from':a,'to':b,'faces':{s:{'uv':[0,0,16,16],'texture':'#'+tex} for s in ['up','down','north','south','east','west']}}
# T6: 88-unit wide pine top, 44-unit height, 31-unit depth; four square legs and low H bracing.
elements=[]
for i in range(4):elements.append(box('Worn pine worktop plank',[0,14.5,2+i*3],[32,16,4.97+i*3]))
for x in [1,29]:
    for z in [2.5,11.5]:elements.append(box('Square pine leg',[x,0,z],[x+2,14.5,z+2]))
for z in [2.5,11.5]:
    elements.append(box('Upper longitudinal apron',[1,12,z],[31,14.5,z+1.7]))
    elements.append(box('Low longitudinal brace',[.5,4,z],[31.5,6,z+1.5]))
for x in [1,29]:elements.append(box('Cross brace',[x,3.5,2],[x+2,5.5,14]))
for x in [2,30]:
    for z in [3.5,12.5]:elements.append(box('Recessed nail',[x,16,z],[x+.35,16.025,z+.35],'metal'))
textures={'wood':'zombierool:block/buildable_pine','metal':'minecraft:block/gray_concrete','particle':'zombierool:block/buildable_pine'}
for right in [False,True]:
    clipped=[];lo=16 if right else 0;hi=lo+16
    for e in elements:
        a=e['from'].copy();b=e['to'].copy();a[0]=max(lo,a[0]);b[0]=min(hi,b[0])
        if a[0]>=b[0]:continue
        a[0]-=lo;b[0]-=lo;clipped.append(box(e['name'],a,b,'metal' if e['faces']['up']['texture']=='#metal' else 'wood'))
    write('assets/zombierool/models/block/buildable_workbench_'+('right' if right else 'left')+'.json',{'textures':textures,'elements':clipped})
variants={}
for face,rot in [('north',0),('east',90),('south',180),('west',270)]:
    for right in [False,True]:variants[f'facing={face},right={str(right).lower()}']={'model':'zombierool:block/buildable_workbench_'+('right' if right else 'left'),'y':rot}
write('assets/zombierool/blockstates/buildable_workbench.json',{'variants':variants})
write('assets/zombierool/models/item/buildable_workbench.json',{'textures':textures,'elements':elements,'display':{'gui':{'rotation':[25,-40,0],'translation':[-4,-3,0],'scale':[.55,.55,.55]},'ground':{'translation':[-4,2,0],'scale':[.4]*3},'firstperson_righthand':{'translation':[-4,0,0],'scale':[.5]*3},'thirdperson_righthand':{'translation':[-4,0,0],'scale':[.4]*3}}})
pad=[box('Placement marker',[3,0,3],[13,.7,13],'metal')]
write('assets/zombierool/models/block/buildable_part.json',{'textures':textures,'elements':pad})
write('assets/zombierool/blockstates/buildable_part.json',{'variants':{'':{'model':'zombierool:block/buildable_part'}}})
write('assets/zombierool/models/item/buildable_part.json',{'parent':'zombierool:block/buildable_destination'})
write('data/zombierool/loot_tables/blocks/buildable_workbench.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'zombierool:buildable_workbench'}],'conditions':[{'condition':'minecraft:block_state_property','block':'zombierool:buildable_workbench','properties':{'right':'false'}}]}]})
write('data/zombierool/loot_tables/blocks/buildable_part.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'zombierool:buildable_part','functions':[{'function':'minecraft:copy_nbt','source':'block_entity','ops':[{'source':k,'target':'BlockEntityTag.'+k,'op':'replace'} for k in ['recipe','part','visual']]}]}]}]})
fr={'career_compact':'Niv. %s · %s XP', 'back':'Retour', 'title':'Établi — objets à assembler','support_title':'Support de pièce','part_group':'Groupe de la pièce (facultatif)','automatic_part':'Automatique selon l’objet','held_part':'Apparence : objet tenu','keep_part':'Garder la pièce enregistrée','save_part':'Enregistrer cette pièce','chosen_recipe':'Objet à assembler : %s','support_help':'Même recette + même groupe = emplacements alternatifs. Un seul sera choisi par partie.','free_only':'Tous les établis acceptent toutes les recettes.','advanced':'Options de la recette','tab_title':'Pièces de la carte','scroll':'molette','status_missing':'À trouver','status_self':'Vous la portez','status_team':'Coéquipier','status_installed':'Installée'}
en={'career_compact':'Level %s · %s XP', 'back':'Back', 'title':'Workbench — buildable objects','support_title':'Component support','part_group':'Part group (optional)','automatic_part':'Automatic from the item','held_part':'Appearance: held item','keep_part':'Keep saved component','save_part':'Save this component','chosen_recipe':'Buildable: %s','support_help':'Same recipe + same group = alternative locations. Only one spawns per match.','free_only':'Every workbench accepts every recipe.','advanced':'Recipe options','tab_title':'Map components','scroll':'scroll','status_missing':'Not found','status_self':'Carried by you','status_team':'Teammate','status_installed':'Installed'}
for lang in ['en_us','fr_fr','de_de','es_es','pt_br','ru_ru']:
    path='assets/zombierool/lang/'+lang+'.json';n=json.loads((ROOTS[0]/path).read_text(encoding='utf8'));values=fr if lang=='fr_fr' else en
    n.update({'gui.zombierool.buildable.'+k:v for k,v in values.items()})
    n.update({'block.zombierool.buildable_workbench':'Établi' if lang=='fr_fr' else 'Buildable workbench','block.zombierool.buildable_part':'Support de pièce' if lang=='fr_fr' else 'Component support'})
    n['message.zombierool.buildable.add']='[%s] Ajouter la pièce' if lang=='fr_fr' else '[%s] Add component'
    n['message.zombierool.buildable.swap']='[%s] Échanger avec %s' if lang=='fr_fr' else '[%s] Swap for %s'
    n['message.zombierool.buildable.bench_short']='[%s] Assembler %s — %s/%s' if lang=='fr_fr' else '[%s] Assemble %s — %s/%s'
    n['message.zombierool.buildable.assembling_bar']='Assemblage' if lang=='fr_fr' else 'Assembling'
    write(path,n)
ref=Path('C:/Users/ecorange1/AppData/Local/Temp/ZR-T6-workbench-ref/model_export/p6_zm_work_bench_lod0.obj')
if ref.exists():
    out=ROOT/'assets-source/26.7/buildable-workbench-reference.json';out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({'reference':'T6 zm_transit: p6_zm_work_bench','source':'D:/Jeux/pluto_t6_full_game/zone/all/zm_transit.ff','reference_mesh_sha256':hashlib.sha256(ref.read_bytes()).hexdigest(),'implementation':'Native two-block Minecraft geometry; original pine texture. Reference mesh is not bundled.'},indent=2)+'\n')
print('Workbench, part support, loot, native models and translations generated.')

# Keep newer editor and placement assets when regenerating the original native blocks.
import runpy
runpy.run_path(str(ROOT/"tools/prepare-buildable-ui.py"))
runpy.run_path(str(ROOT/"tools/prepare-buildable-placement.py"))
