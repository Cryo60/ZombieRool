"""Author compact block-style decorative cars; no source-game extraction or scratch files."""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/zombierool'
DEST = ASSETS / 'scenery/vehicles'
PITCH = .1
MODELS = {
    'sedan': (2.0, 4.6, 1.65, (102, 39, 32)),
    'compact': (1.8, 3.6, 1.65, (58, 108, 111)),
    'muscle': (2.2, 5.0, 1.48, (173, 80, 27)),
    'sport': (2.0, 4.4, 1.32, (141, 33, 34)),
    'estate': (2.0, 4.9, 1.72, (85, 106, 66)),
    'suv': (2.3, 4.8, 2.05, (57, 65, 54)),
    'taxi': (2.1, 4.8, 1.7, (188, 143, 35)),
    'police': (2.1, 4.8, 1.7, (39, 46, 55)),
}

def surfaces(cells):
    faces = []
    for axis in range(3):
        u, v = (axis+1)%3, (axis+2)%3
        for side in (-1, 1):
            planes = {}
            for xyz, color in cells.items():
                neighbor = list(xyz); neighbor[axis] += side
                if tuple(neighbor) in cells: continue
                planes.setdefault(xyz[axis]+(side>0), {})[xyz[u], xyz[v]] = color
            for level, plane in planes.items():
                while plane:
                    (x,y), color = next(iter(plane.items())); w = h = 1
                    while plane.get((x+w,y)) == color: w += 1
                    while all(plane.get((xx,y+h)) == color for xx in range(x,x+w)): h += 1
                    for xx in range(x,x+w):
                        for yy in range(y,y+h): del plane[xx,yy]
                    corners = []
                    for xx,yy in ((x,y),(x+w,y),(x+w,y+h),(x,y+h)):
                        p = [0,0,0]; p[axis]=level*PITCH; p[u]=xx*PITCH; p[v]=yy*PITCH
                        corners.append([round(k,3) for k in p])
                    if side<0: corners.reverse()
                    faces.append([axis,side,(color[0]<<16)|(color[1]<<8)|color[2],*sum(corners,[])])
    return faces

