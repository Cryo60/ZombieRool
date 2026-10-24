"""Buildable translations and original T6 assembly audio, read-only against the local game banks."""
from pathlib import Path
import io, json, struct, subprocess, sys, hashlib, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ROOTS = [ROOT/'src/main/resources', ROOT/'resources', ROOT]
GUI_EN = {
    'title':'Buildables — map maker', 'new':'New recipe', 'id':'Recipe ID', 'name':'Display name',
    'part_item':'Part: held item', 'part_block':'Part: target block',
    'parts':'Unique part IDs, separated by commas', 'ticks':'Assembly time (ticks, 40 minimum)',
    'limit':'Total copies (0 = unlimited)', 'mode':'Result: %s', 'held_reward':'Use held item',
    'keep_reward':'Keep saved result', 'save':'Save recipe', 'delete':'Delete recipe',
    'part':'Part ID at the targeted block', 'bind_part':'Bind this part', 'bind_bench':'Dedicated bench',
    'free_bench':'Free bench', 'unbind':'Unbind target', 'close':'Close',
    'target':'Target: %s', 'no_target':'aim at a block before opening',
    'numbers':'Assembly time and copy limit must be integers.', 'small_screen':'Reduce GUI scale to edit buildables.'
}
GUI_FR = {
    'title':'Objets à assembler — map maker', 'new':'Nouvelle recette', 'id':'Identifiant de recette', 'name':'Nom affiché',
    'part_item':'Pièce : objet tenu', 'part_block':'Pièce : bloc visé',
    'parts':'Identifiants des pièces, séparés par des virgules', 'ticks':'Assemblage (ticks, minimum 40)',
    'limit':'Exemplaires au total (0 = illimité)', 'mode':'Résultat : %s', 'held_reward':'Utiliser l’objet tenu',
    'keep_reward':'Garder le résultat enregistré', 'save':'Enregistrer', 'delete':'Supprimer la recette',
    'part':'Identifiant de la pièce visée', 'bind_part':'Lier cette pièce', 'bind_bench':'Établi dédié',
    'free_bench':'Établi libre', 'unbind':'Délier le bloc visé', 'close':'Fermer',
    'target':'Bloc visé : %s', 'no_target':'viser un bloc avant d’ouvrir',
    'numbers':'La durée et le nombre d’exemplaires doivent être des entiers.', 'small_screen':'Réduisez l’échelle de l’interface pour modifier les recettes.'
}
MSG_EN = {
    'carried_hud':'Part: %s',
    'pickup':'[%s] Pick up part: %s', 'take':'[%s] Take %s',
    'bench':'[%s] Assemble %s — %s/%s | Carried: %s', 'carrying':'You already carry %s. Install it or drop it first.',
    'need_part':'Find a part and bring it to this workbench.', 'wrong_bench':'This part belongs to a different buildable.',
    'already_installed':'This part is already installed.', 'other_bench':'This object is being built at another workbench.',
    'busy':'Another player is assembling a part.', 'assembling':'Assembling %s… %s s',
    'exhausted':'No more copies are available.', 'owned':'You already have this object.', 'inventory_full':'Your inventory is full.',
    'blocked':'Clear the space above the workbench.', 'editing_match':'Stop the match before changing buildables.',
    'invalid_id':'Use a unique recipe ID: a-z, 0-9, _, ., /, :, - (64 characters maximum).',
    'invalid_parts':'Specify 1–32 different valid part IDs, separated by commas.', 'invalid_mode':'Result must be item, block or event.',
    'empty_reward':'Hold the desired result in your main hand.', 'block_reward':'Block results require a block item.',
    'unknown_recipe':'Save or select an existing recipe first.', 'unknown_part':'This part ID is not in the recipe.',
    'target_block':'Aim at the target block within six blocks, then reopen the editor.', 'saved':'Buildable configuration saved.'
}
MSG_FR = {
    'carried_hud':'Pièce : %s',
    'pickup':'[%s] Ramasser la pièce : %s', 'take':'[%s] Récupérer %s',
    'bench':'[%s] Assembler %s — %s/%s | Pièce portée : %s', 'carrying':'Vous portez déjà %s. Assemblez-la ou déposez-la d’abord.',
    'need_part':'Trouvez une pièce et apportez-la à cet établi.', 'wrong_bench':'Cette pièce appartient à un autre objet.',
    'already_installed':'Cette pièce est déjà assemblée.', 'other_bench':'Cet objet est en construction sur un autre établi.',
    'busy':'Un autre joueur assemble une pièce.', 'assembling':'Assemblage de %s… %s s',
    'exhausted':'Aucun autre exemplaire n’est disponible.', 'owned':'Vous possédez déjà cet objet.', 'inventory_full':'Votre inventaire est plein.',
    'blocked':'Libérez l’espace au-dessus de l’établi.', 'editing_match':'Arrêtez la partie avant de modifier les buildables.',
    'invalid_id':'Identifiant : a-z, 0-9, _, ., /, :, - (64 caractères maximum).',
    'invalid_parts':'Indiquez 1 à 32 identifiants de pièces différents, séparés par des virgules.', 'invalid_mode':'Le résultat doit être item, block ou event.',
    'empty_reward':'Tenez le résultat souhaité dans la main principale.', 'block_reward':'Le résultat block exige un objet bloc.',
    'unknown_recipe':'Enregistrez ou sélectionnez d’abord une recette existante.', 'unknown_part':'Cette pièce ne figure pas dans la recette.',
    'target_block':'Visez le bloc cible à moins de six blocs, puis rouvrez l’éditeur.', 'saved':'Configuration des buildables enregistrée.'
}

