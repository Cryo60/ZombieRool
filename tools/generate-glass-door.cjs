const fs=require('fs'),path=require('path');
for(const root of ['src/main/resources/assets/zombierool','assets/zombierool']){
 const variants={};for(let stage=0;stage<=7;stage++){let dest=path.join(root,`textures/block/glass_door_${stage}.png`);fs.copyFileSync(`C:/Users/ecorange1/Desktop/atlas/porte_vitree_sombre_atlas_stage_${stage}.png`,dest);}
 for(const breached of [false,true])for(let stage=0;stage<=7;stage++)for(const half of ['lower','upper']){
 const id=`glass_defense_door_${breached?'planks':'glass'}_${stage}_${half}`;
 let glass=`zombierool:block/glass_door_${breached?0:stage}`;
 const faces={north:{texture:'#glass',uv:[0,half==='upper'?0:8,16,half==='upper'?8:16]},south:{texture:'#glass',uv:[16,half==='upper'?0:8,0,half==='upper'?8:16]},east:{texture:'#glass',uv:[15,half==='upper'?0:8,16,half==='upper'?8:16]},west:{texture:'#glass',uv:[0,half==='upper'?0:8,1,half==='upper'?8:16]},up:{texture:'#glass',uv:[0,0,16,1]},down:{texture:'#glass',uv:[0,15,16,16]}};
 let elements=[{from:[0,0,0],to:[16,16,3],faces}];let textures={glass,particle:glass};
 if(breached&&stage>0&&half==='upper'){textures.boards=`zombierool:block/barricade${Math.min(stage,5)}`;elements.push({from:[0,0,-.02],to:[16,16,3.02],faces:{north:{texture:'#boards',uv:[0,0,16,16]},south:{texture:'#boards',uv:[16,0,0,16]}}});}
 fs.writeFileSync(path.join(root,`models/block/${id}.json`),JSON.stringify({ambientocclusion:false,textures,elements}));
 for(const facing of ['north','east','south','west'])variants[`breached=${breached},stage=${stage},half=${half},facing=${facing}`]={model:'zombierool:block/'+id,y:{north:0,east:90,south:180,west:270}[facing]};
 }
 fs.writeFileSync(path.join(root,'blockstates/glass_defense_door.json'),JSON.stringify({variants}));fs.writeFileSync(path.join(root,'models/item/glass_defense_door.json'),JSON.stringify({parent:'minecraft:item/generated',textures:{layer0:'zombierool:block/glass_door_7'}}));
 for(const lang of ['en_us','fr_fr']){let file=path.join(root,'lang',lang+'.json'),data=JSON.parse(fs.readFileSync(file));const en=lang==='en_us';Object.assign(data,{
 'block.zombierool.glass_defense_door':en?'Glass Defense Door':'Porte défensive vitrée',
 'shape.zombierool.door':en?'Iron door':'Porte en fer',
 'gui.zombierool.texture_editor':en?'Pixel editor':'Éditeur de pixels',
 'gui.zombierool.texture_color':en?'Color (RRGGBBAA)':'Couleur (RRGGBBAA)',
 'gui.zombierool.texture_tool0':en?'Brush':'Pinceau','gui.zombierool.texture_tool1':en?'Eraser':'Gomme','gui.zombierool.texture_tool2':en?'Fill':'Remplissage','gui.zombierool.texture_tool3':en?'Eyedropper':'Pipette',
 'gui.zombierool.texture_brush':en?'Brush size: %s':'Taille du pinceau : %s',
 'gui.zombierool.texture_undo':en?'Undo':'Annuler','gui.zombierool.texture_import':en?'Import atlas':'Importer un atlas',
 'gui.zombierool.texture_import_file':en?'PNG in custom_blocks/import':'PNG dans custom_blocks/import',
 'gui.zombierool.texture_save':en?'Save':'Enregistrer','gui.zombierool.texture_saved':en?'Saved without resource reload':'Enregistré sans reload des ressources',
 'gui.zombierool.texture_error':en?'Invalid file or atlas (square, 4x3 net or 1x2 door)':'Fichier/atlas invalide (carré, patron 4x3 ou porte 1x2)',
 'message.zombierool.maptex.apply':en?'Update textures':'Actualiser les textures','message.zombierool.maptex.reload':en?'Map textures updated':'Textures du monde actualisées'});fs.writeFileSync(file,JSON.stringify(data,null,2));}
}
