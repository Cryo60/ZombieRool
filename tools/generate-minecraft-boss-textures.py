"""Original pixel atlases for six cuboid boss designs, inspired by desktop photos."""
from pathlib import Path
import sys, shutil, random
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'build/video-python'))
from PIL import Image, ImageDraw

def save(im, name):
    target=root/'src/main/resources/assets/zombierool/textures/entities'/name
    im.save(target)
    for folder in ['resources/assets/zombierool/textures/entities','assets/zombierool/textures/entities']:
        shutil.copy2(target,root/folder/name)

for kind in ['panzer','brutus']:
    for variant in range(3):
        rng=random.Random(267+variant)
        metal=([(77,91,99),(94,106,99),(61,69,78)] if kind=='panzer' else [(119,99,73),(104,89,67),(87,78,64)])[variant]
        im=Image.new('RGBA',(256,256),(31,31,29,255));draw=ImageDraw.Draw(im)
        for tile in range(32):
            x,y=tile%4*64,tile//4*32
            color=metal
            skin=tile==1 or kind=='brutus' and tile in (3,23,26)
            if skin:color=(131,130,99) if kind=='panzer' else (146,136,113)
            if tile in (6,8,12,15,17,19,24,25,31):color=(41,43,42)
            if tile==16:color=(112,104,73)
            if tile==21:color=(179,153,84)
            if tile==27:color=(244,190,57) if kind=='panzer' else (240,37,15)
            if tile==28:color=(255,216,117)
            if tile in (11,29):color=(66,142,170)
            if tile==30:color=(105,28,22)
            draw.rectangle((x,y,x+63,y+31),fill=color+(255,))
            if tile in (27,28):
                for row in range(32):
                    factor=.86+.14*(1-abs(row-16)/16)
                    draw.line((x,y+row,x+63,y+row),fill=tuple(int(c*factor) for c in color)+(255,))
                continue
            for _ in range(65):
                px,py=x+rng.randrange(60),y+rng.randrange(30)
                shift=rng.choice([-20,-12,8,16])
                tint=tuple(max(0,min(255,c+shift)) for c in color)+(255,)
                draw.line((px,py,min(x+63,px+rng.randrange(1,4)),py),fill=tint)
            if skin:
                if tile==1:
                    draw.rectangle((x+6,y+6,x+11,y+12),fill=(142,135,110,255))
                    draw.line((x+6,y+8,x+7,y+11),fill=(83,66,52,255))
                    draw.line((x+10,y+7,x+11,y+9),fill=(91,76,56,255))
                    draw.line((x+7,y+11,x+10,y+11),fill=(46,41,35,255))
                    for px in (7,9):draw.point((x+px,y+12),fill=(188,180,151,255))
                if tile in (3,23):
                    for px,py in [(7,8),(11,11),(5,6),(14,4)]:draw.line((x+px,y+py,x+px+2,y+py+3),fill=(105,87,72,255))
                if tile==26:
                    draw.line((x+1,y+2,x+5,y+2),fill=(46,41,34,255))
                    for px in (1,3,5):draw.point((x+px,y+2),fill=(199,191,164,255))
                continue
            if tile in (11,29):
                for row in range(0,30,6):draw.line((x,y+row,x+20,y+row+8),fill=(104,185,209,255))
                continue
            if tile==30:continue
            for left,top,w,h in [(0,0,14,7),(7,7,13,12),(22,7,12,12),(0,21,26,9)]:
                draw.rectangle((x+left,y+top,x+left+w,y+top+h),outline=tuple(max(0,c-33) for c in color)+(255,))
                draw.line((x+left+1,y+top+1,x+left+w-1,y+top+1),fill=tuple(min(255,c+29) for c in color)+(255,))
            if tile in (9,10,13,20):
                for px,py in [(1,1),(7,7),(12,7),(7,12),(18,8),(25,10)]:draw.rectangle((x+px,y+py,x+px+1,y+py+1),fill=(167,157,132,255))
                for _ in range(9):
                    px,py=x+rng.randrange(30),y+rng.randrange(24)
                    draw.line((px,py,px+2,py),fill=(101,75,46,255))
            if tile in (13,12):
                for row in (4,6,8,10):draw.line((x+3,y+row,x+10,y+row),fill=(22,27,29,255))
            if tile==21:
                draw.rectangle((x+1,y+1,x+4,y+4),outline=(219,196,124,255))
                draw.line((x+1,y+2,x+3,y+2),fill=(52,44,32,255))
            if tile==31:
                for row in range(0,31,3):draw.line((x,y+row,x+55,y+row),fill=(167,154,129,255))
        name=f'{kind}_minecraft'+('' if variant==0 else f'_{variant+1}')+'.png'
        save(im,name)

im=Image.new('RGBA',(64,64),(213,210,185,255));draw=ImageDraw.Draw(im)
for y in range(0,64,8):draw.line((0,y,63,y),fill=(197,193,169,255))
draw.rectangle((7,23,8,24),fill=(44,45,39,255));draw.rectangle((10,23,11,24),fill=(44,45,39,255));draw.line((7,26,10,26),fill=(57,56,47,255))
save(im,'bone_gibs.png')
