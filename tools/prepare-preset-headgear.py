from pathlib import Path
import sys,shutil
sys.path[:0]=['build/video-python','build/audio-python'];from PIL import Image,ImageDraw
r=Path('D:/Dev/ZombieRool');a=r/'src/main/resources/assets/zombierool';folder=a/'textures/entities/presets/headgear';folder.mkdir(exist_ok=True)
colors={'steel':(75,80,66),'mp':(194,192,175),'officer':(66,69,56),'field':(83,80,62),'guard':(41,49,64),'cowboy':(93,69,46),'miner':(140,117,65),'pickelhaube':(62,59,45),'crusader':(104,102,88),'beanie':(77,76,71)}
for kind,base in colors.items():
 im=Image.new('RGBA',(128,128),base+(255,));draw=ImageDraw.Draw(im)
 for y in range(128):
  for x in range(128):
   n=((x*7+y*13)%5)-2;im.putpixel((x,y),tuple(max(0,min(255,c+n)) for c in base)+(255,))
 draw.rectangle((0,22,76,25),fill=tuple(max(0,c-17) for c in base)+(255,));draw.line((18,18,35,18),fill=tuple(min(255,c+24) for c in base)+(255,));draw.rectangle((0,50,100,54),fill=tuple(max(0,c-24) for c in base)+(255,))
 if kind in ['officer','guard','field']:draw.rectangle((25,21,28,24),fill=(159,144,89,255))
 if kind=='mp':
  for offset,glyph in [(21,['10001','11011','10101','10001','10001']),(28,['1110','1001','1110','1000','1000'])]:
   for yy,line in enumerate(glyph):
    for xx,on in enumerate(line):
     if on=='1':draw.point((offset+xx,19+yy),fill=(45,47,40,255))
 if kind in ['pickelhaube','crusader','miner']:draw.rectangle((90,4,95,7),fill=(174,160,104,255))
 im.save(folder/(kind+'.png'))
for color,rgb in {'default':(235,153,39),'red':(240,30,19),'blue':(47,142,230),'green':(62,225,51)}.items():
 im=Image.new('RGBA',(128,128));draw=ImageDraw.Draw(im)
 for x in [20,28]:draw.line((x,22,x+2,22),fill=rgb+(255,))
 im.save(a/'textures/entities/presets'/('eyes_'+color+'.png'))
for other in [r/'resources/assets/zombierool',r/'assets/zombierool']:
 shutil.copytree(folder,other/'textures/entities/presets/headgear',dirs_exist_ok=True)
 for p in (a/'textures/entities/presets').glob('eyes_*.png'):shutil.copy2(p,other/'textures/entities/presets'/p.name)
print('Prepared fitted helmet materials and narrow eye masks')
