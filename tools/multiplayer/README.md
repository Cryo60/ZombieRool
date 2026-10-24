# Distribution multijoueur privee

`build_distribution.py` prepare `distribution/server` et
`distribution/ZombieRool-Installateur-Amis.zip` depuis les instances CurseForge
locales `ZombieRool` et `ZombieRool (no mods)`. Il conserve les versions exactes
et copie les mondes sans toucher aux instances. Le dossier `distribution` est
ignore par Git et contient les binaires et sauvegardes prives.

Prerequis : Python 3.11+, Java 17 et serveur Forge 1.20.1-47.4.10 installe dans
`distribution/server`. Installateur officiel :
https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar

```powershell
java -jar distribution/forge-installer.jar --installServer distribution/server
python tools/multiplayer/build_distribution.py
python tools/multiplayer/validate_distribution.py
```

La construction refuse de reutiliser un staging ou des mondes deja existants.
Pour reconstruire, archiver d'abord toute la distribution dans un autre dossier.
Ne pas ecraser un serveur utilise pour jouer.

Le lanceur lit les memes catalogues GitHub que `MapCommunityClient.java` :
`Cryo60/zombierool-maps` et `Cryo60/zombierool-community-hub`, branche `main`,
fichier `maps.json`. Il filtre les maps approuvees pour Minecraft 1.20.1.
Nacht der Untoten reste la copie de `ZombieRool (no mods)` et ne peut pas etre
remplacee automatiquement par une version GitHub. Les autres sauvegardes de
developpement ne font pas partie du catalogue.

Le catalogue est actualise avant le choix de map, avec cache hors ligne.
Une map distante absente est telechargee a la selection, verifiee par SHA-256,
puis extraite apres validation des chemins. Un monde deja installe est conserve
sans mise a jour automatique, pour preserver la progression. Les dependances
declarees par l'auteur sont affichees et enregistrees ; elles ne sont pas
ajoutees automatiquement aux mods des joueurs. Des versions de map anciennes
ou des mods supplementaires peuvent demander une adaptation pour jouer.

Le lanceur conserve un monde independant par map, verifie Java et le port,
et verrouille les lancements concurrents.
L'acceptation de l'EULA revient a l'utilisateur au premier lancement.
La validation locale reutilise temporairement l'EULA deja acceptee dans
`run-server/eula.txt`, puis remet `eula=false` dans la distribution.

L'installateur client fonctionne sous Windows PowerShell 5.1, demande un dossier
d'instance avec une boite de dialogue, valide les versions, telecharge les mods
referencables dans les metadonnees CurseForge et utilise la copie embarquee si
le telechargement echoue. Les gunpacks locaux et ZombieRool sont embarques.
Tous les fichiers sont verifies par SHA-256 avant remplacement. Les anciens
dossiers sont deplaces dans une sauvegarde horodatee. Une erreur de remplacement
restaure les dossiers deja deplaces. Mondes et comptes sont exclus du paquet.

Les mods reserves au client (Oculus et Embeddium) sont conserves
dans l'installateur et exclus du serveur dedie. TacZ Mesh Loader est aussi
installe sur le serveur car Elite X le declare comme dependance obligatoire.
Les archives TaCZ identiques
sont dedoublonnees par SHA-256 ; les autres gunpacks restent inchanges.
