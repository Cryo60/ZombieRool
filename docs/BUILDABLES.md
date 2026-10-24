# Buildables : établi et support de pièce

Le [bouclier, la turbine et les verrous du gardien](PORTABLE_EQUIPMENT.md) utilisent ce système d’établi et de supports de pièces.

## Créer un objet

1. Posez un **Établi**, puis faites un **clic droit** dessus en créatif.
2. Cliquez sur **Nouvel objet**, donnez un nom, puis **Choisir l’objet obtenu**. Le sélecteur propose votre inventaire (avec les données des armes personnalisées) ou tous les objets avec une recherche.
3. Cliquez sur **Enregistrer et continuer**, puis **Ajouter une pièce**. Choisissez l’objet à ramasser : un **support déjà configuré** arrive dans votre inventaire et le menu se ferme. Posez-le sur la carte : la pose s’enregistre automatiquement. Rien d’autre à sauvegarder.
4. Recommencez pour les autres composants. La liste se remplit lorsque les supports sont posés.
5. Pour un autre emplacement possible, cliquez sur l’icône de la pièce dans l’éditeur : vous recevez le même support à poser ailleurs. Un seul emplacement sera choisi par partie.

Un clic droit sur un support existant permet de changer la pièce ou son objet à construire. Le nom de pièce est automatique ; modifiez-le seulement si deux composants distincts doivent utiliser le même objet. **Ajouter une pièce** sauvegarde d’abord les changements de la recette. Le bouton **Enregistrer la recette** reste disponible pour sauvegarder un changement de nom ou de résultat sans ajouter de pièce. Sur un support existant, **Enregistrer et fermer** applique les changements puis ferme le menu.

Posez les supports sur le sol ou sur une face de mur : le modèle suit cette orientation. Les objets plats sont couchés au sol. Les composants de type bloc utilisent leur modèle complet à environ 80 % de la taille d’un bloc, sans la réduction appliquée aux miniatures d’inventaire.

La recette récupère automatiquement sa liste de pièces à partir des supports. Aucun identifiant de recette, liste séparée par virgules, coordonnées ou durée n’est à saisir. Les options avancées concernent uniquement le résultat de la recette : objet, bloc fixe, événement Lua, et limite d’exemplaires.

**Tous les établis sont libres.** Il n’existe aucun réglage permettant d’affecter un établi à une recette. La première pièce installée détermine la construction en cours, comme sur Buried. Une recette commencée sur une table doit y être terminée.

## Pendant la partie

- **F** (ou votre touche d’action) ramasse une pièce uniquement si le viseur touche son modèle et si elle est à moins de 2,25 blocs des yeux du joueur. Viser un faux composant voisin ne ramasse pas une vraie pièce à côté.
- Si vous en portez déjà une, **F échange les deux pièces sur place** : l’ancienne reste à l’emplacement de celle que vous venez de ramasser.
- Les pièces ne montrent aucune indication de ramassage. **F** reste actif lorsqu’on vise une pièce à portée.
- **Tab** regroupe les pièces par recette au-dessus du tableau des joueurs. Les météorites ne figurent pas dans cette liste de craft. Un slot est grisé tant que la pièce n’est pas installée, puis dégrisé lorsqu’elle est ajoutée à l’établi. La pièce transportée par le joueur a un **cadre doré**, sans ajouter de légende ou de statuts de coéquipiers. Aucun statut de coéquipier ni légende n’est affiché. La molette fait défiler les pages si nécessaire.
- **Maintenir F sur un établi** effectue l’assemblage. Relâcher la touche annule immédiatement sans consommer la pièce. Le geste du Pack-a-Punch est conservé. La progression est affichée par une barre centrale, sans secondes ni compte à rebours.
- Un encart compact **en haut à droite** montre l’icône et le nom de la pièce transportée, ou de l’objet prêt à installer. Il est masqué pendant Tab et les menus.
- Les contributions sont partagées entre joueurs. S’éloigner, perdre la ligne de vue ou tomber interrompt l’assemblage sans consommer la pièce.
- Une fois terminé, le résultat apparaît **posé sur la table**, à la place des composants. **F** permet de le prendre. Son modèle reste affiché après récupération, même lorsque la limite est atteinte. Cette présence visuelle ne donne pas de copie supplémentaire.
- Mort, déconnexion et changement de dimension rendent la pièce à son support d’origine. Les échanges successifs ne font perdre aucune pièce. La fin de partie restaure supports, objets fixes et progression.

## Construire une machine puis la transporter

