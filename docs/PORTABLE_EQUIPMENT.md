# Équipements secondaires et verrou du gardien

Les recettes de carte et la bibliothèque de presets sont distinctes. **Zombie Shield**, **Turbine** et **Sentry Turret** sont des presets anglais à sélectionner explicitement ; aucune recette n’est ajoutée automatiquement aux buildables. Les huit pièces sont visibles dans la catégorie **Composants** de l’inventaire créatif. Les modèles se couchent à plat au sol ou au plafond et gardent leur orientation verticale sur les murs. Les decoys masquent leur socle lorsqu’un objet leur est assigné. Le Component Support utilise l’icône visible de l’Installation Marker. La suppression d’une recette retire ses composants et markers associés. Le total de copies se règle directement dans un champ numérique.

## Emplacement commun et commandes

Les tenues (lunaire, plongée, hazmat) partagent un emplacement dédié, indépendant des objets portables. **J** équipe ou retire la tenue ; **H** prend en main ou range le bouclier, la turbine ou la tourelle. Ces touches sont reconfigurables. Une tenue et un objet portable peuvent être conservés ensemble. L’animation de changement de tenue range temporairement l’objet portable en main sans le perdre. Deux icônes et deux rappels de touches sont affichés dans le HUD.

Le bouclier arrive dans le dos. La turbine en main affiche un aperçu : clic gauche ou droit pour confirmer la pose, **H** pour la ranger. La pose est autorisée en mode Aventure, utilisé par les parties. Client et serveur utilisent le même rayon de collision, qui ignore les Path Blocks invisibles. Un sol solide et deux blocs libres ou techniques remplaçables sont nécessaires. Le Path Block remplacé est sauvegardé avec l’appareil et restauré à sa récupération, destruction ou expiration ; les markers au-dessus sont conservés.

Regarder son appareil posé à moins de trois blocs affiche une invite : **F** ou clic droit pour le récupérer avec son usure. Le ciblage inclut sa partie haute, même avec une arme TacZ en main. La récupération remet l’objet dans l’emplacement secondaire. Un seul appareil peut être porté ou déployé par joueur ; un coéquipier ne peut pas récupérer l’exemplaire d’un autre.

## Zombie Shield

- Rangé, il protège des attaques de zombies venant du dos ; en main, il protège devant. Son modèle élargi couvre le torse et dépasse les épaules. Le rendu dans le dos est indépendant des couches de joueur, y compris pendant les animations TacZ.
- Les zombies ont une collision avec les joueurs pour éviter qu’ils les traversent.
- Attaque normale ou touche de mêlée : coup à trois blocs avec ligne de vue et délai de 15 ticks. Les zombies ordinaires meurent en un coup jusqu’à la manche 20 ; ensuite le coup inflige 20 dégâts. Les boss et ennemis immunisés aux éliminations instantanées reçoivent 20 dégâts.
- Les éliminations ne donnent aucun point ; un coup non létal réussi donne 10 points.
- Maj + clic droit sur une surface pose le bouclier comme obstacle. La récupération conserve son usure.
- Durabilité : 60 coups bloqués ou coups de bouclier réussis. Les explosions l’endommagent aussi. Aucune barre numérique n’est affichée.
- Les armes ZombieRool et TacZ sont bloquées lorsque le bouclier est en main.
- Un coup bloqué déclenche une petite secousse du modèle et de la caméra. Les quatre impacts et le son de casse viennent des alias originaux TranZit de T6. Provenance et empreintes : `assets-source/26.7/audio/shield-manifest.json` ; extraction : `tools/prepare-shield-audio.py`.

## Turbine

