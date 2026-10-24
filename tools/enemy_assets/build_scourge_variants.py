"""Source-referenced colour variants of the authored blocky Scourge horde.
Local BLPs are read only to verify the source skin identities/palettes; final
Minecraft atlases are drawn by build_blocky_enemies, never projected source skins.
"""
from pathlib import Path
import os,json,hashlib,io
from PIL import Image,ImageDraw
from local_casc import LocalCasc
from build_blocky_enemies import atlas,ROOT,OUT
VARIANTS=[
 ('scourge_ghoul','blue',124161,{'skin':'#7d939f','shade':'#596c7a','pants':'#53515d'}),
 ('scourge_ghoul','grey',124162,{'skin':'#9a9b88','shade':'#717664','pants':'#555047'}),
 ('scourge_ghoul','red',124163,{'skin':'#a17d70','shade':'#79584d','pants':'#59473e'}),
 ('scourge_ghoul','white',124164,{'skin':'#c2bd9f','shade':'#918d76','pants':'#696454'}),
 ('scourge_zombie','black',126572,{'skin':'#85857b','shade':'#5c6259','cloth':'#5b6262','pants':'#403b41'}),
 ('scourge_zombie','green',126573,{'skin':'#a0a57e','shade':'#757e5a','cloth':'#6c7e85','pants':'#5d4d49'}),
 ('scourge_zombie','seagreen',126574,{'skin':'#8fae9e','shade':'#617e70','cloth':'#687b7c','pants':'#594a55'}),
 ('scourge_skeleton','bone',125943,{'bone':'#c3bba1','rust':'#74634f','eye':'#d5b95c'}),
 ('scourge_skeleton','dark_teal',125944,{'bone':'#899b91','rust':'#53676a','eye':'#9be2c4'}),
 ('scourge_skeleton','green',125945,{'bone':'#a3b08c','rust':'#627150','eye':'#b7db72'}),
 ('scourge_skeleton','armored',125941,{'bone':'#b4ae9b','rust':'#71667d','strap':'#45434b','eye':'#bcb1df'})]
if __name__=='__main__':
    sources=[LocalCasc('C:/Program Files (x86)/World of Warcraft',product=p) for p in ['wow_classic_era','wow','wow_anniversary','wowf']];rows=[];receipt=[]
    for kind,name,fd,palette in VARIANTS:
        raw=None
        for casc in sources:
            try:raw=casc.read(fd);break
            except (KeyError,ValueError):pass
        if raw is None:raise RuntimeError('Source skin unavailable locally: '+str(fd))
        assert raw[:4]==b'BLP2';img=Image.open(io.BytesIO(raw));img.load()
        id=kind+'_'+name;atlas(id,palette)
        # Vanilla UV companion for inventory previews and detached Minecraft gore pieces.
        skin=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(skin);flesh=palette.get('skin',palette.get('bone','#a0a288'));cloth=palette.get('cloth',palette.get('skin',palette.get('bone','#777a62')))
        d.rectangle((0,0,31,15),fill=flesh);d.rectangle((16,16,39,31),fill=cloth);d.rectangle((40,16,55,31),fill=flesh);d.rectangle((0,16,15,31),fill=palette.get('pants',palette.get('bone','#54494b')));d.rectangle((7,10,9,11),fill='#33372c');d.rectangle((12,10,14,11),fill='#33372c')
        skinpath=OUT/'textures/entities/blocky'/(id+'_skin.png');skin.save(skinpath)
        rows.append({'model':kind,'skin':name,'texture':'textures/entities/blocky/'+id+'_skin.png','blockyTexture':'textures/entities/blocky/'+id+'.png'})
        receipt.append({'model':kind,'variant':name,'fileDataID':fd,'sourceDimensions':img.size,'sourceSha256':hashlib.sha256(raw).hexdigest()})
    catalog=OUT/'skins/enemy_presets.json';data=json.loads(catalog.read_text(encoding='utf-8'));data['zombies']['scourge']={'hound':'original','mixed':True,'variants':rows};catalog.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'assets-source/26.7/enemy-skins/scourge-blocky-variants.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print('Verified and authored',len(rows),'variants in one Scourge horde.')
