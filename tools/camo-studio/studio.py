"""Local reward-code generator. The former camo editor is retired."""
from pathlib import Path
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, unquote
import json, secrets, re, mimetypes, os, math, webbrowser, argparse
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ASSETS=ROOT/'src/main/resources/assets/zombierool'
TOKEN=secrets.token_urlsafe(32)
def reward_rarities():
 source=(ROOT/'src/main/java/me/cryo/zombierool/client/career/CareerUnlockables.java').read_text(encoding='utf-8')
 return dict(re.findall(r'CAMOS\.put\("([a-z0-9_]+)",\s*new CamoDef\("[^" ]+",\s*\d+,\s*(?:true|false),\s*\d+,\s*(?:true|false),\s*"([a-z_]+)"',source))
def reward_ids():return set(reward_rarities())

def catalog():
 source=(ROOT/'src/main/java/me/cryo/zombierool/client/TacZCamoOverlay.java').read_text(encoding='utf-8');defaults={}
 for ids,value in re.findall(r'case ([^;]+?) -> ([0-9.]+)f;',source):
  for key in re.findall(r'"([a-z0-9_]+)"',ids):defaults[key]=float(value)
 lang=json.loads((ASSETS/'lang/fr_fr.json').read_text(encoding='utf-8'));rarities=reward_rarities()
 result=[]
 for file in sorted((ASSETS/'textures/item/camos').glob('*.png')):
  ident=file.stem;meta=file.with_suffix('.png.mcmeta');animation=json.loads(meta.read_text(encoding='utf-8')).get('animation',{}) if meta.exists() else {}
  result.append(dict(id=ident,name=lang.get('camo.zombierool.'+ident,ident),repeats=defaults.get(ident,6),animation=animation,rarity=rarities.get('camo_'+ident,''),redeemable=('camo_'+ident in rarities)))
 return dict(camos=result,models=[],profiles={},token=TOKEN)
