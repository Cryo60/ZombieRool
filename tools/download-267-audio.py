"""Fetch CC0 sound packs from the author's download links, preserving their licenses."""
from pathlib import Path
import urllib.request, re, zipfile
root=Path(__file__).resolve().parents[1]/'assets-source/26.7/audio'
root.mkdir(parents=True,exist_ok=True)
for name in ['rpg-audio','impact-sounds','sci-fi-sounds']:
    page='https://kenney.nl/assets/'+name
    html=urllib.request.urlopen(page).read().decode()
    urls=re.findall(r'https://[^\s\"\'<>]+\.zip',html)
    if not urls: raise RuntimeError('No download link in '+page)
    url=urls[0]; print(name,url,flush=True)
    archive=root/(name+'.zip')
    urllib.request.urlretrieve(url,archive)
    with zipfile.ZipFile(archive) as z:z.extractall(root/name)
    (root/(name+'-source.txt')).write_text(page+'\n'+url+'\n',encoding='utf-8')
print('\n'.join(str(p.relative_to(root)) for p in root.rglob('*') if p.suffix in ['.ogg','.wav']))
