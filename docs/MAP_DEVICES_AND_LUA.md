# Blocs de carte et API Lua

## Équilibrage des dégâts

La vie de base du mod est de 6 points, soit 3 cœurs. En partie, les dégâts finaux de santé sont normalisés après difficulté/armure ; les protections qui annulent un coup restent respectées.

- Zombie normal : 2 points = 1 cœur.
- Zombie rampant, Nova Crawler et hellhound : 1 point = ½ cœur.
- Auto-explosion : 1/6 de la vie maximale, soit ½ cœur avec la vie de base. PHD conserve son immunité. Les explosions simultanées ont une protection de 10 ticks contre le cumul.
- Feu, noyade, chute, gel, cactus, baies, faim, magie, wither : 1 point par événement de dégâts.
- Lave, foudre, enclume, blocs qui tombent et stalagmites : 2 points par événement.
- Les dégâts spéciaux non listés conservent leur comportement ; le vide reste mortel. La fréquence propre à chaque source reste celle du jeu.
- Piège électrique/feu : contact immédiat, puis au maximum 1 point par seconde, même avec plusieurs pièges superposés. Le propriétaire est aussi exposé.

## Pièges et émetteurs

Les six blocs sont disponibles dans l’onglet créatif ZombieRool. Clic droit en créatif : configuration. En partie : activation payante avec la touche d’interaction F, remappable dans les options.

- `zombierool:sound_emitter` : volume, tonalité/vitesse, portée, intervalle et identifiant du son.
- `zombierool:particle_emitter` : quantité, dispersion, vitesse, portée visible, intervalle et identifiant/paramètres de particules.
- `zombierool:trap_controller` : boîtier mural, canal, prix, durée, recharge, besoin de courant.
- `zombierool:electric_trap` et `zombierool:fire_pit` : placer au sol ou plafond, saisir le même canal que le boîtier. Ils émettent vers la face choisie à la pose.
- `zombierool:turret` : autonome ou reliée à un boîtier. Réévalue les cibles vivantes à chaque tir, respecte les obstacles et utilise `zombierool:mg42_fire`.

Par défaut : prix 1 000, durée 600 ticks (30 s), recharge 1 200 ticks (60 s). Tourelle : 20 dégâts tous les 5 ticks, portée 16. Électricité/feu : 100 000 dégâts dès le premier tick de contact, puis tous les 10 ticks par cible, portée 3, ralentissement de 90 %. Ces valeurs sont configurables ; les dégâts colossaux ne sont pas un effet de mort instantanée illimité.

Le propriétaire reçoit 50 % des points normalement attribués par les coups et morts du piège, avec les bonus de points existants. Aucun crédit si ce propriétaire est déconnecté. Un canal déjà actif ou en recharge ne peut pas être racheté.

Le courant est requis par défaut si la map déclare des interrupteurs. Sans interrupteur, les pièges fonctionnent. Le créateur peut désactiver cette exigence sur le boîtier ou la tourelle autonome ; les éléments liés suivent le boîtier.

Les émetteurs sont invisibles et sans collision en survie. Leurs impulsions automatiques et les pièges fonctionnent pendant la partie. Le bouton de prévisualisation et Lua peuvent déclencher un émetteur hors partie. Les configurations persistent ; les activations payées et recharges sont remises à zéro au restart et ne sont pas sauvegardées entre chargements.

Les textures des nouveaux blocs sont en 32 × 32. Les noms sont traduits dans les six langues présentes ; les menus sont en français/anglais, avec repli anglais pour les autres langues.

## Ressources propres au monde

Le navigateur propose Vanilla, Mods et Personnalisé, avec recherche. Les identifiants des mods proviennent de leurs registres : le mod correspondant doit être présent chez le serveur et les clients.

Sons OGG : `saves/<monde>/zombierool/audio/custom/`. Exemple : `alarms/red.ogg` devient `zombierool:custom/alarms/red`. Ils utilisent la synchronisation audio du monde vers les joueurs. Préférer des noms minuscules ASCII. Recharger les ressources du monde après ajout.

Particules : `saves/<monde>/zombierool/particles/custom/`. Un fichier `red_dust.json` contenant `{"particle":"minecraft:dust 0.8 0.05 0.02 1"}` donne `zombierool:custom/red_dust`. Un preset référence un type existant, vanilla ou mod ; ce n’est pas un chargement de code de particule inédit. Les paramètres acceptés sont ceux de `/particle`, également saisissables directement dans le champ effet.