def issue_rewards(payload):
 import subprocess,shutil
 camos=payload.get('camos',[]);allowed=reward_ids()
 if not isinstance(camos,list) or len(camos)>100 or any(c not in allowed for c in camos):raise ValueError('Sélection de camos invalide')
 args=[shutil.which('node') or 'node',str(ROOT/'tools/reward-codes.mjs'),'issue','--camos',','.join(camos)]
 for key in ['zrf','xp','count']:
  value=payload.get(key,1 if key=='count' else 0);maximum=100 if key=='count' else 1000000
  if type(value) is not int or not (1 if key=='count' else 0)<=value<=maximum:raise ValueError('Valeur invalide : '+key)
  args+=['--'+key,str(value)]
 random_count=payload.get('randomCount',0);rarities=payload.get('rarities',['common','rare','epic','legendary'])
 if type(random_count) is not int or not 0<=random_count<=100:raise ValueError('Nombre de camos aléatoires invalide')
 available=reward_rarities()
 if not isinstance(rarities,list) or not rarities or any(r not in set(available.values()) for r in rarities):raise ValueError('Rareté invalide')
 pool=[ident for ident,rarity in available.items() if rarity in rarities and ident not in camos]
 if random_count>len(pool):raise ValueError('Pas assez de camos différents dans les raretés choisies')
 args+=['--random-count',str(random_count),'--rarities',','.join(rarities)]
 if not camos and not random_count and not payload.get('zrf',0) and not payload.get('xp',0):raise ValueError('Choisissez au moins une récompense')
 if payload.get('campaign')=='halloween_release':
  from datetime import datetime
  try:start=datetime.fromisoformat(payload.get('from','').replace('Z','+00:00'));end=datetime.fromisoformat(payload.get('until','').replace('Z','+00:00'))
  except (ValueError,TypeError):raise ValueError('Définissez la date réelle de sortie pour cette campagne')
  if start.tzinfo is None or end.tzinfo is None or abs((end-start).total_seconds()-30*86400)>1:raise ValueError('Cette campagne doit durer exactement 30 jours après la sortie')
 target=payload.get('target','').strip()
 if target:
  if not re.fullmatch(r'(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})',target):raise ValueError('UUID Minecraft invalide')
  args+=['--target',target]
 for key in ['from','until']:
  value=payload.get(key,'').strip()
  if value:
   from datetime import datetime
   try:datetime.fromisoformat(value.replace('Z','+00:00'))
   except ValueError:raise ValueError('Date invalide : '+key)
   args+=['--'+key,value]
 result=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,encoding='utf-8',timeout=15,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if result.returncode:raise ValueError('Génération impossible. Vérifiez les dates et la présence de la clé créateur.')
 codes=[line.strip() for line in result.stdout.splitlines() if line.startswith('ZR2.')]
 if len(codes)!=payload.get('count',1):raise ValueError('Nombre de codes générés incorrect')
 import base64
 rewards=[]
 for code in codes:
  encoded=code.split('.')[1];rewards.append(json.loads(base64.urlsafe_b64decode(encoded+'='*(-len(encoded)%4)))['reward'])
 return dict(codes=codes,rewards=rewards)
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def send(self,status,data,kind='application/json'):
  self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(data)
 def do_GET(self):
  try:
   if self.headers.get('Host') not in ('127.0.0.1:'+str(self.server.server_port),'localhost:'+str(self.server.server_port)):return self.send(403,b'{}')
   route=unquote(urlparse(self.path).path)
   if route=='/api/catalog':return self.send(200,json.dumps(catalog()).encode())
   if route in ('/index.html','/studio.js','/presets.json','/camo-appearance.json'):return self.send(404,b'{}')
   if route.startswith('/textures/'):
    base=ASSETS/'textures/item/camos';file=(base/route[10:]).resolve()
   else:base=HERE;file=(HERE/('home.html' if route=='/' else route.lstrip('/'))).resolve()
   if not file.is_relative_to(base.resolve()) or file.suffix not in ('.html','.js','.css','.png','.json'):return self.send(404,b'{}')
   self.send(200,file.read_bytes(),mimetypes.guess_type(file)[0] or 'application/octet-stream')
  except Exception as e:self.send(500,json.dumps({'error':str(e)}).encode())
 def do_POST(self):
  try:
   if self.path!='/api/issue' or self.headers.get('X-Studio-Token')!=TOKEN:return self.send(403,b'{}')
   length=int(self.headers.get('Content-Length','0'))
   if not 0<length<100000:return self.send(400,b'{}')
   payload=json.loads(self.rfile.read(length));result=issue_rewards(payload);self.send(200,json.dumps(result).encode())
  except Exception as e:self.send(400,json.dumps({'error':str(e)}).encode())
class LocalServer(ThreadingHTTPServer):
 allow_reuse_address=False
 def server_bind(self):
  import socket
  if hasattr(socket,'SO_EXCLUSIVEADDRUSE'):self.socket.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
  super().server_bind()
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--no-browser',action='store_true');parser.add_argument('--port',type=int,default=8769);parser.add_argument('--page',default='/');args=parser.parse_args()
 try:server=LocalServer(('127.0.0.1',args.port),Handler)
 except OSError:
  import urllib.request
  url='http://127.0.0.1:'+str(args.port)
  existing=json.load(urllib.request.urlopen(url+'/api/catalog',timeout=2))
  if not all(key in existing for key in ('models','camos','profiles')):raise RuntimeError('Port occupied; use --port with another number')
  if not args.no_browser:webbrowser.open(url+args.page)
  print('ZombieRool Creator is already running: '+url,flush=True);raise SystemExit(0)
 url='http://127.0.0.1:'+str(server.server_port);print('ZombieRool Creator: '+url,flush=True)
 if not args.no_browser:webbrowser.open(url+args.page)
 server.serve_forever()
