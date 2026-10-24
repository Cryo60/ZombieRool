"""Check the visible cuboid geometry, rather than only animation anchor distances."""
import json, math
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[2]/'src/main/resources/assets/zombierool'
JOINTS=['torso','head','left_arm','right_arm','left_forearm','right_forearm','left_leg','right_leg','left_shin','right_shin']

def rotation(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                     [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                     [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])

def geometry(kind,clip,index):
    model=json.loads((ROOT/f'models/entities/blocky/{kind}.json').read_text())
    animation=json.loads((ROOT/f'animations/blocky/{kind}.json').read_text())
    values=animation['clips'][clip]['frames'][index];hip=np.array(animation['hip']);world={};boxes={}
    rotations={n:rotation(values[i*4:i*4+4]) for i,n in enumerate(JOINTS)}
    for part in model['parts']:
        name=part['name'];position=np.array(part['pivot'],float);r=rotations.get(name,np.eye(3))
        if name=='torso':position=hip+values[40:43]+r@(position-hip)+values[47:50]
        if name in ['left_leg','right_leg']:position=hip+values[40:43]+rotation(values[43:47])@(position-hip)+values[50:53]
        if part.get('parent'):
            parent_r,parent_p=world[part['parent']];position=parent_p+parent_r@position;r=parent_r@r
        world[name]=(r,position);points=[]
        for cube in part['cubes']:
            x,y,z,w,h,d=cube['box']
            points.extend(position+r@np.array([xx,yy,zz]) for xx in [x,x+w] for yy in [y,y+h] for zz in [z,z+d])
        if points:boxes[name]=np.array(points)
    return boxes

if __name__=='__main__':
    checks=0
    for kind in ['scourge_ghoul','scourge_skeleton']:
        boxes=geometry(kind,'death',-1);lower=np.concatenate([boxes[n] for n in ['left_leg','right_leg','left_shin','right_shin']])
        if kind=='scourge_ghoul':
            final=json.loads((ROOT/f'animations/blocky/{kind}.json').read_text())['clips']['death']['frames'][-1]
            axis=rotation(final[24:28])[:,1].copy();axis[1]=0;axis/=np.linalg.norm(axis)
            core=np.concatenate([boxes['torso'],boxes['head']])
            assert (lower@axis).min()-(core@axis).max()>1.49,(kind,'legs hidden behind upper body')
        else:assert lower[:,2].min()-boxes['torso'][:,2].max()>1.49,(kind,'waist cubes overlap')
        assert abs(lower[:,1].max()-24)<.001,(kind,'lower half floats')
        upper=np.concatenate([v for n,v in boxes.items() if n not in ['left_leg','right_leg','left_shin','right_shin']])
        assert abs(boxes['torso'][:,1].max()-24)<.001,(kind,'torso floats')
        motion=json.loads((ROOT/f'animations/blocky/{kind}.json').read_text())['clips']['death']['frames'][-1]
        assert np.linalg.norm(np.array(motion)[[40,42]]+np.array(motion)[[47,49]])<=6.001,(kind,'torso travels too far')
        checks+=3
    for clip in ['idle','walk','run']:
        animation=json.loads((ROOT/'animations/blocky/scourge_ghoul.json').read_text())
        for i in range(len(animation['clips'][clip]['frames'])):
            boxes=geometry('scourge_ghoul',clip,i);feet=[boxes[n][:,1].max() for n in ['left_shin','right_shin']]
            assert max(feet)>23.9 and min(feet)>(20 if clip=="run" else 21.5),(clip,i,feet)
        checks+=1
    boxes=geometry('scourge_zombie','death',-1)
    assert abs(boxes['torso'][:,1].max()-24)<.001,'Zombie corpse body floats'
    checks+=1
    print(f'Visible enemy geometry PASS: {checks} checks')
