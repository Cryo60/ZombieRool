"""Anatomy-matched animation, with source wing motion retained where compatible."""
import math,copy
import numpy as np

def mounted_source_death(model,source,mapping):
    """Retarget the actual mounted M2 Death sequence, including rider displacement."""
    from build_stratholme_actors import B,rotation,quaternion
    mapping=dict(mapping)
    for side,footkey in [('left',59),('right',60)]:
        knee=source.parents[source.keys[footkey]]
        mapping[side+'_leg']=source.parents[knee];mapping[side+'_shin']=knee
    bind=source.frame(source.clip([0]),0);index=source.clip([1]);duration=source.animations[index][2]
    scale=32/source.height
    def source_pose(frame,i):
        r=B@frame[i][:3,:3]@B.T;u,_,v=np.linalg.svd(r)
        return u@v,B@(frame[i]@np.r_[source.pivots[i],1])[:3]
    neutral={name:source_pose(bind,i) for name,i in mapping.items()}
    rest={}
    for p in model.parts:
        pr,pp=rest.get(p.get('parent'),(np.eye(3),np.zeros(3)))
        rest[p['name']]=(pr@rotation(p['rotation']),pp+pr@np.array(p['pivot']))
    frames=[]
    for ms in np.linspace(0,duration,min(96,max(2,round(duration/33)))):
        raw=source.frame(index,ms);source_world={name:source_pose(raw,i) for name,i in mapping.items()}
        frame={'time':round(float(ms/50),5)};world={};bottom=-1e9
        for p in model.parts:
            name=p['name'];parent=p.get('parent');pr,pp=world.get(parent,(np.eye(3),np.zeros(3)))
            offset=np.array(p['pivot'],dtype=float)
            if name in mapping:
                sr,sp=source_world[name];nr,npivot=neutral[name]
                wr=sr@nr.T@rest[name][0];r=pr.T@wr
                if parent in mapping:
                    spr,spp=source_world[parent];npr,npp=neutral[parent]
                    offset+=(spr.T@(sp-spp)-npr.T@(npivot-npp))*scale
                elif parent is None:offset+=(sp-npivot)*scale
            else:r=rotation(p['rotation']);wr=pr@r
            wp=pp+pr@offset;world[name]=(wr,wp)
            pose=dict(zip(('qx','qy','qz','qw'),map(float,quaternion(r))))
            pose.update(dict(zip(('x','y','z'),map(float,offset-np.array(p['pivot'])))))
            frame[name]=pose
            for cube in p['cubes']:
                x,y,z,sx,sy,sz=cube['box']
                for a in [x,x+sx]:
                    for b in [y,y+sy]:
                        for c in [z,z+sz]:bottom=max(bottom,(wp+wr@np.array([a,b,c]))[1])
        frame['root']={'y':float(24-bottom)};frames.append(frame)
    return {'duration':duration/50,'loop':False,'frames':frames}

