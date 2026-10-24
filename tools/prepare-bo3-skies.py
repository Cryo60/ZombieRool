"""Read local BO3 skybox XPAK entries; retain HDR gradients before conversion to map panoramas.

XPAK layout reference: https://github.com/Scobalula/Greyhound (DBGameFiles/XPAKCache).
HDR decoder: Microsoft's DirectXTex texconv. Game files are opened read-only.
Run with the bundled Python (Pillow/numpy); lz4 is installed in build/bo3-skyboxes/deps.
"""
from pathlib import Path
import argparse,sys,struct,json,hashlib,subprocess,io,shutil
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'build/bo3-skyboxes';sys.path.insert(0,str(WORK/'deps'))
import lz4.block
import numpy as np
from PIL import Image

def extract(path):
    with path.open('rb') as f:
        header=f.read(120);assert struct.unpack_from('<I',header)[0]==0x4950414b
        fields=struct.unpack_from('<14Q',header,8);f.seek(fields[6]);hashes={}
        for _ in range(fields[5]):
            key,offset,size=struct.unpack('<QQQ',f.read(24));hashes[key]=(offset,size&0xffffffffffffff)
        f.seek(fields[12]);sky=None
        for _ in range(fields[11]):
            key,size=struct.unpack('<QQ',f.read(16));props=f.read(size).decode('utf-8',errors='replace');row=dict(line.split(':',1) for line in props.splitlines() if ':' in line);row={k.strip():v.strip() for k,v in row.items()}
            if row.get('type')=='skybox' and 'default' not in row.get('name','') and key in hashes:sky=(key,row)
        if sky is None:raise ValueError('No map skybox in '+str(path))
        key,row=sky;offset,size=hashes[key];f.seek(fields[3]+offset);payload=f.read(size)
    output=bytearray();pos=0
    while pos<len(payload):
        count,offset,*commands=struct.unpack_from('<32I',payload,pos);pos+=128;assert count<=30
        for cmd in commands[:count]:
            size=cmd&0xffffff;flag=cmd>>24;part=payload[pos:pos+size];pos+=size
            if flag==0:output.extend(part)
            elif flag==3:output.extend(lz4.block.decompress(part,uncompressed_size=int(row['size0'])-len(output)))
            elif flag!=0xcf:raise ValueError('Unsupported XPAK compression '+str(flag))
        pos=(pos+127)&~127
    assert len(output)==int(row['size0'])
    return bytes(output),{'asset':row['name'],'key':hex(key),'compressed_sha256':hashlib.sha256(payload).hexdigest(),'raw_sha256':hashlib.sha256(output).hexdigest()}

def prepare(game,texconv):
    manifest=[]
    for zone,name,width,height,exposure in [('zm_genesis','fractured',8192,4096,1),('zm_factory','winter',6144,3072,64),('zm_temple','jungle',8192,4096,1)]:
        source=game/'zone'/(zone+'.xpak');raw,record=extract(source)
        assert len(raw)==width*height # BC6H: one compressed byte per pixel.
        header=bytearray(148);header[:4]=b'DDS ';struct.pack_into('<7I',header,4,124,0x81007,height,width,len(raw),0,1);struct.pack_into('<II4s',header,76,32,4,b'DX10');struct.pack_into('<I',header,108,0x1000);struct.pack_into('<5I',header,128,95,3,0,1,0)
        dds=WORK/(zone+'.dds');dds.write_bytes(header+raw)
        subprocess.run([str(texconv),'-w','4096','-h','2048','-ft','pfm','-y','-o',str(WORK),str(dds)],check=True,stdout=subprocess.DEVNULL)
        with dds.with_suffix('.pfm').open('rb') as f:
            assert f.readline().strip()==b'PF';w,h=map(int,f.readline().split());scale=float(f.readline());linear=np.frombuffer(f.read(),dtype='<f4' if scale<0 else '>f4').reshape(h,w,3)[::-1].copy()
        linear*=exposure
        # Filmic HDR compression and linear-to-sRGB, before quantization (avoids banding in The Giant).
        color=np.clip((linear*(2.51*linear+.03))/(linear*(2.43*linear+.59)+.14),0,1);color=np.where(color<=.0031308,color*12.92,1.055*np.power(color,1/2.4)-.055)
        image=Image.fromarray(np.uint8(np.clip(color*255+.5,0,255)));relative=Path('assets/zombierool/textures/environment')/(name+'_sky.png');target=ROOT/'src/main/resources'/relative;target.parent.mkdir(parents=True,exist_ok=True);image.save(target)
        target.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
        for base in ['resources','build/resources/main','']:
            dest=ROOT/base/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,dest);shutil.copy2(target.with_suffix('.png.mcmeta'),dest.with_suffix('.png.mcmeta'))
        record.update(source=str(source),output=str(relative),dimensions=[w,h],source_dimensions=[width,height],linear_exposure=exposure,tonemap='filmic+sRGB',output_sha256=hashlib.sha256(target.read_bytes()).hexdigest());manifest.append(record)
        image.resize((1200,600)).save(WORK/(zone+'-preview-final.png'));print(name,image.size,target.stat().st_size,flush=True)
    path=ROOT/'assets-source/26.7/environment/bo3-presets-manifest.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--game',type=Path,default=Path('C:/Program Files (x86)/Steam/steamapps/common/Call of Duty Black Ops III'));p.add_argument('--texconv',type=Path,default=WORK/'texconv.exe');a=p.parse_args();prepare(a.game,a.texconv)