def write_json(relative, data):
    for root in ROOTS:
        p=root/relative; p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

for lang in ['en_us','fr_fr','de_de','es_es','pt_br','ru_ru']:
    path=ROOTS[0]/f'assets/zombierool/lang/{lang}.json'
    data=json.loads(path.read_text(encoding='utf8')) if path.exists() else {}
    data.update({'gui.zombierool.buildable.'+k:v for k,v in (GUI_FR if lang=='fr_fr' else GUI_EN).items()})
    data.update({'message.zombierool.buildable.'+k:v for k,v in (MSG_FR if lang=='fr_fr' else MSG_EN).items()})
    data['subtitles.zombierool.buildable_assemble']='Assemblage de pièce' if lang=='fr_fr' else 'Assembling part'
    data['subtitles.zombierool.buildable_installed']='Pièce installée' if lang=='fr_fr' else 'Part installed'
    data['subtitles.zombierool.buildable_complete']='Construction terminée' if lang=='fr_fr' else 'Buildable completed'
    write_json(f'assets/zombierool/lang/{lang}.json',data)

if '--translations-only' in sys.argv:
    sys.exit(0)
sys.path.insert(0,str(ROOT/'build/audio-python'))
import numpy as np
import soundfile as sf

names={int(e.attrib['Hash'],16):e.attrib['Path'] for e in ET.parse(ROOT/'build/dev-26.7/Names.xml').getroot()
       if int(e.attrib['Hash'],16) in {0x7B36C904,0x32DA235A,0xA70972BB}}
rates=[8000,12000,16000,24000,32000,44100,48000,96000,192000]
found={}
for bank in sorted(Path('D:/Jeux/pluto_t6_full_game/sound').glob('*.sab*')):
    with bank.open('rb') as f:
        header=f.read(72)
        if len(header)<48 or struct.unpack_from('<II',header)!=(0x23585532,14):continue
        count=struct.unpack_from('<I',header,20)[0]; table=struct.unpack_from('<Q',header,40)[0];f.seek(table)
        for _ in range(count):
            record=f.read(20)
            if len(record)!=20:break
            key,size,offset,samples,rate,ch,loop,fmt=struct.unpack('<IIIIBBBB',record)
            if key in names and key not in found and rate<len(rates):found[key]=(bank,size,offset,rates[rate],ch,fmt)
manifest=[];sounds=json.loads((ROOTS[0]/'assets/zombierool/sounds.json').read_text(encoding='utf8'))
for key,(bank,size,offset,rate,ch,fmt) in found.items():
    name=names[key];event='assemble' if 'building_lp' in name else 'installed' if 'add_piece' in name else 'complete'
    with bank.open('rb') as f:f.seek(offset);raw=f.read(size)
    if fmt==0:audio=np.frombuffer(raw,dtype='<i2').reshape(-1,ch).astype(np.float64)/32768
    else:
        decoded=subprocess.run([str(ROOT/'build/audio-tools/ffmpeg.exe'),'-v','error','-i','pipe:0','-f','wav','pipe:1'],input=raw,capture_output=True,check=True).stdout
        audio,rate=sf.read(io.BytesIO(decoded),always_2d=True)
    mono=audio.mean(axis=1)
    # The assembly gesture lasts two seconds; a long original loop must not outlive it.
    if event=='assemble':mono=mono[:2*rate]
    fade=min(int(rate*.012),len(mono)//2);mono[:fade]*=np.linspace(0,1,fade);mono[-fade:]*=np.linspace(1,0,fade)
    mono*=min(1,.8/max(.001,np.max(np.abs(mono))))
    buf=io.BytesIO();sf.write(buf,mono,rate,format='OGG',subtype='VORBIS');encoded=buf.getvalue()
    for root in ROOTS:
        p=root/f'assets/zombierool/sounds/buildable/{event}.ogg';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encoded)
    sounds['buildable_'+event]={'subtitle':'subtitles.zombierool.buildable_'+event,'sounds':[{'name':'zombierool:buildable/'+event,'stream':False}]}
    manifest.append({'event':'buildable_'+event,'source':str(bank),'asset':name,'hash':f'{key:08X}','sha256':hashlib.sha256(raw).hexdigest(),'seconds':len(mono)/rate})
assert len(manifest)==3,f'Expected three original buildable clips, found {len(manifest)}'
write_json('assets/zombierool/sounds.json',sounds)
out=ROOT/'assets-source/26.7/audio/buildables-manifest.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
print(json.dumps(manifest,indent=2))