La copie du monde transporte les configurations et les dossiers personnalisés. Seuls les appareils dans des chunks chargés fonctionnent et apparaissent dans la liste Lua.

## Nouveautés Lua / API

Objet existant : `ZombieroolAPI`. Positions et listes sont résolues dans sa dimension. Les durées sont en ticks (20 = 1 seconde).

- `placeMapDevice(x,y,z,kind,facing)` → booléen. `kind` : SOUND, PARTICLE, CONTROL, ELECTRIC, FIRE, TURRET. `facing` : north, south, east, west, up, down. Remplace le bloc à la position donnée.
- `getMapDevice(x,y,z)` → table de configuration et `kind`, `active_ticks`, `cooldown_ticks`, `owner`, `error` ; table vide si absent.
- `configureMapDevice(x,y,z,config)` → booléen. Mise à jour partielle, valeurs bornées côté serveur ; arrête une activation en cours.
- `listMapDevices()` → tableau des appareils chargés, incluant x/y/z et les informations ci-dessus.
- `listEmitterEffects("sound"|"particle")` → tableau trié des identifiants vanilla, mods et presets personnalisés.
- `setEmitterEnabled(x,y,z,enabled)` → booléen.
- `pulseEmitter(x,y,z)` → booléen ; émission immédiate, y compris hors partie. `getMapDevice().error` détaille un échec.
- `activateTrap(x,y,z,playerUUID,charge)` → chaîne vide si succès, sinon clé de traduction d’erreur. `charge=false` ignore uniquement le prix ; partie, joueur vivant, courant et recharge restent vérifiés.
- `stopTrap(x,y,z)` → booléen ; déclenche la recharge si actif.
- `resetTrap(x,y,z)` → booléen ; arrête et enlève la recharge.

Champs de configuration : `enabled`, `requires_power`, `channel`, `effect`, `volume`, `pitch`, `range`, `interval`, `count`, `spread`, `speed`, `cost`, `duration`, `recharge`, `damage`. Les champs inutiles au type sont ignorés par son fonctionnement.

Nouveaux événements globaux :

```lua
function OnTrapActivated(x,y,z,channel,ownerUUID,durationTicks) end
function OnTrapStopped(x,y,z,channel) end
function OnTrapReady(x,y,z,channel) end
function OnTrapHit(x,y,z,targetUUID,ownerUUID,configuredDamage,killed) end
function OnEmitterPulse(x,y,z,kind,effectID) end
```

`OnTrapHit` fournit les dégâts configurés, pas un relevé des dégâts après les protections de la cible. `OnTrapReady` correspond à la fin normale d’une recharge. Une remise à zéro générale ne simule pas tous les événements de fin.

Exemple à adapter aux coordonnées de la map :

```lua
function OnGameStart()
    ZombieroolAPI:configureMapDevice(10,64,10, {
        channel="corridor", cost=750, duration=600,
        recharge=1200, requires_power=true
    })
    ZombieroolAPI:configureMapDevice(12,64,10, {
        channel="corridor", range=3, damage=100000
    })
    ZombieroolAPI:configureMapDevice(9,65,10, {
        effect="zombierool:custom/alarms/red", volume=0.8,
        range=24, interval=100, enabled=false
    })
end
function OnTrapActivated(x,y,z,channel,ownerUUID,durationTicks)
    if channel=="corridor" then
        ZombieroolAPI:pulseEmitter(9,65,10)
    end
end
```

## Armes faibles

Uniquement **M1911** (`m1911`) et **Mauser C96** (`mauserc96`, alias `mauser_c96` / `c96`). Les autres armes à feu sont fortes par défaut, notamment les snipers, fusils à verrou, fusils d’assaut et mitrailleuses. Le `browningm1911` existant (100 coups, 9 dégâts) reste fort. Les armes de mêlée font saigner sans bénéficier de la règle de démembrement des armes à feu. Les explosions conservent leur effet gore propre.

## Régressions à vérifier en jeu

