"""Small read-only CASC reader for named enemy assets, not a full game unpacker.
Format references: Kruithne/wow.export src/js/casc/{casc-source,casc-source-local}.js
(MIT, Kruithne and Marlamin), and Blizzard's BLTE layout. No game files are changed.
Encrypted/missing chunks fail explicitly; nothing is silently zero-filled or downloaded.
"""
from pathlib import Path
import struct,zlib,hashlib,bisect

def blte(raw):
    assert raw[:4]==b'BLTE'
    header=struct.unpack_from('>I',raw,4)[0]
    def block(data):
        if data[:1]==b'N':return data[1:]
        if data[:1]==b'Z':return zlib.decompress(data[1:])
        raise ValueError('Unsupported/encrypted BLTE block '+repr(data[:1]))
    if not header:return block(raw[8:])
    count=int.from_bytes(raw[9:12],'big');pos=header;parts=[]
    for i in range(count):
        compressed,size=struct.unpack_from('>II',raw,12+i*24);data=raw[pos:pos+compressed];pos+=compressed
        assert hashlib.md5(data).digest()==raw[20+i*24:36+i*24]
        data=block(data);assert len(data)==size;parts.append(data)
    return b''.join(parts)

class LocalCasc:
    def __init__(self,install,product='wow',locale=None):
        self.locale=locale
        self.base=Path(install)/'Data';self.index={}
        lines=(Path(install)/'.build.info').read_text(encoding='utf-8').splitlines();names=[x.split('!')[0] for x in lines[0].split('|')]
        builds=[dict(zip(names,line.split('|'))) for line in lines[1:] if line.strip()]
        info=next(x for x in builds if x['Product']==product and x['Active']=='1');key=info['Build Key']
        config=(self.base/'config'/key[:2]/key[2:4]/key).read_text(encoding='utf-8')
        self.config=dict(line.split(' = ',1) for line in config.splitlines() if ' = ' in line)
        for file in sorted((self.base/'data').glob('*.idx'),reverse=True):
            data=file.read_bytes();h=struct.unpack_from('<I',data)[0];p=(8+h+15)&~15;length=struct.unpack_from('<I',data,p)[0];p+=8
            for off in range(p,p+length,18):
                key=data[off:off+9]
                if key in self.index:continue
                value=int.from_bytes(data[off+9:off+14],'big');size=struct.unpack_from('<I',data,off+14)[0]
                self.index[key]=(value>>30,value&0x3fffffff,size)
        self.encoding=self.read_key(bytes.fromhex(self.config['encoding'].split()[1]))
        d=self.encoding;assert d[:2]==b'EN'
        self.ck=d[3];self.ek=d[4];self.page_size=int.from_bytes(d[5:7],'big')*1024;self.pages=int.from_bytes(d[9:13],'big');spec=int.from_bytes(d[18:22],'big')
        pos=22+spec;self.page_keys=[d[pos+i*(self.ck+16):pos+i*(self.ck+16)+self.ck] for i in range(self.pages)];self.page_start=pos+self.pages*(self.ck+16)
        self.root=self.read_content(bytes.fromhex(self.config['root']));self.ids={}
    def read_key(self,key):
        bank,offset,size=self.index[key[:9]]
        with (self.base/'data'/f'data.{bank:03}').open('rb') as f:f.seek(offset+30);raw=f.read(size-30)
        return blte(raw)
    def content_key(self,ckey):
        i=bisect.bisect_right(self.page_keys,ckey)-1
        if i<0:raise KeyError(ckey.hex())
        p=self.page_start+i*self.page_size;end=p+self.page_size;d=self.encoding
        while p<end and d[p]:
            count=d[p];key=d[p+6:p+6+self.ck]
            if key==ckey:
                keys=[d[p+6+self.ck+j*self.ek:p+6+self.ck+(j+1)*self.ek] for j in range(count)]
                return next((k for k in keys if k[:9] in self.index),keys[0])
            p+=6+self.ck+self.ek*count
        raise KeyError(ckey.hex())
    def read_content(self,key):return self.read_key(self.content_key(key))
    def find(self,wanted):
        wanted=set(wanted)-self.ids.keys()
        if not wanted:return
        d=self.root;assert d[:4]==b'TSFM';header,version=struct.unpack_from('<II',d,4)
        if header!=24:total,named=header,version;version=0;p=12
        else:total,named=struct.unpack_from('<II',d,12);p=header
        while p<len(d):
            count=struct.unpack_from('<I',d,p)[0];p+=4
            if version in (0,1):flags,locale=struct.unpack_from('<II',d,p);p+=8
            elif version==2:locale,f1,f2,last=struct.unpack_from('<IIIB',d,p);flags=f1|f2|(last<<17);p+=13
            else:raise ValueError('Unsupported CASC root '+str(version))
            accepted=self.locale is None or locale==0 or bool(locale&{'enUS':2,'frFR':16}[self.locale])
            current=0;matches=[]
            for i,delta in enumerate(struct.unpack_from('<'+str(count)+'i',d,p)):
                current+=delta
                if current in wanted and accepted:matches.append((current,i))
                current+=1
            p+=count*4
            for fdid,i in matches:
                key=d[p+i*16:p+(i+1)*16]
                try:local=self.content_key(key)[:9] in self.index
                except KeyError:local=False
                if local or fdid not in self.ids:self.ids[fdid]=key
            p+=count*16
            if not(total!=named and flags&0x10000000):p+=count*8
    def read(self,fdid):
        self.find([fdid]);return self.read_content(self.ids[fdid])

if __name__=='__main__':
    import sys
    casc=LocalCasc(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
    for arg in sys.argv[3:]:
        fdid=int(arg);raw=casc.read(fdid);(out/str(fdid)).write_bytes(raw);print(fdid,len(raw),raw[:4])
