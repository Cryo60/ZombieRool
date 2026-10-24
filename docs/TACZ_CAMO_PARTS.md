# Camos TACZ par pièce

Le revêtement conserve la texture d'origine des canons, bouches, culasses,
détentes, commandes identifiées, rails, viseurs, munitions et mécanismes.
Carcasses, crosses, garde-mains, poignées, chargeurs et glissières de pistolet
restent peints. Les lentilles, écrans et surfaces lumineuses sont protégés.
Le reflet Pack-a-Punch suit le même masque pour ne pas repeindre les pièces
exclues. Les accessoires rendus par les renderers fonctionnels TACZ restent
hors du passage de camo, notamment les accessoires Gucci Vuitton.

`TacZCamoParts` reconnaît les noms réellement rencontrés dans les packs
(dont `jimiao`, `aug_charge`, `MagLatch`, `saftyswitch`, `tirgger`,
`tactical_handgraud`). Une pièce sans nom descriptif hérite de son assemblage.
Un garde-main explicitement nommé peut rester peint sous un canon ; ses rails
restent nus. Les groupes d'animation combinant armes et mains ne sont pas
exclus. Le parcours transmet lui-même l'héritage : `BedrockPart.addChild`
ne renseigne pas `getParent()` dans le binaire TACZ contrôlé.

Les résultats sont mis en cache par pièce, puis invalidés au rechargement des
ressources. Aucun fichier de gunpack ni texture de l'auteur n'est modifié.
Les armes ZombieRool vanilla ne sont pas concernées.

## Périmètre contrôlé le 15 octobre 2026

Catalogue : https://raw.githubusercontent.com/Cryo60/zombierool-maps/refs/heads/main/dependencies.json

Inspection structurelle de 608 modèles (variantes et LOD compris), issus des
15 entrées du catalogue :

- TACZ 1.1.8-hotfix2 : 110 modèles.
- LesRaisins 0.3.0 : 87 modèles.
- Gucci Vuitton 0.2.2 : 64 modèles d'accessoires, aucun modèle d'arme.
- Fibers 1.3 : 7 modèles.
- Elite X 4.2 : 46 modèles.
- Cold War 0.51 : 17 modèles.
- Gunpowder Revolution 1.2.7 : 39 modèles.
- Halo 1.6.0 : 15 modèles.
- MW2019 (`mw2019.zip`) : 24 modèles.
- Valorant 0.1.2 hotfix 1 : 10 modèles.
- WaT 0.2.2 : 31 modèles.
- Warzone 1.1.7A Hotfix : 134 modèles.
- Enlisted 1.2.1.3 : 52 modèles.
- Modern Warfare Addon / TheWindowsGuy 2.0.0 : 31 modèles.
- BO6 1.0.2 : 5 modèles. La page CurseForge 6024155 du catalogue désigne
  cette version, malgré un nom de fichier `1.1pre5` dans le JSON.

Les liens GitHub des deux derniers packs renvoyaient 404. MW Addon 2.0.0
a été obtenu depuis le fichier CurseForge 6505302 de son auteur. La version
BO6 1.0.2 était déjà installée. Elite X 4.2 a été contrôlé séparément de la
version 6.0 installée. Cette dernière ajoute 60 modèles à l'audit, soit
668 modèles au total.

Le passage de camo prend aussi en charge les surfaces `poly_mesh` via le
TACZ Mesh Loader 0.1.8 installé, sans rendre ce mod obligatoire. Il suit les
pièces animées et les exclusions du loader, et protège ses surfaces
translucides, lumineuses et optiques. Les poignées et chargeurs restent peints ;
les munitions à l'intérieur des chargeurs restent protégées. Les alias
`handle`, `handgrip`, `pistolhandle`, `magezine`, `drummag`, `magbox` et `dmag`
complètent les noms reconnus.

## Limites et vérifications

Ce filtrage porte sur les pièces du modèle, pas sur des matériaux déduits de
la couleur. Les modèles LOD fusionnés et certains modèles aux pièces nommées
uniquement `bone`, `main`, `group` ne permettent pas une séparation complète
canon/carcasse. Ces surfaces conservent le comportement hérité ; une finition
parfaite exigerait des masques spécifiques aux surfaces ou un découpage du
modèle. L'audit liste les modèles sans séparation dans son fichier `.unmixed.txt`.
Les armes fantastiques sans mécanisme distinct peuvent aussi figurer dans cette liste.

Vérifications exécutées : compilation Java ciblée, tests des alias et de
l'héritage, parcours réel du renderer avec des pièces Bedrock, audit des
hiérarchies des archives, 774 projections pour les 84 camos et les replis.
Un test d'intégration CPU avec le vrai Mesh Loader et un maillage synthétique
vérifie les sommets peints des poignées et chargeurs, les surfaces protégées
et les exclusions du loader. L'application du nouveau mixin et le rendu
polygonal complet restent à vérifier en jeu.
Ce ne sont pas des captures de chaque arme en jeu. Validation visuelle dans
Minecraft, notamment avec shaders et modèles de distance, encore nécessaire.

Reproduction de l'inventaire (les archives restent locales et ne sont pas
redistribuées) :

```text
python tools/tests/audit-tacz-camo-packs.py <instance> <archives-et-dependencies.json> <audit.json>
# Ajouter les chemins d'archives supplémentaires après audit.json pour les inclure.
java -cp <classes-et-dependances> TacZCamoPackAudit <audit.json>
java -cp <classes-et-dependances> TacZCamoPartsTest
java -cp <classes-et-TACZ-remappe-official> TacZCamoTraversalTest
java -cp <classes-et-TACZ-et-Mesh-Loader-remappes-official> me.cryo.zombierool.client.TacZMeshCamoTest
```