- Acheter une arme TacZ au mur, la recharger avec/sans Speed Cola ; vérifier animation, durée et sons. Le crash 10:51 désignait la classe synthétique Mixin `Args$1` ; l’injection audio utilise maintenant `ModifyArg`.
- Acheter une augmentation compatible : installation directe, une seule consommation, ancien accessoire rendu. Accessoire incompatible : inventaire normal.
- Ouvrir un coffre pendant une partie, tenter d’y déposer une arme de map, jeter une arme avec Vulture Aid : aucun doublon ni dépôt interdit.
- Modifier un Defense Wall puis recharger la map : texture visible avant perte de stage.
- Tuer zombie, crawler et hellhound ; vérifier yeux éteints, poses du crawler, petits saignements du hellhound sans démembrement. Restart : plus de cadavres ni flaques.
- Escalier avec détour et passages de 1/2 blocs : poursuite sans visibilité directe, sans franchir les limites de cheminement de la map. Une taille personnalisée supérieure à la normale peut encore empêcher un crawler de passer.
- Tester les trois pièges avec/sans courant, fonds insuffisants, expiration, recharge, propriétaire exposé et deux joueurs. Vérifier le crédit de moitié.
- Copier le monde avec audio/presets vers une autre installation équipée des mêmes mods, tester les effets et la prévisualisation.

Les tests de compilation ne remplacent pas ces vérifications visuelles et multijoueur.

## Correctifs après retour en jeu

- HUD des pièges blanc, format `Press [<key>] to buy <X> [Cost: <cost>]` ; état actif, recharge avec secondes, courant absent et désactivation synchronisés.
- Contacts détectés chaque tick ; le délai entre dégâts reste propre à chaque cible.
- Nova Crawlers : 25 % de gaz sur les morts ordinaires hors mêlée/headshot ; une mort explosive déclenche toujours le gaz. Corps conservé après libération du gaz, sans répétition ; les impacts des premières 10 ticks ne font pas exploser immédiatement le cadavre.
- Grenade ZombieRool : application explicite au propriétaire dans le rayon ; la régénération est uniquement serveur et attend de nouveau 5 secondes après des dégâts de santé. Le soin naturel de Minecraft ne se cumule plus avec elle lorsque la faim est désactivée.
- TacZ : les pistes complètes de rechargement vide et tactique passent maintenant par le multiplicateur de Speed Cola, en plus des sons des animations.

### Defense Walls des maps existantes

Le modèle reconstruit ses données directement depuis le camouflage sauvegardé du bloc principal, même si une partie secondaire ne possède pas encore de données de rendu en cache. Le chargement d’un chunk rafraîchit également les murs des chunks voisins déjà chargés. Aucun changement de stage ni remplacement des murs existants n’est nécessaire pour déclencher cette lecture. Compilation vérifiée ; rendu initial à confirmer dans la map concernée.

## Texture Kit, porte vitrée et sons (1.6.5)

Le Texture Kit réserve ses sprites 32×32 au démarrage. Modifier ou importer une texture remplace uniquement les pixels correspondants, sans reload global ni relecture des gunpacks. L'éditeur propose pinceau, gomme, remplissage, pipette, taille 1 à 8, annulation, couleur RGBA, création par nom et résolution 16/32. Il fonctionne dans le monde local (serveur intégré), comme l'ancien chargement de textures. Les PNG et manifestes voyagent avec le dossier du monde.

Importer un PNG depuis `zombierool/custom_blocks/import/` : face carrée, patron cube 4×3, bande de six faces (nord, sud, est, ouest, dessus, dessous), ou porte 1×2 (haut puis bas). Le patron 4×3 utilise dessus en (1,0), ouest/nord/est/sud sur la ligne 1, dessous en (1,2). Les faces sont converties à la résolution choisie. Les portes Texture Kit utilisent le fonctionnement des portes en fer (redstone).

`zombierool:glass_defense_door` commence au stage 7. Les coups et tirs descendent la vitre jusqu'à 0 ; ensuite F reconstruit de 1 à 5 planches. Carpenter intervient uniquement après une brèche complète. Le journal et la réinitialisation des portes chargées remettent la vitre au stage 7 au restart ou à la fin de partie.

API Lua supplémentaire :
- `placeGlassDefenseDoor(x,y,z,facing)` : place les deux moitiés (`north/east/south/west`).
- `getGlassDefenseDoor(x,y,z)` : table `{stage, breached}`.
- `damageGlassDefenseDoor(x,y,z)` : retire un stage.
- `repairGlassDefenseDoor(x,y,z,planks)` : règle 0–5 planches uniquement après la brèche.
- Événements `OnGlassDoorDamaged(x,y,z,stage,breached)` et `OnGlassDoorPlanksChanged(x,y,z,stage)`.

Les tirs Pack-a-Punch superposent le son normal et le son amélioré. TacZ utilise aussi cette superposition et un buffer de glint sur le modèle. Les quatre WAV du Bureau deviennent l'événement `zombierool:zombie_step`, avec variation aléatoire.