def polish(m):
    if m.kind=='balnazzar':
        m.scale=2.1
        for side,s in [('left',1),('right',-1)]:
            m.by[side+'_leg']['pivot']=(s*3.3,12,0)
            m.by[side+'_leg']['rotation']=(0,0,-s*.05)
            m.by[side+'_shin']['rotation']=(0,0,0)
    if m.kind=='crypt_fiend':
        for side,sign in [('left',1),('right',-1)]:
            for i,z in enumerate([-5,2,9]):
                upper=m.by[side+'_leg'+str(i)];lower=m.by[side+'_leg'+str(i)+'_shin']
                upper['pivot']=(sign*4.5,0,z);upper['rotation']=(0,(i-1)*.5,-sign*.9)
                upper['cubes'][0]['box'][4]=12
                lower['pivot']=(0,12,0);lower['rotation']=(0,0,sign*.25)
                lower['cubes'][0]['box'][4]=15;lower['cubes'][1]['box'][1]=13
        m.box('torso','skin',-4,-11,-5,8,9,7)
        m.box('torso','metal',-4.5,-9,-5.3,9,3,1)
    if m.kind=='baron_rivendare':
        m.by['horse']['pivot']=(0,8,2)
        m.by['horse']['cubes']=[]
        m.box('horse','dark',-4.5,-5,-10,9,7,22)
        m.box('horse','bone',-4,-6,-10,8,3,22)
        for z in [-7,-3,1,5,9]:
            for s in [-1,1]:m.box('horse','bone',s*4.6-.6,-4,z,1.2,6,1.2)
        m.box('horse','hood',-5.2,-6,-1,10.4,7,12)
        m.box('horse','gold',-5.4,-6.2,-1.2,10.8,1,12.4)
        m.box('horse','strap',-3.4,-7,-1,6.8,1.2,8)
        m.by['torso']['parent']='horse';m.by['torso']['pivot']=(0,-19,3)
        for s,side in [(1,'left'),(-1,'right')]:
            m.by[side+'_leg']['pivot']=(s*3.5,11,0)
            m.by[side+'_leg']['rotation']=(.2,0,-s*.25)
        m.by['horse_neck']['pivot']=(0,-4,-9)
        m.by['horse_neck']['rotation']=(.4,0,0)
        m.by['horse_neck']['cubes']=[]
        m.box('horse_neck','bone',-2.7,-13,-2.5,5.4,14,5)
        m.box('horse_neck','dark',-2.8,-12,2.1,5.6,12,1.5)
        m.by['horse_head']['pivot']=(0,-12,-1.5)
        m.by['horse_head']['cubes']=[]
        m.box('horse_head','bone',-2.8,-4,-5,5.6,6,7)
        m.box('horse_head','bone',-2.3,-1,-11,4.6,3.5,8)
        m.box('horse_head','dark',-2.5,.2,-11.3,5,1.4,3)
        m.box('horse_head','metal',-3,-4.5,-4.8,6,2,6)
        for s in [-1,1]:
            m.box('horse_head','bone',s*2-.6,-9,-1,1.2,5,2)
            m.box('horse_head','eye',s*2.9-.25,-2.5,-4,.5,1.2,1.6)
            m.box('horse_neck','strap',s*2.9-.2,-8,-2,.4,10,.6)
        m.extra('horse','horse_tail','dark',[-1,0,-1,2,12,2],p=(0,-3,12),rot=(.5,0,0))
        for side in ['left','right']:
            for end in ['front','back']:
                bone=side+'_'+end;m.by[bone]['cubes'][0]['box'][4]=8
                shin=m.by[bone+'_shin'];shin['pivot']=(0,8,0)
                shin['cubes'][0]['box'][4]=8;shin['cubes'][1]['box'][1]=6.5
    ordered=[];seen=set()
    def visit(part):
        if part['name'] in seen:return
        if part.get('parent'):visit(m.by[part['parent']])
        ordered.append(part);seen.add(part['name'])
    for part in m.parts:visit(part)
    m.parts=ordered
    return m

