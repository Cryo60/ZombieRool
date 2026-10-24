from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,base64,shutil

ROOT=Path(__file__).resolve().parents[1]
GENERATED=Path('C:/Users/ecorange1/.codex/generated_images/01a12136-59bd-7dd0-b982-6050a921ab98')
OUT=Path('C:/Users/ecorange1/Desktop/ZombieRool - Iron Defense Door - choix 26.7')
OUT.mkdir(parents=True,exist_ok=True)
for folder in ['textures_16x16','originaux']:(OUT/folder).mkdir(exist_ok=True)
choices=json.loads((ROOT/'assets-source/26.7/choices-manifest.json').read_text())
remaining={c['fnv']:c for c in choices}
for p in GENERATED.glob('*.png'):
    if p.stat().st_mtime< __import__('datetime').datetime(2026,10,10,23,1).timestamp():continue
    h=2166136261
    for byte in base64.b64encode(p.read_bytes()):h=((h^byte)*16777619)&0xffffffff
    if h not in remaining:continue
    choice=remaining.pop(h);name=choice['name']+'.png'
    shutil.copy2(p,OUT/'originaux'/name)
    image=Image.open(p).convert('RGBA').resize((16,16),Image.Resampling.NEAREST)
    image.save(OUT/'textures_16x16'/name)
    image.resize((256,256),Image.Resampling.NEAREST).save(OUT/name)
    choice['source']=str(p);choice['preview']=str(OUT/name)
assert not remaining,'Missing generated choices: '+repr(remaining)
sheet=Image.new('RGB',(1200,680),'#202328');draw=ImageDraw.Draw(sheet)
font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',15)
for i,c in enumerate(choices):
    x=(i%4)*300+22;y=(i//4)*340+16
    for py in range(0,256,16):
        for px in range(0,256,16):draw.rectangle((x+px,y+py,x+px+15,y+py+15),fill='#3c4148' if (px//16+py//16)%2 else '#30343b')
    image=Image.open(OUT/(c['name']+'.png')).convert('RGBA');sheet.paste(image,(x,y),image)
    title=c['name']+'.png';draw.text((x,y+270),title,fill='white',font=font)
    draw.text((x,y+294),'Texture Minecraft : 16 x 16',fill='#aeb8c3',font=font)
sheet.save(OUT/'00_comparaison.png')
(OUT/'LISEZ_MOI.txt').write_text('Choisis un fichier parmi 01...08 et donne-moi son nom.\nLes PNG principaux sont des apercus agrandis sans lissage.\nLes vraies textures 16x16 transparentes sont dans textures_16x16.\nLes originaux sont conserves dans originaux.\n00_comparaison.png rassemble les huit choix.\nAucune variante n\'est encore choisie comme texture definitive.\n',encoding='utf-8')
(OUT/'prompts.json').write_text(json.dumps(choices,indent=2,ensure_ascii=False),encoding='utf-8')
print('Prepared '+str(len(choices))+' named textures in '+str(OUT))