### Correctif de la 1.6.5 remplacée

Les textures du Texture Kit sont préparées côté serveur (patron cube 4×3 ou bande 6 faces, porte 1×2, face carrée) puis envoyées aux joueurs, sans téléchargement préalable de la map ni reload des ressources. Un joueur qui rejoint reçoit les 32 slots ; les joueurs présents reçoivent uniquement les slots modifiés. Le bouton Actualiser permet au créateur de diffuser un changement de fichier. Les sources originales en atlas restent intactes. L'éditeur affiche désormais une face extraite, et conserve les autres faces lors de l'enregistrement.

La porte vitrée est incluse dans `allowed_blocks`, sa collision laisse passer au stage 0 et arrête les projectiles pendant les stages pleins. Le couteau et les tirs peuvent casser les stages. Les destructions de planches jouent les sons existants `wood_snap_00` à `wood_snap_05`. La zone de vitre reçoit un masque alpha sur ses sprites (cadre préservé), avec rendu translucide.

Le protocole réseau passe à 3 : tous les joueurs doivent utiliser le JAR 1.6.5 remplacé.

## API 26.7 : objectifs, navigation et équipements

Les méthodes précédentes restent disponibles. Les nouvelles configurations sont des **patchs** : les champs omis gardent leur valeur. Les clés correspondent à celles des menus (`requiresPower`, `chewTime`, etc.). Les ID doivent être uniques ; un ID absent ou dupliqué ne choisit jamais un bloc arbitraire.

- `listSoulObjectives()` : liste des ID, Soul Chests et Cerberus ensemble.
- `getSoulObjective(id)` : configuration, `kind`, `collected`, `completed`, coordonnées ; Cerberus ajoute `phase` (0 idle, 1 attraction, 2 repas, 3 terminé) et `revealed`.
- `isSoulObjectiveComplete(id)` / `areSoulObjectivesComplete("S1,C1,C2")` : état, y compris les objectifs déchargés. Une liste vide ne valide jamais.
- `configureSoulObjective(id, settings)` / `resetSoulObjective(id)` : patch ou remise à zéro d'un objectif précis. Comme le menu du bloc, changer sa configuration remet sa progression à zéro.
- `listTeleporters()` / `getTeleporter(id)` : configuration et `linked`, `busy`, `powered`, `cooldownTicks`, `readiness` (clé de message, vide si prêt).
- `configureTeleporter(id, settings)` : patch, y compris sur un téléporteur désactivé. Les options partagées sont synchronisées avec ses destinations liées. Refus pendant un trajet.
- `resetTeleporterCooldown(id)` : recharge à zéro, refus pendant un trajet.
- `activateTeleporter(id, playerUUID)` : interaction normale, avec courant, liaison, paiement et charge visuelle. Renvoie une clé de message, ou une chaîne vide si l'interaction est acceptée.
- `setTeleporterCondition(channel, condition, active)` / `getTeleporterCondition(channel, condition)` : conditions du réseau ; elles repartent à zéro à chaque partie.
- `getNavigationLink(x,y,z)` / `configureNavigationLink(x,y,z,settings)` : Jump, Traversal, Crawler Wall et Zipline ; un patch s'applique aux parties liées comme dans le menu.
- `setSpawnerChannels(enable, disable, durationSeconds)` : listes de canaux séparés par des virgules ; `0` conserve le changement jusqu'au reset de partie.
- `getEquipmentLoadout(playerUUID)` : `equipment`, `equipmentCharges`, `tactical`, `tacticalCharges`, `lethal`, `lethalCharges`.
- `equipEquipment(playerUUID, kind)` : `c4`, `claymore`, `bouncing_betty`, `flash`, `pheromone`, `semtex` ; utilise les emplacements dédiés.
- `setEquipmentCharges(playerUUID, equipment, tactical)` : nombres limités à 0–2 ; un emplacement sans équipement reste vide.
- `getEnemyStatus(entityUUID)` : `alive`, `boss`, `flashTicks`, `pheromoneTicks`, `winter_frozenTicks`.

Une requête de bloc inconnu renvoie une table vide ; une modification impossible renvoie `false`. Les requêtes ciblées d'un objectif ou téléporteur peuvent charger son chunk. Les méthodes de liste et de validation de completion utilisent le registre sans charger toute la map.

Nouveaux événements :

