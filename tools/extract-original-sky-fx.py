"""Export loaded FX from our own OpenAssetTools subprocess, without opening a game process.

OAT already deserializes FX structures but its released dumper does not write them.
The stdout pipe keeps that subprocess alive while we read its loaded, relocated data.
Layouts: Laupetin/OpenAssetTools src/Common/Game/T6/T6_Assets.h.
"""
from pathlib import Path
import ctypes as C,ctypes.wintypes as W,struct,subprocess,json,sys,re
ROOT=Path(__file__).resolve().parents[1]
K=C.WinDLL('kernel32',use_last_error=True);N=C.WinDLL('ntdll')
class MBI(C.Structure):
 _fields_=[('BaseAddress',C.c_void_p),('AllocationBase',C.c_void_p),('AllocationProtect',W.DWORD),('PartitionId',W.WORD),('RegionSize',C.c_size_t),('State',W.DWORD),('Protect',W.DWORD),('Type',W.DWORD)]
K.VirtualQueryEx.argtypes=[W.HANDLE,C.c_void_p,C.POINTER(MBI),C.c_size_t];K.VirtualQueryEx.restype=C.c_size_t
K.ReadProcessMemory.argtypes=[W.HANDLE,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)];K.ReadProcessMemory.restype=W.BOOL
N.NtSuspendProcess.argtypes=[W.HANDLE];N.NtResumeProcess.argtypes=[W.HANDLE]
class Range(C.LittleEndianStructure):_fields_=[('base',C.c_float),('amplitude',C.c_float)]
class IntRange(C.LittleEndianStructure):_fields_=[('base',C.c_int32),('amplitude',C.c_int32)]
class Effect(C.LittleEndianStructure):
 _fields_=[('name',C.c_uint32),('flags',C.c_uint16),('priority',C.c_int8),('looping',C.c_int16),('oneShot',C.c_int16),('emission',C.c_int16),('totalSize',C.c_int32),('loopingLife',C.c_int32),('oneShotLife',C.c_int32),('elements',C.c_uint32),('bounds',C.c_float*3),('center',C.c_float*3),('depthBias',C.c_float),('fadeIn',C.c_int32),('fadeOut',C.c_int32),('scale',Range)]
class EffectT5(C.LittleEndianStructure):
 _fields_=[('name',C.c_uint32),('flags',C.c_uint8),('priority',C.c_int8),('reserved',C.c_uint8*2),('totalSize',C.c_int32),('loopingLife',C.c_int32),('looping',C.c_int32),('oneShot',C.c_int32),('emission',C.c_int32),('elements',C.c_uint32),('bounds',C.c_float*3),('sphere',C.c_float*4)]
class Atlas(C.LittleEndianStructure):_fields_=[('behavior',C.c_uint8),('index',C.c_uint8),('fps',C.c_uint8),('loopCount',C.c_uint8),('colBits',C.c_uint8),('rowBits',C.c_uint8),('entries',C.c_uint16)]
class Element(C.LittleEndianStructure):
 _fields_=[('flags',C.c_int32),('spawn',IntRange),('spawnRange',Range),('fadeInRange',Range),('fadeOutRange',Range),('cullRadius',C.c_float),('delay',IntRange),('life',IntRange),('origin',Range*3),('offsetRadius',Range),('offsetHeight',Range),('angles',Range*3),('angularVelocity',Range*3),('initialRotation',Range),('rotationAxis',C.c_uint32),('gravity',Range),('reflection',Range),('atlas',Atlas),('wind',C.c_float),('type',C.c_uint8),('visualCount',C.c_uint8),('velCount',C.c_uint8),('visCount',C.c_uint8),('velSamples',C.c_uint32),('visSamples',C.c_uint32),('visuals',C.c_uint32),('collMins',C.c_float*3),('collMaxs',C.c_float*3),('onImpact',C.c_uint32),('onDeath',C.c_uint32),('emitted',C.c_uint32),('emitDist',Range),('emitVariance',Range),('attached',C.c_uint32),('extended',C.c_uint32),('sort',C.c_uint8),('lighting',C.c_uint8),('unused',C.c_uint8*2),('alphaFade',C.c_uint16),('maxWind',C.c_uint16),('spawnIntervalAtMaxWind',C.c_uint16),('lifeAtMaxWind',C.c_uint16),('extra',Range),('spawnSound',C.c_uint32),('pivot',C.c_float*2)]
