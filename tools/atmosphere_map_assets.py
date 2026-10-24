"""Install the laboratory's map-local resources using the existing dynamic resource layout."""
from pathlib import Path
import shutil,gzip,struct,time
ROOT=Path(__file__).resolve().parents[1]
def install_resources(world):
    root=Path(world)/'zombierool';sky=root/'assets/skyboxes';sky.mkdir(parents=True,exist_ok=True);audio=root/'audio/custom/ashfall';audio.mkdir(parents=True,exist_ok=True)
    assets=ROOT/'src/main/resources/assets/zombierool'
    for source,name in [('ashfall_sky.png','ashfall.png')]:shutil.copy2(assets/'textures/environment'/source,sky/name)
    for event,name in [('ambient_ash_wind','wind'),('ambient_cloud_rumble','cloud'),('ambient_distant_thunder','thunder'),('ambient_distant_alarm','alarm'),('ambient_wind_gust','gust')]:
        for file in (assets/'sounds/environment').glob(event+'_*.ogg'):shutil.copy2(file,audio/(name+'_'+file.stem.rsplit('_',1)[1]+'.ogg'))
    (root/'ATMOSPHERE.txt').write_text("SKY AND AMBIENCE / CIEL ET AMBIANCES\n\nPNG : zombierool/assets/skyboxes/\nOGG : zombierool/audio/custom/\n\nMap config > Environnement > Skybox, image et ambiances > Presets : choisissez pour prévisualiser, Terminé pour enregistrer.\nAmbiances > Ajouter un son : choisissez, réglez le volume et la portée, Placer ici, puis Terminé.\nLes deux délais sont en secondes. Lecture en continu = boucle.\nVos fichiers apparaissent dans les listes à la prochaine entrée ou au rechargement des ressources de carte. Aucun resources.zip n’est nécessaire.\n",encoding='utf-8')
class NBT:
    def __init__(self,b):self.b=b;self.i=0
    def take(self,n):v=self.b[self.i:self.i+n];self.i+=n;return v
    def num(self,f):return struct.unpack('>'+f,self.take(struct.calcsize('>'+f)))[0]
    def string(self):return self.take(self.num('H')).decode('utf-8')
    def payload(self,t):
        if t in range(1,7):return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==8:return self.string()
        if t==9:
            ty,n=self.num('B'),self.num('i');return ty,[self.payload(ty) for _ in range(n)]
        if t==10:
            out={}
            while (ty:=self.num('B')):
                name=self.string();out[name]=(ty,self.payload(ty))
            return out
        if t in (7,11,12):
            count=self.num('i');return count,self.take(count*{7:1,11:4,12:8}[t])
        raise ValueError(t)
    def root(self):ty=self.num('B');name=self.string();return ty,name,self.payload(ty)
def string(s):b=s.encode('utf-8');return struct.pack('>H',len(b))+b
def payload(t,value):
    if t in range(1,7):return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],value)
    if t==8:return string(value)
    if t==9:ty,rows=value;return bytes([ty])+struct.pack('>i',len(rows))+b''.join(payload(ty,row) for row in rows)
    if t==10:return b''.join(bytes([ty])+string(key)+payload(ty,v) for key,(ty,v) in value.items())+b'\0'
    if t in (7,11,12):return struct.pack('>i',value[0])+value[1]
    raise ValueError(t)
def update_demo(world):
    world=Path(world);assert world.is_dir();install_resources(world)
    pack=world/'resources.zip'
    if pack.exists():pack.unlink()
    path=world/'data/zombierool_map_atmosphere.dat';original=path.read_bytes();ty,name,data=NBT(gzip.decompress(original)).root();settings=data['data'][1]['settings'][1]
    for key in ('cloudEnabled','cloudTexture','cloudYaw','cloudSize','cloudElevation'):settings.pop(key,None)
    (world/'zombierool/assets/skyboxes/nuclear_cloud.png').unlink(missing_ok=True)
    settings.update({'presetId':(8,'nuketown_zombies'),'skyTexture':(8,'zombierool:world_sky/ashfall')})
    for row in settings['sources'][1][1]:
        key=row['id'][1]
        if key in ('wind','cloud','thunder','alarm'):row['sound']=(8,'zombierool:custom/ashfall/'+key+'_0')
    updated=gzip.compress(bytes([ty])+string(name)+payload(ty,data));NBT(gzip.decompress(updated)).root()
    backup=ROOT/'build/map-atmosphere/demo'/('atmosphere-before-'+str(int(time.time()))+'.dat');backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(original);path.write_bytes(updated)
    (world/'LIRE-MOI.txt').write_text((world/'zombierool/ATMOSPHERE.txt').read_text(encoding='utf-8'),encoding='utf-8')
    print('Updated demo resources and atmosphere:',world)
if __name__=='__main__':update_demo(Path((ROOT/'build/map-atmosphere/demo/installed-map.txt').read_text(encoding='utf-8')))
