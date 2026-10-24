"""Build the private multiplayer distribution from the two local CurseForge instances."""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'distribution'
SOURCE = Path.home() / 'curseforge/minecraft/Instances/ZombieRool'
MAP_SOURCE = SOURCE.with_name('ZombieRool (no mods)')
SERVER = OUT / 'server'
CLIENT = OUT / 'client-installer'


def sha(path):
    return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def copy_tree(src, dst):
    if src.exists():
        shutil.copytree(src, dst, dirs_exist_ok=True,
                        ignore=shutil.ignore_patterns('*.bak', 'session.lock', '.export-state.json'))


def main():
    SERVER.mkdir(parents=True, exist_ok=True)
    CLIENT.mkdir(parents=True, exist_ok=True)
    metadata = json.loads((SOURCE / 'minecraftinstance.json').read_text(encoding='utf-8-sig'))
    assert metadata['gameVersion'] == '1.20.1'
    assert metadata['baseModLoader']['name'] == 'forge-47.4.10'
    payload = OUT / 'client-staging'
    if payload.exists():
        raise RuntimeError('client-staging existe deja : utiliser un nouveau dossier de distribution.')
    folders = ['mods', 'tacz', 'config', 'defaultconfigs', 'resourcepacks', 'shaderpacks']
    for folder in folders:
        copy_tree(SOURCE / folder, payload / folder)
        (payload / folder).mkdir(parents=True, exist_ok=True)
    # One exact duplicate exists in the source. Keep one copy per archive hash.
    seen = set()
    duplicates = []
    for path in sorted((payload / 'tacz').glob('*.zip')):
        digest = sha(path)
        if digest in seen:
            duplicates.append(path.name)
            path.unlink()
        else:
            seen.add(digest)
    downloads = {}
    for addon in metadata.get('installedAddons', []):
        f = addon.get('installedFile', {})
        if f.get('fileName') and f.get('downloadUrl'):
            downloads['mods/' + f['fileName']] = f['downloadUrl']
    files = []
    with zipfile.ZipFile(CLIENT / 'client-payload.zip', 'w', zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for folder in folders:
            archive.mkdir(folder + '/')
        for path in sorted(payload.rglob('*')):
            if path.is_file():
                rel = path.relative_to(payload).as_posix()
                archive.write(path, rel)
                files.append({'path': rel, 'sha256': sha(path), 'url': downloads.get(rel)})
    manifest = {'minecraft': '1.20.1', 'forge': '47.4.10', 'folders': folders,
                'payloadSha256': sha(CLIENT / 'client-payload.zip'), 'files': files,
                'removedExactDuplicates': duplicates}
    write_json(CLIENT / 'manifest.json', manifest)
    shutil.copy2(Path(__file__).with_name('Install-ZombieRool.ps1'), CLIENT)
    (CLIENT / 'Installer-ZombieRool.cmd').write_text(
        '@echo off\ncd /d "%~dp0"\npowershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0Install-ZombieRool.ps1"\npause\n', encoding='ascii')
    (CLIENT / 'LIRE-MOI.txt').write_text(
        'ZOMBIEROOL - INSTALLATION POUR LES AMIS\n\n'
        '1. Extraire TOUT le ZIP avant de lancer le programme.\n'
        '2. Creer ou choisir une instance CurseForge Minecraft 1.20.1 / Forge 47.4.10.\n'
        '3. Fermer Minecraft. Double-cliquer Installer-ZombieRool.cmd.\n'
        '4. Selectionner le dossier de cette instance (CurseForge > clic droit > Ouvrir le dossier).\n'
        '5. Lancer le jeu depuis CurseForge puis Multijoueur > adresse du serveur.\n\n'
        'Les mods publics sont telecharges dans leur version exacte. Tous les fichiers sont aussi inclus\n'
        'pour installer hors ligne si une source est indisponible, y compris ZombieRool et les gunpacks locaux.\n'
        'Verification SHA-256. Anciens mods/gunpacks/configs sauvegardes dans .zombierool-backup-...\n'
        'Les mondes, options, carrieres et comptes ne sont pas modifies.\n'
        'Restauration : jeu ferme, deplacer les dossiers installes ailleurs puis remettre ceux de la sauvegarde.\n'
        'Mode hors ligne : powershell -ExecutionPolicy Bypass -File .\\Install-ZombieRool.ps1 -Offline\n', encoding='utf-8')
    compatibility = (
        'COMPATIBILITE DES GUNPACKS D ORIGINE\n\n'
        'Le serveur demarre avec TaCZ 1.1.8-hotfix2 et les fichiers exacts de l instance.\n'
        'Tous les packs sont fournis, mais certains contenus etaient deja incomplets ou anciens :\n'
        '- Black Ops6 v1.0.2 : ancien format, sans gunpack.meta.json ; TaCZ 1.1.8 ne le charge pas.\n'
        '  Le pack Warzone moderne fourni contient aussi des armes BO6, mais ce sont deux packs distincts.\n'
        '- MW2019 : jak12 n a pas son fichier de donnees.\n'
        '- Gunpowder Revolution : fichiers de donnees absents pour lebel1886_07c, m1887_hc,\n'
        '  uppercut, coltm1851_chain, lugerp08_artillerie et colt1873_lb.\n'
        '- WaT : entree laser_peq15 sans donnees et recette m32a1_he invalide.\n'
        '- Enlisted : entree i37 sans donnees, deux listes d accessoires mal formees\n'
        '  (m1928a1 et m28s) et une recette avec nom de fichier invalide.\n\n'
        'Ces erreurs ne bloquent pas le serveur, mais les entrees rejetees sont indisponibles.\n'
        'Les fichiers des auteurs sont conserves : aucune arme manquante n est inventee.\n'
        'Le fonctionnement en partie et la connexion depuis Internet restent a tester avec des joueurs.\n')
    (CLIENT / 'Compatibilite-gunpacks.txt').write_text(compatibility, encoding='utf-8')
    (SERVER / 'Compatibilite-gunpacks.txt').write_text(compatibility, encoding='utf-8')

    client_only = ('embeddium-', 'oculus-')
    for path in (payload / 'mods').glob('*.jar'):
        if not path.name.startswith(client_only):
            (SERVER / 'mods').mkdir(exist_ok=True)
            shutil.copy2(path, SERVER / 'mods' / path.name)
    for folder in ['tacz', 'defaultconfigs']:
        copy_tree(payload / folder, SERVER / folder)
    (SERVER / 'config').mkdir(exist_ok=True)
    for name in ['tacz-common.toml', 'effortlessbuilding-common.toml']:
        src = payload / 'config' / name
        if src.exists():
            shutil.copy2(src, SERVER / 'config' / name)
    maps = []
    sources = [('nacht-der-untoten', MAP_SOURCE / 'saves/Nacht der Untoten')]
    for key, path in sources:
        dest = SERVER / 'worlds' / key
        if dest.exists():
            raise RuntimeError(f'Map deja presente, aucune sauvegarde ecrasee : {dest}')
        copy_tree(path, dest)
        maps.append({'id': key, 'name': path.name, 'sourceInstance': path.parents[1].name,
                     'sourceLevelSha256': sha(path / 'level.dat')})
    write_json(SERVER / 'maps.json', maps)
    write_json(SERVER / 'maps-local.json', maps)
    shutil.copy2(Path(__file__).with_name('Start-ZombieRool.ps1'), SERVER)
    shutil.copy2(Path(__file__).with_name('Map-Catalogue.ps1'), SERVER)
    for name in ['Update-ServerAddress.ps1', 'Configurer-Adresse-Gratuite.ps1', 'Configurer-Adresse-Gratuite.cmd']:
        shutil.copy2(Path(__file__).with_name(name), SERVER)
    for name in ['Configurer-PareFeu.ps1', 'Configurer-PareFeu.cmd']:
        shutil.copy2(Path(__file__).with_name(name), SERVER)
    (SERVER / 'Demarrer-ZombieRool.cmd').write_text(
        '@echo off\ncd /d "%~dp0"\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-ZombieRool.ps1"\npause\n', encoding='ascii')
    java = shutil.which('java')
    if java:
        (SERVER / 'java-path.txt').write_text(java, encoding='utf-8')
    (SERVER / 'server.properties').write_text(
        'motd=ZombieRool - Survie Zombies\nserver-port=25565\nserver-ip=\n'
        'level-name=worlds/nacht-der-untoten\nonline-mode=true\n'
        'gamemode=adventure\nforce-gamemode=true\ndifficulty=normal\n'
        'max-players=8\nallow-flight=true\nenable-command-block=true\n'
        'spawn-protection=0\nview-distance=8\nsimulation-distance=6\n'
        'pvp=false\nwhite-list=false\nenable-rcon=false\nenable-query=false\n', encoding='ascii')
    (SERVER / 'eula.txt').write_text('eula=false\n', encoding='ascii')
    (SERVER / 'user_jvm_args.txt').write_text('-Xms2G\n-Xmx6G\n', encoding='ascii')
    (SERVER / 'LIRE-MOI.txt').write_text(
        'ZOMBIEROOL - SERVEUR 1.20.1 / FORGE 47.4.10\n\n'
        'Double-cliquer Demarrer-ZombieRool.cmd, choisir la map puis accepter les conditions Minecraft.\n'
        'Java 17 requis. java-path.txt contient le chemin local de java.exe. RAM par defaut : 6 Go.\n'
        'RAM personnalisee : powershell -ExecutionPolicy Bypass -File .\\Start-ZombieRool.ps1 -MemoryGB 4\n'
        'Arret propre : saisir stop dans la console. Pour changer de map, arreter et relancer le selecteur.\n'
        'Chaque map conserve sa propre progression dans worlds. Les sauvegardes CurseForge originales restent intactes.\n'
        'Sauvegarder worlds avant de reinitialiser une map. Ne jamais copier un monde pendant son utilisation.\n\n'
        'Connexion locale : localhost:25565. Meme reseau : adresse IPv4 du PC serveur, port 25565.\n'
        'Amis sur Internet : rediriger TCP 25565 dans la box vers ce PC et autoriser Java dans le pare-feu,\n'
        'ou utiliser un VPN prive commun. Donner ensuite cette adresse a tes amis.\n'
        'Le routeur et le pare-feu ne sont pas modifies automatiquement.\n'
        'Pour limiter aux amis : whitelist on puis whitelist add PSEUDO dans la console.\n'
        'Pour administrer ZombieRool : op TON_PSEUDO dans la console.\n\n'
        'Nacht der Untoten provient uniquement de ZombieRool (no mods).\n'
        'Oculus et Embeddium reserves au client ; TacZ Mesh Loader inclus car requis par Elite X.\n', encoding='utf-8')
    with zipfile.ZipFile(OUT / 'ZombieRool-Installateur-Amis.zip', 'w', zipfile.ZIP_STORED) as archive:
        for path in CLIENT.rglob('*'):
            if path.is_file():
                archive.write(path, 'ZombieRool-Installateur/' + path.relative_to(CLIENT).as_posix())
    print(json.dumps({'maps': len(maps), 'clientFiles': len(files), 'downloadableMods': len(downloads),
                      'duplicatesRemoved': duplicates, 'installerMB': round((OUT / 'ZombieRool-Installateur-Amis.zip').stat().st_size / 1e6)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