def build(model):
    width,length,roof,paint = MODELS[model]; cells={}; w=width/2; l=length/2
    metal=(103,108,106); dark=(24,27,29); rubber=(23,24,25); glass=(44,68,77)
    def box(x0,y0,z0,x1,y1,z1,color):
        for x in range(round(x0/PITCH),round(x1/PITCH)):
            for y in range(round(y0/PITCH),round(y1/PITCH)):
                for z in range(round(z0/PITCH),round(z1/PITCH)): cells[x,y,z]=color
    box(-w+.15,.28,-l+.25,w-.15,.5,l-.2,dark)
    box(-w,.5,-l+.1,w,.9,l-.1,paint)
    box(-w+.1,.9,-l+.25,w-.1,1.0,-.55,paint) # hood
    rear= l-.2 if model in ('compact','estate','suv') else l-.85
    front=-.6 if model!='sport' else -.45
    # Stepped sloping windscreens, inset glazing and pillars around an actual cabin volume.
    box(-w+.15,.95,front,w-.15,roof-.15,rear,dark)
    for y in range(10,round((roof-.15)/PITCH)):
        inset=max(0,(y-10)*.07)
        box(-w+.15+inset,y*.1,front+inset,w-.15-inset,(y+1)*.1,rear-inset,glass)
    box(-w+.28,roof-.15,front+.2,w-.28,roof,rear-.15,paint)
    for side in (-1,1):
        x0=-w if side<0 else w-.1
        for z in (front, .4 if model not in ('sport','muscle') else .65, rear-.1):
            box(x0+.05,.9,z,x0+.15,roof-.1,z+.1,paint)
        # Door seams, handles, sill, mirrors and wheel arches.
        for z in (.25,rear-.5):
            box(x0,.55,z,x0+.1,.95,z+.1,dark)
            box(x0-.02,.83,z+.1,x0+.12,.93,z+.32,metal)
        box(x0,.45,-l+.5,x0+.1,.55,l-.5,metal)
        box(x0-.14,1.05,front+.15,x0+.25,1.2,front+.45,paint)
        for axle in (-l+.85,l-.8):
            # Voxel tires: tread ring, recessed sidewall, metal rim and dark central hub.
            radius=.44 if model=='suv' else .38
            for y in range(0,10):
                for z in range(round((axle-radius)/.1)-1,round((axle+radius)/.1)+1):
                    dist=math.hypot((y+.5)*.1-.44,(z+.5)*.1-axle)
                    if dist>radius: continue
                    color=rubber if dist>radius*.62 else metal if dist>radius*.25 else dark
                    box(x0-.1,y*.1,z*.1,x0+.2,(y+1)*.1,(z+1)*.1,color)
    # Front radiator slats, headlamps, rear lamps, bumpers and number plates.
    box(-w+.15,.5,-l-.05,w-.15,.65,-l+.15,metal)
    box(-w+.15,.5,l-.15,w-.15,.65,l+.05,metal)
    box(-.45,.65,-l-.02,.45,.88,-l+.1,dark)
    for z in (-l-.08,l-.02): box(-.25,.48,z,.25,.63,z+.1,(166,166,144))
    for side in (-1,1):
        x=side*(w-.35)
        box(x-.2,.72,-l-.05,x+.2,.92,-l+.08,(222,215,170))
        box(x-.18,.68,l-.08,x+.18,.9,l+.02,(126,25,20))
    if model in ('muscle','sport'):
        for x in (-.35,.15): box(x,1.0,-l+.3,x+.2,1.1,front,(29,30,30))
        box(-.75,.93,l-.35,.75,1.03,l-.2,dark)
        if model=='sport': box(-.85,1.12,l-.4,.85,1.22,l-.1,paint)
    if model=='suv':
        for side in (-1,1):box(side*.68-.05,roof,-.2,side*.68+.05,roof+.15,rear-.25,dark)
        box(-.5,.7,l-.05,.5,1.45,l+.2,rubber)
        box(-.25,.9,l+.2,.25,1.25,l+.3,metal)
    if model=='taxi':
        box(-.35,roof,-.1,.35,roof+.22,.35,(207,180,83))
        for side in (-1,1):
            for z in range(-12,14,2):box(-w if side<0 else w-.1,.75,z*.1,-w+.1 if side<0 else w,.85,(z+1)*.1,dark)
    if model=='police':
        for side in (-1,1):box(-w if side<0 else w-.1,.6,-.4,-w+.1 if side<0 else w,.9,1.1,(174,180,174))
        box(-.65,roof,-.1,.65,roof+.1,.3,dark)
        box(-.6,roof+.1,-.1,-.1,roof+.3,.3,(153,31,29))
        box(.1,roof+.1,-.1,.6,roof+.3,.3,(32,58,149))
    # Sparse wear preserves large merged panels; no per-instance material or particle allocation.
    for key,c in list(cells.items()):
        if c==paint and hash(key)%43==0:cells[key]=tuple(max(15,v-15) for v in c)
    damaged={}
    for (x,y,z),c in cells.items():
        if z*.1 < -l+.65 and x>0 and y>5: continue
        if c==glass and (x*7+z*3+y)%7<3:continue
        # Buckle part of the hood; reveal a dark engine instead of a flat burnt recolor.
        if z*.1 < front and y>=9 and x<0: y-=2
        damaged[x,y,z]=tuple(max(12,int(v*(.42 if z*.1<front else .66))) for v in c)
    burnt={}
    for key,c in damaged.items():
        if c==tuple(max(12,int(v*.66)) for v in glass):continue
        # Ash, scorched steel and exposed rusty patches across the entire wreck.
        tone=((key[0]//4)*13+(key[1]//3)*7+(key[2]//5)*3)%17
        burnt[key]=(41,31,23) if tone<2 else (22+tone//3,23+tone//3,23+tone//3)
    return cells,damaged,burnt

def icon(faces,path):
    verts=np.array([f[3:] for f in faces]).reshape(-1,3)
    a=.65;rot=np.array([[math.cos(a),0,-math.sin(a)],[0,1,0],[math.sin(a),0,math.cos(a)]])
    v=verts@rot.T;p=np.column_stack((v[:,0],-v[:,1]+v[:,2]*.4));lo=p.min(axis=0);hi=p.max(axis=0)
    image=Image.new('RGBA',(128,128));draw=ImageDraw.Draw(image)
    for j in sorted(range(len(faces)),key=lambda j:np.mean(v[j*4:j*4+4,2])):
        f=faces[j];normal=np.zeros(3);normal[f[0]]=f[1];normal=rot@normal
        if normal[2]>.4:continue
        color=np.array([(f[2]>>16)&255,(f[2]>>8)&255,f[2]&255]);color=np.clip(color*(.7+.3*max(0,normal[1])+.14*max(0,-normal[0])),0,255).astype(int)
        points=(p[j*4:j*4+4]-(lo+hi)/2)*(116/max(hi-lo))+64
        draw.polygon([tuple(point) for point in points],fill=tuple(color)+(255,))
    image.save(path)

if __name__ == "__main__":
    for model in MODELS:
        normal,broken,burnt=build(model)
        for state,cells in (('normal',normal),('broken',broken),('burnt',burnt)):
            faces=surfaces(cells);assert len(faces)<20000
            (DEST/f'car_{model}_{state}.json').write_text(json.dumps({'pitch':PITCH,'faces':faces},separators=(',',':')),encoding='utf8')
            if state=='normal':
                block='decor_car' if model=='sedan' else 'decor_car_'+model
                icon(faces,ASSETS/f'textures/item/{block}.png')
                for rel,data in [(f'models/item/{block}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'zombierool:item/{block}'}}),(f'blockstates/{block}.json',{'variants':{'':{'model':f'zombierool:block/{block}'}}}),(f'models/block/{block}.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/gray_concrete'},'elements':[]})]:
                    (ASSETS/rel).write_text(json.dumps(data),encoding='utf8')
        print(model,'normal/burnt surfaces',len(surfaces(normal)),len(faces))