- Le modèle déployé fait deux blocs de haut, hélice comprise. Après chaque pose, il démarre pendant **50 ticks (2,5 secondes)** sans alimenter les machines. Le témoin clignote en orange pendant le démarrage, puis devient vert ; des particules et un son confirment que l’alimentation est prête.
- Elle fournit un signal de puissance 15 aux voisins et alimente les appareils visibles dans un rayon horizontal de trois blocs, du pied moins un bloc au pied plus trois blocs. Un mur solide interrompt cette alimentation.
- Une porte simple ou les deux battants d’une double porte s’ouvrent à portée et se referment à la perte d’alimentation. Un verrou du gardien reste prioritaire.
- Machines à atouts, Pack-a-Punch et dispositifs utilisant le courant local bénéficient de cette alimentation. Les réglages explicites sont respectés : une machine en mode **Global** demande toujours le courant général ; le mode **Both** demande les deux sources.
- Les **Buyable Doors** restent indépendantes de la turbine : leur achat utilise uniquement le prix et les règles habituelles. Aucun réglage de courant ne figure dans leur configuration.
- Les tourelles et pièges configurés pour une source locale utilisent la même alimentation. La turbine n’active pas le courant général.
- L’autonomie maximale est de 6 000 ticks actifs, soit cinq minutes à 20 ticks/s. La pause et les chunks déchargés ne simulent pas l’usure.
- Les ennemis proches peuvent prendre la turbine pour cible et la détruire. Cinq attaques au maximum détruisent un exemplaire neuf ; l’usure préalable réduit cette résistance. Les explosions vanilla et ZombieRool l’endommagent aussi.
- La récupération conserve l’alimentation pendant **30 ticks (1,5 seconde)** avant extinction des machines. Une turbine récupérée avant la fin du démarrage ne laisse aucune alimentation résiduelle.
- Chaque véritable dégât secoue le modèle entier pendant dix ticks et produit un retour sonore et des particules. L’usure normale ne déclenche pas cette animation. La destruction coupe immédiatement l’alimentation, libère l’emplacement du propriétaire et restaure le Path Block initial.

Le bus de TranZit, l’Avogadro et les grenades EMP ne sont pas présents dans ce projet. Une carte peut simuler une impulsion avec `zr:disableTurbines(x, y, z, rayon)`, rayon maximal 64, pour détruire les turbines chargées à portée. Une porte secrète de Pack-a-Punch peut utiliser les circuits locaux.

## Clé et verrou du gardien

Le verrou se pose sur une porte normale ou une Buyable Door et protège ses interactions et son achat. Un verrou couvre les deux battants d’une double porte correctement appairée, leurs moitiés hautes et tout groupe d’obstacles contigus. Il garde la texture copiée de la porte. Son modèle en métal sombre rejoint la surface réelle de la porte.

Dans **Map config → Bosses → Loot**, le map maker choisit les boss qui lâchent la clé. Brutus possède cette option par défaut, les autres boss non. La clé apparaît seulement s’il reste un verrou fermé et si l’équipe ne l’a pas encore obtenue. Les emplacements de butin classiques acceptent aussi l’objet clé.

Le butin reste au sol : le regarder à moins de trois blocs et appuyer sur **F** pour le prendre. Il peut disparaître au bout de cinq minutes ; le prochain boss admissible peut le lâcher de nouveau tant que l’équipe ne le possède pas. Le ramassage accorde un droit permanent à toute l’équipe, y compris aux arrivants ultérieurs et dans les autres dimensions. La clé ne dépend pas de l’inventaire et n’est pas perdue en mourant. **Tab** affiche la clé débloquée.

Avec la clé, regarder le verrou ou sa porte à moins de trois blocs et appuyer sur **F** pour déverrouiller. L’invite affiche la touche actuelle ou demande de trouver la clé. Le cadenas se détache, tombe puis disparaît après 30 ticks, y compris sur double porte. La clé n’est pas consommée ; l’interaction suivante ouvre ou achète normalement la porte. Le déverrouillage ne paie pas son prix. Une nouvelle partie retire le droit d’équipe et rétablit les verrous.

Le client connaît les portes verrouillées pour empêcher une ouverture temporaire sous prédiction. La synchronisation des verrous enregistrés est différée après chargement des chunks, sans redemander un chunk dans son propre événement de chargement. Lua : `zr:hasWardenKey()`.

## Vérifications et extension