- `OnSoulCollected(id, collected, required, killerUUID)` : une âme arrive réellement dans le coffre.
- `OnCerberusFeeding(id, corpseUUID)` : un cadavre est accepté.
- `OnCerberusMealFinished(id, collected, required, killerUUID)` : déglutition terminée.
- `OnEquipmentUsed(playerUUID, kind, x, y, z)` : équipement réellement créé.
- `OnEnemyEquipmentEffect(enemyUUID, kind, playerUUID)` : première application de flash/phéromones, hors boss ; une simple prolongation ne répète pas cet événement.
- `OnVrilConversion(zombieUUID, humanUUID, playerUUID)` : humanification réussie.

Les événements existants `OnSoulObjectiveCompleted(id)`, `OnSoulChestCompleted(x,y,z,required)`, `OnCerberusCompleted(x,y,z,required)`, `OnTeleporterUsed(sourceId,destinationId,payerUUID)` et ceux de navigation conservent leurs arguments. Le registre est déjà mis à jour quand l'événement de completion est exécuté.

```lua
function OnSoulObjectiveCompleted(id)
    if ZombieroolAPI:areSoulObjectivesComplete("S1,C1,C2") then
        ZombieroolAPI:configureTeleporter("T1", {enabled=true})
        ZombieroolAPI:setSpawnerChannels("6", "0", 30)
    end
end
```

La carrière ajoute deux objectifs chiffrés pour chacune de dix activités : âmes collectées, cadavres mangés, objectifs terminés, voyages en téléporteur, ennemis flashés, ennemis sous phéromones, éliminations avec équipement/Semtex, ennemis gelés, humanifications et trajets en tyrolienne. Les trajets doivent être terminés, les repas entièrement avalés et les effets nouvellement appliqués. La progression est validée côté serveur pendant une partie sans cheats, pour les joueurs vivants hors créatif/spectateur, puis sauvegardée dans le profil local existant. Ces défis entrent dans le tirage et le remplacement habituels sans effacer les défis en cours.


## Alimentation par bloc et marqueurs (14 octobre)

En créatif, le bouton Alimentation du menu habituel des atouts, téléporteurs, tyroliennes et pièges ouvre le choix des sources. Les spawners proposent ce choix dans leur menu principal, sans sous-menu. Un clic droit normal en créatif ouvre le réglage du Pack-a-Punch et du Wunderfizz. Choisir `global`, `local`, `either` (OU) ou `both` (ET). `default` conserve le réglage d’alimentation existant, y compris les dispositifs sans besoin de courant. Une source explicite active ce besoin, même si l’ancien interrupteur « courant requis » était désactivé. Les choix sont sauvegardés dans la map et partagés par les parties d’un même bloc multi-partie.

En mode ET, un activator global directement adjacent ne tient pas lieu de second signal local. La redstone locale est reçue sur les six faces ; les atouts utilisent leurs trois parties et les téléporteurs leurs neuf cases. Les récepteurs, activators globaux, power switches, soul chests et soul completion checkers émettent un signal faible et fort dans les six directions. Les entrées de plusieurs activators d’un même canal sont combinées en OU.

Un clic droit normal en créatif sur un activator ou un receptor règle « Visible en survie ». Ils sont invisibles en survie par défaut, visibles en créatif et sans collision dans les deux cas.

LuaJ : `ZombieroolAPI:getBlockPowerMode(x,y,z)` retourne la source ; `ZombieroolAPI:setBlockPowerMode(x,y,z,"both")` la modifie et retourne un booléen. Valeurs : `default`, `global`, `local`, `either`, `both`.


## Console de debug et limite des atouts

Le menu Map Config, onglet Extras, permet de choisir `maxPerks` : `-1` supprime la limite, `0` interdit les atouts, une valeur positive fixe le plafond (défaut : `4`). Le réglage est enregistré dans la map, synchronisé pour les indications d’interaction et appliqué aux machines d’atouts et au Wunderfizz. Le bonus On the House conserve ses dépassements spéciaux, sauf lorsque les atouts sont interdits.

Lua utilise le même réglage : `ZombieroolAPI:configureMap({maxPerks = -1})` et `ZombieroolAPI:getMapConfig().maxPerks`.

Les commandes de la console secrète nécessitent les droits opérateur. Les actions de test marquent la partie comme utilisant des cheats. `help` affiche les commandes ; la molette parcourt les messages, les flèches haut/bas rappellent les commandes, Tab complète les commandes et les identifiants enregistrés.