def clips_for(m,original):
    from build_stratholme_actors import rotation,quaternion
    def pose(angles):return dict(zip(('qx','qy','qz','qw'),map(float,quaternion(rotation(angles)))))
    base={p['name']:list(p['rotation']) for p in m.parts}
    clips={}
    for clip,duration,loop in [('idle',60,True),('walk',36,True),('run',24,True),('attack',24,False),('cast',40,False),('death',64 if m.kind=='baron_rivendare' else 54,False)]:
        frames=[]
        for t in np.linspace(0,duration,25):
            phase=t/duration*math.tau;progress=t/duration;angles=copy.deepcopy(base)
            def add(name,x=0,y=0,z=0):
                if name in angles:angles[name]=[angles[name][0]+x,angles[name][1]+y,angles[name][2]+z]
            if clip in ('walk','run'):
                amp=.42 if clip=='walk' else .65
                if m.kind=='baron_rivendare':
                    for side,s in [('left',1),('right',-1)]:
                        for end,k in [('front',0),('back',math.pi)]:
                            p=phase+(0 if s==1 else math.pi)+k
                            add(side+'_'+end,x=math.sin(p)*amp)
                            add(side+'_'+end+'_shin',x=max(0,-math.sin(p))*.55)
                    add('horse_neck',x=math.sin(phase)*.035)
                elif m.kind=='crypt_fiend':
                    for side,s in [('left',1),('right',-1)]:
                        for i in range(3):
                            p=phase+i*math.pi+(0 if s==1 else math.pi)
                            add(side+'_leg'+str(i),x=math.sin(p)*.22,y=math.sin(p)*.15)
                            add(side+'_leg'+str(i)+'_shin',x=-math.sin(p)*.13)
                else:
                    for side,s in [('left',1),('right',-1)]:
                        p=phase+(0 if s==1 else math.pi)
                        add(side+'_leg',x=math.sin(p)*amp)
                        add(side+'_shin',x=-max(0,math.sin(p))*.55)
                        add(side+'_arm',x=-math.sin(p)*amp*.65)
                        add(side+'_forearm',x=-.12)
            if clip=='idle':add('torso',x=math.sin(phase)*.012)
            if clip in ('attack','cast'):
                swing=math.sin(progress*math.pi)
                add('right_arm',x=-swing*(1.7 if clip=='attack' else 1.3))
                add('right_forearm',x=-swing*.3)
                if clip=='cast':add('left_arm',x=-swing*1.3)
            root_angles=(0,0,0)
            if clip=='death':
                def ease(a,b):
                    v=max(0,min(1,(progress-a)/(b-a)));return v*v*(3-2*v)
                knees=ease(.05,.4);fall=ease(.28,.8);settle=ease(.8,1)
                if m.kind=='crypt_fiend':
                    # Six legs buckle outwards, thorax lowers between them; head droops.
                    for side,sign in [('left',1),('right',-1)]:
                        for i in range(3):
                            add(side+'_leg'+str(i),z=-sign*.6*knees,x=(i-1)*.18*fall)
                            add(side+'_leg'+str(i)+'_shin',z=sign*.9*knees)
                    add('head',x=.6*fall);add('torso',x=.18*fall)
                    for side in ['left','right']:add(side+'_arm',x=.4*fall);add(side+'_forearm',x=.5*fall)
                elif m.kind=='baron_rivendare':
                    # The horse buckles before rolling onto its shoulder. Rider stays on the saddle.
                    for side in ['left','right']:
                        for end in ['front','back']:
                            add(side+'_'+end,x=(-1 if end=='front' else 1)*knees*.8)
                            add(side+'_'+end+'_shin',x=knees*1.15)
                    add('horse_neck',x=.65*knees);add('horse_head',x=.35*knees)
                    add('torso',x=.5*fall,z=.15*fall);add('head',x=.6*fall)
                    add('right_arm',x=.3*fall);add('left_arm',x=.4*fall)
                    root_angles=(0,0,1.35*fall+.06*settle)
                else:
                    # Dreadlord drops to his knees, folds his wings, then collapses forward.
                    for side,sign in [('left',1),('right',-1)]:
                        add(side+'_leg',x=-.75*knees);add(side+'_shin',x=1.35*knees)
                        add(side+'_arm',x=-.7*knees+.9*fall,z=sign*.18*fall)
                        add(side+'_forearm',x=-.65*knees+.4*fall)
                        add(side+'_wing',y=sign*.9*fall,z=-sign*.2*fall)
                        add(side+'_wing_tip',y=sign*1.05*fall)
                    add('torso',x=.2*knees+.2*fall);add('head',x=.5*fall)
                    root_angles=(1.35*fall,0,.12*settle)
            frame={'time':round(float(t),4),**{name:pose(a) for name,a in angles.items()}}
            # Only compatible wing rotations use the original local WoW track.
            if m.kind=='balnazzar' and clip!='death':
                source=original[clip]['frames'];k=min(len(source)-1,round(progress*(len(source)-1)))
                for name in ['left_wing','right_wing','left_wing_tip','right_wing_tip']:
                    if name in source[k]:frame[name]=source[k][name]
            # Rotate a corpse about the feet and translate it onto the floor.
            root_rotation=rotation(root_angles)
            root_p=np.array([0.,24.,0.]);worlds={};bottom=-1e9
            for part in m.parts:
                name=part['name'];parent=part.get('parent')
                pr,pp=worlds.get(parent,(root_rotation,root_p-root_rotation@root_p))
                r=rotation(angles[name]);wr,wp=pr@r,pp+pr@np.array(part['pivot']);worlds[name]=(wr,wp)
                if clip!='death' and not any(k in name for k in ['shin','leg']):continue
                for cube in part['cubes']:
                    x,y,z,sx,sy,sz=cube['box']
                    for a in [x,x+sx]:
                        for b in [y,y+sy]:
                            for c in [z,z+sz]:bottom=max(bottom,(wp+wr@np.array([a,b,c]))[1])
            frame['root']={**pose(root_angles),'y':round(24-bottom,6)}
            frames.append(frame)
        clips[clip]={'duration':duration,'loop':loop,'frames':frames}
    return clips