`tools/tests/run-portable-equipment-tests.py` compile les sources courantes et démarre un serveur Forge isolé : pose en Aventure sur Path Blocks, récupération, restauration, alimentation, double porte, buyable door, vraie machine à atouts, destruction par zombie, durabilité, sons et verrous. `--buildables` exécute les régressions du système de construction.

`tools/tests/render-secondary-equipment.py` vérifie le client réel avec TacZ sur une copie isolée de **ZombieRool - Les Ateliers v2**, dont les commandes physiques de pose et récupération et le rendu des composants. Journaux et captures dans `build/secondary-visuals`. La sauvegarde d’origine n’est pas modifiée.

Voir [BUILDABLE_ADDONS.md](BUILDABLE_ADDONS.md) pour les datapacks, tags, API Java, poses de composants et événements Lua.

Les classes, paquets réseau, outils et documentation des équipements utilisent des noms fonctionnels : `PortableEquipment`, `PortableEquipmentPackets`, `PortableEquipmentClient`. Les anciens identifiants de données et de bloc-entité sont reconnus pour migrer les sauvegardes existantes.


## Sentry Turret

Dans l’établi, ouvrir **Bibliothèque de presets**, puis choisir **Sentry Turret**. Le nombre de presets visibles s’adapte à la hauteur de l’écran ; les trois presets intégrés tiennent sur la même page à la taille habituelle. Cela crée une recette modifiable avec trois composants : **Turret tripod**, **Turret receiver**, **Ammunition box**. Ils sont visibles dans la catégorie Composants ; la tourelle est dans les objets constructibles. Aucune recette de carte n’est créée automatiquement.

En créatif, prendre son objet dans l’inventaire ne l’équipe pas automatiquement. Même touche d’équipement et aperçu que la turbine. Clic gauche ou droit pour poser, interaction F ou clic droit à moins de trois blocs pour récupérer. Le propriétaire conserve son usure à la récupération. Elle ne nécessite pas de turbine ni de courant et ne produit aucune alimentation.

Elle cible l’ennemi visible le plus proche à 12 blocs maximum, un tir toutes les 4 ticks (cinq tirs/seconde), pour 6 dégâts fixes, sans croissance avec les manches. Aucun tir à travers un mur ou sur un joueur. Sa durabilité est de 150 : quinze attaques de mêlée de 10 dégâts détruisent un exemplaire neuf. Elle est traversable. Chaque attaque reçue produit une secousse du socle et de la tête, un flash rouge de 12 ticks et cinq étincelles. Les ennemis proches cherchent un chemin vers elle et doivent réellement la frapper pour lui infliger des dégâts ; leur simple proximité ne cause aucune usure. La tourelle détruite cesse immédiatement de tirer, baisse la tête pendant une seconde, puis disparaît et restitue le bloc initial. Elle ne peut plus être récupérée pendant sa destruction.

Modèle : [Matt K., Sentry Gun, 3D Warehouse](https://3dwarehouse.sketchup.com/model/971a2c475e41acf3b59380235edde7a4). Géométrie originale simplifiée à 42 004 faces avec les matériaux de référence et des surfaces Minecraft pour les parties non texturées. Les canons, le mécanisme, le trépied, les panneaux et la boîte de munitions conservent leurs détails visibles. Les bords ouverts des pièces sont protégés pendant la simplification ; les UV sont reconstruits sur les surfaces de référence. Générateur : `tools/prepare-stylized-sentry.py`. Le rendu conserve les deux faces des feuilles de surface du modèle source pour éviter les pans manquants selon l’angle. La tête est séparée du trépied pour le ciblage et l’animation de destruction.

Lua : `zr:givePortableEquipment(uuid, "sentry_turret")` et `zr:getPortableEquipment(uuid)` ; les mêmes fonctions acceptent `turbine` et `zombie_shield`. Les presets, composants et recettes utilisent les API Buildables habituelles. L’événement `OnPortableTurretShot(x, y, z, targetUuid, ownerUuid, damage)` est émis après un tir.

Le wall weapon utilise la silhouette projetée du modèle 3D des équipements portables, plutôt que leur texture de particule (qui est un carré de matériau).