- `game start`, `game stop`, `game restart`, `game pause`, `game resume` contrôlent la partie ; `status` affiche son état. `wave 2` nécessite une partie démarrée.
- `god [on/off]` annule les dégâts, y compris le vide ; `noclip [on/off]` autorise le vol sans passer en créatif ou en spectateur. Les collisions restent normales à pied ; traverser les blocs nécessite le vol (double appui sur saut). Désactiver noclip restaure les capacités précédentes et dégage le joueur d’un mur si nécessaire. `debug_reset` désactive les deux. Ces états sont temporaires et supprimés à la déconnexion.
- `heal`, `points 1000`, `points_set 1000`, `tp x y z`, `weapon id`, `pap`, `ammo` servent à tester le joueur et ses armes.
- `spawn panzer 2`, `spawn brutus`, `spawn napalm_minion`, `spawn skeleton_minion 10`, `spawn minecraft:zombie` invoquent les entités enregistrées au point visé. Maximum 50 par commande. Ce sont des entités de test, ajoutées hors du quota normal de la manche.
- `enemies` compte les ennemis du mod par type. `killall` tue aussi les mini boss et minions. `despawn [type/all]` retire les entités sans loot ; cette suppression ne termine pas artificiellement la manche.
- `perk give juggernog`, `perk remove juggernog`, `perks clear`, `perks limit -1` règlent les atouts ; `bonus max_ammo` invoque un bonus enregistré à tes pieds.
- `power on/off/reset` contrôle les signaux de la map. `power source both` règle la source du bloc visé ; modes `default/global/local/either/both`.
- `signal alpha on`, `signal omega off` testent les canaux de signal. `signal alpha auto` restitue le contrôle au circuit normal. `debug_reset`, la fin de partie et l’arrêt du serveur libèrent les forçages.
- `inspect` affiche le bloc visé, son origine, sa source d’alimentation, son signal local et ses NBT. `reload scripts` et `reload weapons` rechargent les définitions.

Les projections de sang utilisent à nouveau la texture de combat du renderer publié (`textures/particle/blood_drop.png`). Elles sont découpées sur les surfaces de collision réelles, y compris les demi-dalles, et cessent de s’afficher si leur support est retiré.


Les flaques de combat reprennent le quad tourné de la 1.6.6 hotfix1, avec une taille et une teinte variables. Leur apparence est déterminée par un identifiant sauvegardé pour rester identique entre clients. Chaque trace est découpée sur son support visible.

Les changements temporaires de canaux peuvent redistribuer les ennemis pendant un saut ou une tyrolienne. Cette redistribution libère désormais l’animation avant la sauvegarde afin de ne pas conserver son verrou temporaire sur l’IA. `tp_last` ignore les cadavres et privilégie les ennemis encore comptés dans la manche. Les recherches de chemins commencent dans une région plus petite et sont étendues si le trajet ne peut pas être complété.

Octobre 2026 : absorption vidée au début/à la fin des parties et lors des réapparitions. Le gore et les traces de sang ignorent les blocs techniques invisibles (restrict compris). Le sang reprend la texture, le quad tourné et le mélange translucide de la 1.6.6 hotfix1, avec variations de taille et de teinte et découpage sur les surfaces visibles. Les sons de présence, ramassage, annonces et détonations des bonus proviennent de leur position ; les rappels personnels des effets restent attachés au joueur.

Les jauges d'énergie et de surchauffe occupent deux lignes distinctes du HUD de combat pour les armes concernées. Le lance-flammes conserve la vitesse des zombies ordinaires qu'il brûle ; les rencontres inferno explicites restent distinctes.
Le sang fusionne par face du décor avec une couverture maximale, sans assombrissement par superposition. Le rendu intervient après les blocs mimic et n'écrit pas dans leur tampon de profondeur. Modifier le mimic invalide les traces précédentes.
Commandes : `/zombierool bonus <id|random> [x y z]`, `/zombierool bonus next <id|random> [joueurs]`, `/zombierool bonus clear_next [joueurs]`. Les coordonnées acceptent `~` et `^`. La console secrète propose `bonus <id|random> [x y z]`, `bonus_next [id|random|clear]`, `spawn <id> [nombre] [x y z]` et `tp <x> <y> <z>` avec les mêmes coordonnées relatives. La demande de bonus est personnelle et consommée par un kill ennemi crédité, même si les drops ordinaires sont désactivés. Elle se réinitialise entre parties.
Les recherches vers un joueur inaccessible ont un budget limité et des tentatives espacées. Un changement de position du joueur relance la recherche ; une attente sans trajet valide ne déclenche pas le recyclage des ennemis.
