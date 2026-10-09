# Nouveaux blocs, correctifs et API Lua — 8 octobre 2026

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

Validation : compilation Forge et annotation des mixins, contrôles des ressources et du correctif Discord, conversion réelle des atlas avec conservation des couleurs/transparence, limites des PNG et vérification du JAR. Les comportements visuels et le gameplay restent à essayer dans Minecraft.

### Correctif de la 1.6.5 remplacée

Les textures du Texture Kit sont préparées côté serveur (patron cube 4×3 ou bande 6 faces, porte 1×2, face carrée) puis envoyées aux joueurs, sans téléchargement préalable de la map ni reload des ressources. Un joueur qui rejoint reçoit les 32 slots ; les joueurs présents reçoivent uniquement les slots modifiés. Le bouton Actualiser permet au créateur de diffuser un changement de fichier. Les sources originales en atlas restent intactes. L'éditeur affiche désormais une face extraite, et conserve les autres faces lors de l'enregistrement.

La porte vitrée est incluse dans `allowed_blocks`, sa collision laisse passer au stage 0 et arrête les projectiles pendant les stages pleins. Le couteau et les tirs peuvent casser les stages. Les destructions de planches jouent les sons existants `wood_snap_00` à `wood_snap_05`. La zone de vitre reçoit un masque alpha sur ses sprites (cadre préservé), avec rendu translucide.

Le protocole réseau passe à 3 : tous les joueurs doivent utiliser le JAR 1.6.5 remplacé.