1. Créez la recette et choisissez un **bloc** comme résultat : interrupteur, contrôleur de piège, tourelle, Pack-a-Punch ou autre bloc.
2. Sur cette recette, cliquez sur **Obtenir le repère de pose**. La recette est sauvegardée en mode `deploy`, puis le menu se ferme et un **repère cyan configuré** arrive dans l’inventaire. Il s’agit d’un outil de placement, pas d’une pièce à ramasser.
3. Posez le repère à l’endroit prévu, au sol ou sur un mur. En créatif hors partie, un **aperçu fantôme translucide de la vraie machine** montre son emplacement et son orientation. En survie et pendant la partie, le fantôme reste caché tant que le joueur ne transporte pas le résultat correspondant. Lorsqu’il est **prêt à installer**, les emplacements compatibles montrent le vrai bloc en cyan translucide avec une surbrillance clignotante. L’emplacement est sauvegardé dans la carte.
4. En partie, assemblez les pièces puis récupérez le résultat à l’établi avec **F**. Le joueur transporte l’objet fini ; il ne reçoit pas un bloc librement posable.
5. Rejoignez le repère et maintenez **F** : une animation et un son accompagnent la barre de progression **Installation**. La vraie machine apparaît uniquement à la fin du maintien. Relâcher F annule la pose sans consommer le résultat. S’éloigner ou perdre la ligne de vue annule la pose sans perdre l’objet transporté. La pause fige sa progression. Les données `BlockEntityTag` du résultat et les propriétés `BlockStateTag` sont conservées. Pour conserver une configuration existante, copiez le bloc avec ses données en créatif, puis choisissez cet exemplaire dans **Mon inventaire**.

Un joueur ne transporte qu’une pièce ou un objet terminé à la fois. Une perte, mort, déconnexion ou sortie de dimension rend l’exemplaire disponible à l’établi. Une installation réussie consomme l’exemplaire autorisé. La fin de partie restaure le repère et la configuration d’origine. Le mode `block` reste disponible pour les anciennes recettes qui plaçaient directement le résultat au-dessus de la table.

Les blocs Minecraft simples sont aussi compatibles avec le mode `deploy`, notamment un **bloc de redstone**, un levier ou un piston. La pose produit le vrai bloc, avec son comportement normal et ses signaux de redstone, après la même animation.

## Faux composants décoratifs

Le bloc **Faux composant / Decoy Component** (`zombierool:decorative_component`) affiche le même modèle d’objet et la même orientation qu’un support de pièce. En créatif hors partie, faites un **clic droit avec l’objet voulu en main** pour choisir son apparence. **Maj + clic droit** efface l’apparence. La configuration est sauvegardée avec le bloc. En partie, c’est uniquement un décor : F ne donne aucun composant et il ne participe à aucune recette.

## Courant et constructions

Un power switch prévu comme résultat à installer est pris en compte avant sa construction : les pièges et tourelles qui dépendent du courant ne profitent plus du mode « carte sans courant » en attendant sa pose. Un piège configuré sans courant conserve son indépendance. Les modes explicites de source globale, redstone locale, l’une ou les deux sources restent prioritaires. La restauration du repère retire l’interrupteur temporaire du registre du courant.

La sélection du power switch est une plaque continue sur sa face de fixation, au mur, au sol ou au plafond. La pose suit la face cliquée.

## Modèles

L’établi est un bloc Minecraft de deux cases, avec un plateau de pin, quatre pieds, les traverses basses et les rails du modèle `p6_zm_work_bench` de TranZit. La géométrie native et sa texture ont été recréées d’après le modèle local extrait pour référence. Le support est un vrai bloc à données persistantes qui affiche le modèle de son objet, y compris ses données NBT et ses modèles personnalisés.

Les supports alternatifs sont choisis par groupe unique au lancement de la partie. Ils sont ensuite rendus aux mêmes emplacements en fin de partie. Les blocs et leurs données restent sauvegardés dans la carte, sans script nécessaire.

## Lua et compatibilité

Les callbacks `OnBuildablePartPickedUp`, `OnBuildablePartDropped`, `OnBuildablePartInstalled`, `OnBuildableCompleted` et `OnBuildableClaimed` restent disponibles. `OnBuildablePlaced(uuid, recipe, x, y, z)` est déclenché après une installation. `bindBuildableDestination(recipe,x,y,z)` lie un repère existant et `getCarriedBuildableResult(uuid)` consulte le résultat transporté. Les méthodes de consultation et `dropBuildablePart(uuid)` sont conservées. L’aide API intégrée décrit les signatures.

Les anciennes recettes et associations restent lisibles. L’argument de recette de `bindBuildableBench(recipe,x,y,z)` est désormais ignoré : l’établi est toujours libre. Les appels de définition Lua se font au chargement du script, avant `OnGameStart` ; les réglages ne changent pas pendant une partie active.

Les armes et équipements passent par les fonctions d’attribution existantes du mod. Un résultat `block` place son bloc au-dessus de la table lors de la récupération, puis le restaure à la fin de partie. Un résultat `event` déclenche le callback de récupération ; choisissez une limite de 1 pour une activation unique.

La map de démonstration mise à jour utilise les vrais blocs d’établi et de support : [Les Ateliers](LES_ATELIERS.md).