class Visual(C.LittleEndianStructure):_fields_=[('color',C.c_uint8*4),('rotationDelta',C.c_float),('rotationTotal',C.c_float),('size',C.c_float*2),('scale',C.c_float)]
TYPES=['billboard','oriented','rotated','tail','line','trail','cloud','model','omniLight','spotLight','sound','decal','runner']
def values(x):
 if isinstance(x,C.Array):return [values(v) for v in x]
 if isinstance(x,C.Structure):return {n:values(getattr(x,n)) for n,*_ in x._fields_}
 return x
class Memory:
 def __init__(self,handle,game):self.handle=handle;self.game=game;self.effectType=EffectT5 if game=="t5" else Effect;self.regions=[]
 def read(self,address,count):
  if count<0 or count>64*1024*1024:raise ValueError('Invalid read size')
  buf=C.create_string_buffer(count);got=C.c_size_t()
  if not K.ReadProcessMemory(self.handle,address,buf,count,C.byref(got)) or got.value!=count:raise ValueError('Unreadable address '+hex(address))
  return buf.raw
 def u32(self,addr):return struct.unpack('<I',self.read(addr,4))[0]
 def string(self,addr):
  if not addr:return ''
  try:return self.read(addr,256).split(b'\0')[0].decode('utf8')
  except (ValueError,UnicodeDecodeError):return ''
 def scan(self):
  address=0
  while address<0x100000000:
   m=MBI()
   if not K.VirtualQueryEx(self.handle,address,C.byref(m),C.sizeof(m)):break
   end=(m.BaseAddress or 0)+m.RegionSize
   if end<=address:break
   if m.State==0x1000 and not m.Protect&0x101 and m.Protect&0xee:
    for offset in range(0,m.RegionSize,8*1024*1024):
     size=min(8*1024*1024,m.RegionSize-offset)
     try:self.regions.append((address+offset,self.read(address+offset,size)))
     except ValueError:pass
   address=end
 def find(self,needle):
  for base,data in self.regions:
   offset=-1
   while True:
    offset=data.find(needle,offset+1)
    if offset<0:break
    yield base+offset
 def effect(self,name):
  results=[]
  for string in self.find(name.encode()+b'\0'):
   for pointer in self.find(struct.pack('<I',string)):
    try:
     fx=self.effectType.from_buffer_copy(self.read(pointer,C.sizeof(self.effectType)));count=fx.looping+fx.oneShot+fx.emission
     if not (0<count<256 and min(fx.looping,fx.oneShot,fx.emission)>=0 and fx.elements>65536 and C.sizeof(self.effectType)+count*C.sizeof(Element)<=fx.totalSize<=32*1024*1024):continue
     elements=[Element.from_buffer_copy(self.read(fx.elements+i*C.sizeof(Element),C.sizeof(Element))) for i in range(count)]
     if any(e.type>=len(TYPES) or e.visualCount>128 for e in elements):continue
     results.append((pointer,fx,elements))
    except (ValueError,OSError):continue
  return results
 def export(self,name):
  hits=self.effect(name)
  if not hits:raise ValueError('No validated FX structure: '+name)
  pointer,fx,elements=hits[0];result=values(fx);result['name']=name;result['schemaVersion']=1;result.pop('elements');result['elements']=[]
  for i,e in enumerate(elements):
   row=values(e);row['typeName']=TYPES[e.type];row['group']='looping' if i<fx.looping else 'oneShot' if i<fx.looping+fx.oneShot else 'emission'
   row['visuals']=[]
   for j in range(e.visualCount):
    v=self.u32(e.visuals+j*4) if e.visualCount>1 else e.visuals
    label=self.string(v) if e.type in (10,12) else self.string(self.u32(v)) if v else ''
    row['visuals'].append(label)
   row['visualSamples']=[]
   if e.visSamples:
    for j in range(e.visCount+1):
     data=self.read(e.visSamples+j*48,48);row['visualSamples'].append({'base':values(Visual.from_buffer_copy(data[:24])),'amplitude':values(Visual.from_buffer_copy(data[24:]))})
   row['velocitySamples']=[]
   if e.velSamples:
    for j in range(e.velCount+1):row['velocitySamples'].append(list(struct.unpack('<24f',self.read(e.velSamples+j*96,96))))
   for key in ['onImpact','onDeath','emitted','attached']:
    ptr=getattr(e,key);direct=self.string(ptr) if ptr else ''
    try:row[key]=direct if '/' in direct else self.string(self.u32(ptr)) if ptr else ''
    except ValueError:row[key]='';row.setdefault('unresolvedInactiveReferences',{})[key]=hex(ptr)
   if e.type==5 and e.extended:
    raw=self.read(e.extended,28);scroll,repeat,split,vertexCount,vertices,indexCount,indices=struct.unpack('<4iIiI',raw)
    if not (0<=vertexCount<=4096 and 0<=indexCount<=16384):raise ValueError('Invalid trail counts')
    row['trail']={'scrollTimeMsec':scroll,'repeatDist':repeat,'splitDist':split,'vertices':[list(struct.unpack('<5f',self.read(vertices+j*20,20))) for j in range(vertexCount)],'indices':list(struct.unpack('<'+str(indexCount)+'H',self.read(indices,indexCount*2))) if indexCount else []}
   if e.type==9 and e.extended:row['spotLight']=list(struct.unpack('<3f',self.read(e.extended,12)))
   row['spawnSound']=self.string(e.spawnSound) if e.spawnSound else ''
   row.pop('visSamples');row.pop('velSamples');row.pop('extended');result['elements'].append(row)
  return result

