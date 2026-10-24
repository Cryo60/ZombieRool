"""Convert original art to game resolution and extend native block models. No mod publication."""
from pathlib import Path
from PIL import Image
import json,re

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'assets-source/26.7'
SOURCE=ROOT/'src/main/resources/assets/zombierool'
ROOTS=[ROOT/p for p in ['src/main/resources/assets/zombierool','assets/zombierool','resources/assets/zombierool']]

def cube(start,end,texture,uv=None):
    return {'from':start,'to':end,'faces':{face:{'texture':'#'+texture,**({'uv':uv} if uv else {})} for face in ['north','south','east','west','up','down']}}

for root in ROOTS:
    def write(name,data):
        p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    for name,size in [('iron_barricade',(16,16)),('activator',(32,32)),('receptor',(32,32))]:
        image=ART/'iron_barricade_selected.png' if name=='iron_barricade' else ART/(name+'.png')
        Image.open(image).convert('RGBA').resize(size,Image.Resampling.NEAREST).save(root/'textures/block'/('logic_'+name+'.png'))
    for stage in range(5):
        image=Image.open(ART/f'iron_barricade_stage{stage}.png').convert('RGBA').resize((16,16),Image.Resampling.NEAREST) if stage else Image.new('RGBA',(16,16))
        image.save(root/'textures/block'/f'logic_iron_barricade_stage{stage}.png')
    channels=['alpha','beta','omega','ultima','gamma','delta','epsilon','zeta','eta','theta','iota','kappa','lambda','sigma']
    colors=['cyan','orange','purple','red','green','blue','yellow','light_blue','lime','pink','gray','brown','white','black']
    for index,channel in enumerate(channels):
        for role in ['activator','receptor']:
            name=channel+'_'+role
            write('models/block/'+name+'.json',{'textures':{'face':'zombierool:block/logic_'+role,'badge':'minecraft:block/'+colors[index]+'_concrete','particle':'zombierool:block/logic_'+role},'elements':[cube([0,0,0],[16,16,16],'face'),cube([12,1,-.01],[15,4,0],'badge')]})
            write('blockstates/'+name+'.json',{'variants':{'':{'model':'zombierool:block/'+name}}})
            write('models/item/'+name+'.json',{'parent':'zombierool:block/'+name})
    write('models/block/activator.json',{'parent':'minecraft:block/cube_all','textures':{'all':'zombierool:block/logic_activator'}})
    write('models/item/activator.json',{'parent':'zombierool:block/activator','display':{'thirdperson':{'rotation':[10,-45,170],'translation':[0,1.5,-2.75],'scale':[.375,.375,.375]}}})
    write('blockstates/activator.json',{'variants':{'':{'model':'zombierool:block/activator'}}})
    for name in ['jump_link','crawler_wall_link','zipline']:
        if name=='zipline' and (ROOT/'src/main/resources/assets/zombierool/blockstates/zipline.json').exists():continue # Preserve the authored zipline across unrelated asset generation.
        variants={}
        for phase in range(3):
            model=name+'_'+str(phase)
            if name=='zipline':
                elements=[cube([3,0,4],[5,32,12],'metal'),cube([11,0,4],[13,32,12],'metal'),cube([3,29,4],[13,32,12],'metal')]
                textures={'metal':'minecraft:block/iron_block','particle':'minecraft:block/iron_block'}
            else:
                elements=[cube([3,0,3],[13,3,13],'metal')]
                textures={'metal':'zombierool:block/traversal_node_'+str(0 if phase==0 else 2),'particle':'zombierool:block/traversal_node_0'}
            write('models/block/'+model+'.json',{'textures':textures,'elements':elements})
            if name=='zipline':
                for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
                    variants[f'facing={facing},phase={phase}']={'model':'zombierool:block/'+model,'y':rotation}
            else:variants['phase='+str(phase)]={'model':'zombierool:block/'+model}
        item={'parent':'zombierool:block/'+name+'_0'}
        if name=='zipline':item['display']={'gui':{'rotation':[25,225,0],'translation':[0,-3,0],'scale':[.35,.35,.35]}}
        write('blockstates/'+name+'.json',{'variants':variants});write('models/item/'+name+'.json',item)
    write('models/block/one_way_gate.json',{'textures':{'metal':'zombierool:block/traversal_node_0','particle':'zombierool:block/traversal_node_0'},'elements':[cube([3,0,3],[13,2,13],'metal')]})
    write('blockstates/one_way_gate.json',{'variants':{'facing='+face:{'model':'zombierool:block/one_way_gate','y':rot} for face,rot in [('north',270),('east',0),('south',90),('west',180)]}})
    write('models/item/one_way_gate.json',{'parent':'zombierool:block/one_way_gate'})
    textures={'metal':'minecraft:block/netherite_block','rim':'minecraft:block/iron_block','soul':'minecraft:block/redstone_block','particle':'minecraft:block/netherite_block'}
    body=[cube([2,0,2],[14,2,14],'metal'),cube([2,2,2],[4,9,14],'metal'),cube([12,2,2],[14,9,14],'metal'),cube([4,2,2],[12,9,4],'metal'),cube([4,2,12],[12,9,14],'metal'),cube([6,4,1.7],[10,8,2.2],'soul'),cube([6,4,13.8],[10,8,14.3],'soul')]
    lid=cube([1.5,9,1.5],[14.5,12,14.5],'metal')
    write('models/block/soul_chest.json',{'textures':textures,'elements':body+[lid]})
    open_lid=json.loads(json.dumps(lid));open_lid['rotation']={'origin':[8,9,14],'axis':'x','angle':45,'rescale':False}
    write('models/block/soul_chest_open.json',{'textures':textures,'elements':body+[open_lid]})
    completed_textures=dict(textures,metal='minecraft:block/gilded_blackstone',soul='minecraft:block/amethyst_block',particle='minecraft:block/gilded_blackstone')
    write('models/block/soul_chest_complete.json',{'textures':completed_textures,'elements':body+[lid]+[cube([4,12,4],[12,12.4,12],'soul')]})
    write('blockstates/soul_chest.json',{'variants':{f'completed={done},facing={facing},open={opened}':{'y':rotation,'model':'zombierool:block/'+('soul_chest_complete' if done=='true' else 'soul_chest_open' if opened=='true' else 'soul_chest')} for done in ['false','true'] for opened in ['false','true'] for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]}});write('models/item/soul_chest.json',{'parent':'zombierool:block/soul_chest'})
    # Reuse original hinge geometry; damage removes whole bars via separate 16x16 textures.
    states=json.loads((SOURCE/'blockstates/defense_door.json').read_text())
    write('blockstates/iron_defense_door.json',json.loads(json.dumps(states).replace('zombierool:block/defense_door','zombierool:block/iron_defense_door')))
    for source in list((SOURCE/'models/block').glob('defense_door_*.json')):
        name=source.stem.replace('defense_door','iron_defense_door');old=json.loads(source.read_text());stage=re.search(r'stage(\d)',name);count=int(stage[1]) if stage else 6
        write('models/block/'+source.name,old)
        if '_bottom_' in name:
            write('models/block/'+name+'.json',{'parent':'zombierool:block/'+source.stem});continue
        texture='zombierool:block/logic_iron_barricade'+(f'_stage{count}' if count<5 else '')
        old['textures'].update(top=texture,bottom=texture,particle=texture)
        write('models/block/'+name+'.json',old)
    write('models/item/iron_defense_door.json',{'textures':{'wood':'zombierool:block/door_wood_lower','bars':'zombierool:block/logic_iron_barricade'},'elements':[cube([0,0,7],[16,16,9],'wood',[0,0,16,16]),cube([0,16,7],[16,32,9],'bars',[0,0,16,16])],'display':{'gui':{'rotation':[0,0,0],'translation':[0,-3.2,0],'scale':[.4,.4,.4]}}})
    # A second cabinet skin; keep its existing block entity and interaction contract.
    for opened in [False,True]:
        # Solid gray cabinet, with separate frame and a wire mesh door. No coplanar sheets.
        parts=[cube([1,-16,11],[15,16,13],'metal'),cube([1,-16,3],[3,16,11],'metal'),cube([13,-16,3],[15,16,11],'metal'),cube([3,-16,3],[13,-14,11],'metal'),cube([3,14,3],[13,16,11],'metal')]
        door=[cube([3,-14,2],[4,14,3],'metal'),cube([12,-14,2],[13,14,3],'metal'),cube([4,-14,2],[12,-13,3],'metal'),cube([4,13,2],[12,14,3],'metal'),cube([11,-1,1.4],[12,3,2],'dark')]
        for x in [4,6,8,10,12]:door.append(cube([x,-13,2.35],[x+.3,13,2.65],'dark'))
        for y in range(-12,13,2):door.append(cube([4,y,2.3],[12,y+.3,2.7],'dark'))
        if opened:
            # Rotate the complete door 90 degrees around the left hinge, in native cuboids.
            for piece in door:
                x0,y0,z0=piece['from'];x1,y1,z1=piece['to'];piece['from']=[3+z0-2,y0,2-(x1-3)];piece['to']=[3+z1-2,y1,2-(x0-3)]
        parts.extend(door)
        for piece in parts:
            for face in piece['faces'].values():face['uv']=[0,0,16,16]
        write('models/block/secret_weapon_rack_'+('open' if opened else 'closed')+'.json',{'textures':{'metal':'minecraft:block/light_gray_concrete','dark':'minecraft:block/gray_concrete','particle':'minecraft:block/light_gray_concrete'},'elements':parts})
    cabinet=json.loads((SOURCE/'blockstates/blind_buy_cabinet.json').read_text());write('blockstates/secret_weapon_rack.json',json.loads(json.dumps(cabinet).replace('zombierool:block/blind_buy_cabinet','zombierool:block/secret_weapon_rack')))
    write('models/item/secret_weapon_rack.json',{'parent':'zombierool:block/secret_weapon_rack_closed','display':{'gui':{'rotation':[25,225,0],'translation':[0,3,0],'scale':[.35,.35,.35]}}})
    # The original cabinet also spans two blocks; its item should fit a single inventory slot.
    cabinet_item=json.loads((SOURCE/'models/item/blind_buy_cabinet.json').read_text());cabinet_item.setdefault('display',{})['gui']={'rotation':[25,225,0],'translation':[.6,-.3,0],'scale':[.28,.28,.28]}
    write('models/item/blind_buy_cabinet.json',cabinet_item)
    for model in ['blind_buy_cabinet_closed','blind_buy_cabinet_open']:
        write('models/block/'+model+'.json',json.loads((SOURCE/'models/block'/(model+'.json')).read_text()))
print('Generated 26.7 block variants and 20 new signal blocks in all resource mirrors.')
