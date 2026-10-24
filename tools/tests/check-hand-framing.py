"""Check actual rendered arm footprints against the same scene without an action."""
from pathlib import Path
import sys,json
ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'build/audio-python'),str(ROOT/'build/video-python')]
import numpy as np
from PIL import Image
folder=Path(sys.argv[1])
def footprint(active,baseline):
 a=np.asarray(Image.open(folder/active).convert('RGB'),dtype=np.int16);b=np.asarray(Image.open(folder/baseline).convert('RGB'),dtype=np.int16)
 height,width=a.shape[:2];mask=np.max(np.abs(a-b),axis=2)>55
 mask[:height//2]=False;mask[int(height*.87):]=False
 mask[int(height*.72):,:width//2]=False # Chat/HUD are independent of hand geometry.
 y,x=np.nonzero(mask);assert len(x)>1500,(active,'No visible hand animation')
 area=len(x)/(height*width);assert area<.27,(active,'Hands obscure too much of the view',area)
 return {'pixels':len(x),'area':round(area,4),'x':float(x.mean()),'y':float(y.mean())}
front=footprint('0-grenade_cook-10.png','hands-baseline-front.png')
side=footprint('grenade-looking-sideways.png','hands-baseline-side.png')
assert abs(front['x']-side['x'])<40,(front,side,'Turning the head moved the hands sideways')
assert abs(front['y']-side['y'])<40,(front,side,'Turning the head moved the hands vertically')
assert .7<side['pixels']/front['pixels']<1.4,(front,side,'Turning the head changed hand coverage')
result={'front':front,'side':side,'pass':True};(folder/'hand-framing.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