def main():
 zone=Path(sys.argv[1]);out=Path(sys.argv[2]);names=sys.argv[3:];game='t5' if 'pluto_t5' in str(zone).lower() else 't6';out.mkdir(parents=True,exist_ok=True)
 command=[str(ROOT/'build/boss-assets/oat/Unlinker.exe'),'--include-assets','material','--output-folder',str(out/'materials'),str(zone)]
 proc=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 try:
  while True:
   line=proc.stdout.readline()
   if not line:raise RuntimeError('Extractor exited before loading zone')
   if b'Dumped material' in line:break
  N.NtSuspendProcess(int(proc._handle));memory=Memory(int(proc._handle),game);memory.scan();print('Readable process memory:',sum(len(d) for _,d in memory.regions)//1048576,'MiB; layouts',C.sizeof(Effect),C.sizeof(Element),flush=True)
  exported=set();failed=[]
  while names:
   name=names.pop(0)
   if name in exported:continue
   exported.add(name)
   try:
    result=memory.export(name);result['game']=game
    for element in result['elements']:
     if element['typeName']=='runner':names.extend(v.lstrip(',') for v in element['visuals'] if '/' in v)
    path=out/(name.replace('/','__')+'.json');path.write_text(json.dumps(result,indent=2,allow_nan=False),encoding='utf8');print('EXPORTED',name,len(result['elements']),'elements',flush=True)
   except Exception as e:
    import traceback;traceback.print_exc();failed.append(name);print('FAILED',name,str(e),flush=True)
 finally:
  N.NtResumeProcess(int(proc._handle));proc.terminate();proc.wait(timeout=15)
 if failed:raise SystemExit('FX extraction failed: '+', '.join(failed))
if __name__=='__main__':main()
