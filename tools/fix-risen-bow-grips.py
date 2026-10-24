"""Align archer palms and bow grips while preserving the torso and leg motion."""
from pathlib import Path
import json,math
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'examples/stratholme/zombierool/gameplay/entities'

def rotation(n):
    if 'qw' in n:
        x,y,z,w=(n[k] for k in ('qx','qy','qz','qw'))
        return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
                         [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
                         [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
    x,y,z=(math.radians(n.get(k,0)) for k in ('rx','ry','rz'))
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
                     [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])

def angles(matrix):
    return {k:round(math.degrees(v),6) for k,v in zip(('rx','ry','rz'),
        (math.atan2(matrix[2,1],matrix[2,2]),math.asin(np.clip(-matrix[2,0],-1,1)),math.atan2(matrix[1,0],matrix[0,0])))}

def hand_rotation(profile,frame):
    chain=[];name='left_hand'
    while name in profile['bones']:
        chain.insert(0,name);name=profile['bones'][name]['parent']
    result=np.eye(3)
    for name in chain:result=result@rotation(frame.get(name,profile['bones'][name]))
    return result

def quaternion(matrix):
    e=angles(matrix);x,y,z=(math.radians(e[k])*.5 for k in ('rx','ry','rz'))
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return dict(zip(('qx','qy','qz','qw'),(sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)))

def world(profile,frame,name):
    bone=profile['bones'][name];parent=bone['parent'];pivot=np.array([bone['pivot'+a] for a in 'XYZ'])
    if parent not in profile['bones']:return rotation(frame.get(name,bone)),pivot
    pr,pp=world(profile,frame,parent);pb=profile['bones'][parent]
    offset=pivot-np.array([pb['pivot'+a] for a in 'XYZ'])
    return pr@rotation(frame.get(name,bone)),pp+pr@offset

def along(vector):
    y=vector/np.linalg.norm(vector);x=np.array([1.,0,0]);x-=y*np.dot(x,y)
    if np.linalg.norm(x)<.001:x=np.cross(y,[0,0,1])
    x/=np.linalg.norm(x)
    return np.column_stack((x,y,np.cross(x,y)))

def aim_arms(profile,frame,clip):
    targets={}
    duration=profile['animations'][clip]['duration'];t=frame['time']/max(1,duration)
    # Keep the left palm extended. The drawing hand moves back towards the chest.
    pull=1 if clip=='idle' else min(1,t*1.8) if clip=='cast' else 1
    for side,sign in (('left',1),('right',-1)):
        arm=side+'_arm';forearm=side+'_forearm';hand=side+'_hand'
        _,shoulder=world(profile,frame,arm)
        target=shoulder+np.array([0,2,-9.3] if side=='left' else [3.5,2,-7+4.5*pull])
        delta=target-shoulder;distance=np.linalg.norm(delta);direction=delta/distance
        upper=profile['bones'][forearm]['pivotY']-profile['bones'][arm]['pivotY']
        lower=profile['bones'][hand]['pivotY']-profile['bones'][forearm]['pivotY']
        distance=min(distance,upper+lower-.05);target=shoulder+direction*distance
        reach=(upper*upper-lower*lower+distance*distance)/(2*distance)
        outward=np.array([float(sign),0,0]);outward-=direction*np.dot(outward,direction);outward/=np.linalg.norm(outward)
        elbow=shoulder+direction*reach+outward*math.sqrt(max(0,upper*upper-reach*reach))
        parent=profile['bones'][arm]['parent'];pr,_=world(profile,frame,parent)
        upper_world=along(elbow-shoulder);lower_world=along(target-elbow)
        frame[arm]=quaternion(pr.T@upper_world);frame[forearm]=quaternion(upper_world.T@lower_world);frame[hand]={'qx':0,'qy':0,'qz':0,'qw':1}
        targets[side]=target
    direction=targets['right']-targets['left'];direction[1]=0;direction/=np.linalg.norm(direction)
    return np.column_stack((direction,np.array([0,1.,0]),np.cross(direction,[0,1.,0])))

def fix(profile):
    # Bow string is local +X; actor's rear is model +Z. Its limbs follow +Y.
    desired=rotation({'ry':-90})
    for clip in ('idle','cast','attack'):
        for frame in profile['animations'][clip]['frames']:
            desired=aim_arms(profile,frame,clip)
            frame['left_weapon']=quaternion(hand_rotation(profile,frame).T@desired)
            actual=hand_rotation(profile,frame)@rotation(frame['left_weapon'])
            assert actual[2,0]>0 and actual[1,1]>.99999
    reference=profile['animations']['cast']['frames'][0]
    profile['bones']['left_weapon'].update(angles(rotation(reference['left_weapon'])))
    return profile

def main():
    for sex in ('','_female'):
        path=FOLDER/('scarlet_zombie_archer'+sex+'.json')
        if not path.exists():continue
        profile=fix(json.loads(path.read_text(encoding='utf-8')))
        if not sex:
            for appearance in profile.get('appearances',[]):fix(appearance)
        path.write_text(json.dumps(profile,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
        print('Bow grip corrected:',profile['id'])

if __name__=='__main__':main()
