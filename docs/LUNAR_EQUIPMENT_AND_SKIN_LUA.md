# Casque lunaire et apparences en Lua

Les sources et les essais restent dans `D:/Dev/ZombieRool`. Aucune installation automatique dans CurseForge.

## Joueurs

La **station de casque lunaire** (`zombierool:lunar_helmet_station`) occupe 1 × 1 × 2 blocs. Elle est disponible dans l’onglet créatif ZombieRool. Placer sa base pose aussi sa moitié supérieure ; casser une moitié retire l’autre. **F** ou le clic droit récupère gratuitement le casque. **H**, configurable dans les touches, permet ensuite de le mettre et de le retirer sans retourner à la station.

Le casque est un équipement secondaire : il ne remplace ni l’armure de tête, ni l’équipement tactique, ni la seconde main. L’objet créatif `zombierool:lunar_helmet` permet aussi de le débloquer et de le basculer par clic droit. Le joueur conserve son acquisition à la reconnexion et à la mort ; il doit le remettre après sa mort.

Le modèle porté reprend la géométrie du casque de **Base lunaire 4**. Les trajectoires originales de pose et de retrait du masque T5 utilisent désormais le squelette de mains T5 et leurs durées originales : 34 ticks pour équiper, 21 pour retirer. En première personne, les épaules, coudes et poignets suivent les positions caméra originales ; les bras portent le skin du joueur. La fermeture devient protectrice au tick 30 ; le retrait ouvre le casque au tick 4. La visière affichée par-dessus le HUD est la texture originale `zom_hazmatmask_001` de Moon. Les bruitages originaux générés pour cette intégration accompagnent la manipulation, le verrouillage, la dépressurisation et la respiration. Les trajectoires sources sont conservées dans leur provenance ; les bras restent ceux du joueur Minecraft. Les sons sont recréés, les banques de bruitages sources correspondantes n’étant pas présentes dans les installations locales. Quand le casque est fermé, les sons extérieurs passent par un filtre passe-bas OpenAL (aigus atténués, niveau réduit), y compris ceux qui étaient déjà en cours. Le filtre est retiré dès l’ouverture du casque. La respiration et les manipulations internes, la musique et les sons d’interface conservent leur rendu. Le filtre nécessite une sortie audio OpenAL avec EFX ; en son absence, les sons restent fonctionnels sans filtrage.

## Configuration de carte

Dans **Map Config → Général → Environnement lunaire** :

- `gravity` : multiplicateur de gravité, 1 sur Terre, environ 0.167 sur la Lune ; bornes 0.02 à 4.
- `vacuum` : impose un casque fermé pour respirer.
- `oxygenSeconds` : délai avant asphyxie sans casque, 5 secondes par défaut, bornes 1 à 300.

La carte conserve ces réglages. Le mode créatif et le mode spectateur sont exemptés de faible gravité et d’asphyxie. Une fois la réserve épuisée, le joueur perd 2 points de vie par seconde. Le casque fermé ou une zone avec de l’air restaure la réserve. Les chutes en gravité réduite ne font pas de dégâts, y compris en quittant la zone pendant la chute. Le saut terrestre reste inchangé ; en faible gravité, l’impulsion est compensée pour éviter les bonds exagérés.

### Créer une zone sans Lua

1. Prendre **Zone d’atmosphère** (`zombierool:atmosphere_zone`) dans l’onglet créatif ZombieRool.
2. Placer le bloc à la base du volume. Il n’a aucune collision et devient invisible en mode survie ou en aperçu survie.
3. Faire clic droit en créatif : régler les rayons X et Z, la hauteur, la gravité et « Vide » ou « Air respirable », puis enregistrer.
4. En créatif, le cadre orange indique une zone de vide, le turquoise une zone respirable et le gris une zone désactivée. Viser le bloc affiche ses dimensions, sa gravité et son état. Le feedback est masqué en survie et dans l’aperçu survie.
5. Pour une salle pressurisée, placer un second bloc dans la salle, délimiter son volume, choisir l’air et la gravité 1. En cas de chevauchement, le volume le plus petit est prioritaire, indépendamment de l’ordre de chargement des chunks.
6. Casser le bloc ou désactiver sa zone supprime son effet. Les réglages et volumes survivent à la sauvegarde et fonctionnent même si le bloc est dans un chunk déchargé.

Exemple : rayon X = 8 et rayon Z = 8 donnent une emprise de 17 × 17 blocs, centrée sur le bloc. La hauteur part de son Y et s’étend vers le haut. Le test d’atmosphère prend la position de la tête du joueur.

Pour une carte entièrement lunaire, utiliser directement **Map Config → Général → Environnement lunaire** avec gravité 0.167, vide activé, oxygène 5. Aucun script ni bloc n’est alors nécessaire ; les blocs permettent ensuite les exceptions locales, comme les salles pressurisées.

Les zones sont des volumes explicites : la présence de murs ou de portes ne calcule pas automatiquement une pression atmosphérique. Les limites maximales du volume sont exclues. La plus petite zone contenant la tête du joueur a priorité ; à volume égal, l’identifiant départage les zones de façon stable. Une zone peut être remplacée sous le même identifiant et reste sauvegardée dans la dimension où le script l’a créée.

```lua
local api = ZombieroolAPI

function OnGameStart()
    api:configureLunarEnvironment(0.167, true, 5)
    -- Salle pressurisée et gravité terrestre, entre ces deux coins.
    api:setAtmosphereZone("laboratoire", 10, 64, 10, 30, 75, 30, 1, false)
end

-- Dépressurisation du laboratoire, par exemple après ouverture d’un sas.
function depressuriser()
    api:setAtmosphereZone("laboratoire", 10, 64, 10, 30, 75, 30, 0.167, true)
end

function OnHelmetChanged(playerUUID, equipped)
    -- Appelé au moment où le casque se ferme ou s’ouvre effectivement.
end
```

API supplémentaire : `getLunarEnvironment()`, `getAtmosphereZones()`, `removeAtmosphereZone(id)`, `giveLunarHelmet(playerUUID)`, `removeLunarHelmet(playerUUID)`, `setLunarHelmetEquipped(playerUUID, boolean)` et `getLunarHelmetState(playerUUID)`. Les commandes d’équipement passent par l’animation et peuvent renvoyer `false` pendant une transition, si le casque n’est pas acquis, ou si le joueur ne peut pas agir. Retirer l’acquisition par Lua ouvre immédiatement le casque.

`getLunarHelmetState` expose `owned`, `equipped`, `changing`, `target`, `elapsed`, `air`, `capacity`, `vacuum` et `gravity`. Les valeurs d’air sont en ticks. `configureMap` et `getMapConfig` donnent également accès aux réglages sous `addonConfig["zombierool:lunar"]`.

## Apparences pendant la partie

Les indices de variantes Lua commencent à **1** ; **0** choisit une variante au hasard. Les identifiants persistants `@preset/...` conservent leur ancienne numérotation à partir de zéro.

```lua
local api = ZombieroolAPI
local presets = api:getEnemySkinPresets()

-- Futures apparitions et zombies actuellement chargés dans cette dimension.
local changed = api:setEnemySkinPreset("zombie", "origins", true)
api:setEnemySkinPreset("hellhound", "infernal", true)

-- Une seule entité existante ; troisième variante du pénitencier.
for _, enemy in ipairs(api:getEnemyAppearances()) do
    if enemy.kind == "zombie" then
        api:setEnemyAppearance(enemy.uuid, "mob_of_dead", 3)
        break
    end
end
```

`getEnemySkinPresets()` renvoie deux groupes, `zombies` et `hounds`, indexés par preset. Chaque variante contient son `id` et sa `texture`. `getEnemyAppearances()` renvoie les ennemis chargés avec `uuid`, `kind`, `skin`, `eyes`, `x`, `y`, `z`.

`getEntitySkin(uuid)`, `setEntitySkin(uuid, skinID)`, `getEntityEyeSkin(uuid)`, `setEntityEyeSkin(uuid, eyeSkinID)` et `getCustomEnemySkins(kind)` exposent les apparences intégrées et celles du pack de carte. Un identifiant vide restaure le rendu historique. Une apparence intégrée d’une autre espèce ou une texture de carte inconnue est refusée. Modifier le corps réattribue aussi ses yeux selon les règles du pack de carte. Ces changements utilisent les données d’entité synchronisées existantes et persistent avec l’entité.

Le changement de preset renvoie le nombre d’ennemis modifiés, ou `-1` pour une espèce ou un preset invalide. Avec `updateExisting=false`, seuls les prochains spawns sont concernés. Les entités déchargées conservent leur apparence sauvegardée. Les règles existantes de priorité des skins personnalisés restent applicables aux futurs spawns ; un remplacement explicite d’entité sélectionne exactement le preset demandé.

Les hellhounds proposent **Infernal** et **Classique**, avec le choix automatique conservé dans Map Config. Les anciens choix `t4`, `t5`, `t6` migrent vers `infernal`. Les variantes précédemment nommées « Croisés » figurent à la suite de **Front de 1918**, qui contient cinq variantes. Les anciennes entités et leurs identifiants restent lisibles. **Pénitencier 3** utilise une conversion 256 × 256 avec répétition des coordonnées UV de la source et un relèvement des ombres pour mieux montrer les détails du tissu et des accessoires.

## Vérification

`tools/tests/EnemySkinServerTest.java` teste les appels Lua sur un serveur Forge réel, les migrations et les zones sauvegardées. `tools/tests/LunarEquipmentClientTest.java` exécute le scénario casque/animation/asphyxie/gravité dans un véritable client intégré. Les images de contrôle sont des captures de Minecraft, pas des rendus de l’atlas de texture.

`tools/prepare-enemy-presets.py`, `tools/consolidate-enemy-presets.py` et `tools/prepare-lunar-equipment.py` régénèrent les ressources. Les fichiers de jeu sources restent en lecture seule. Les règles de provenance et de redistribution des apparences précédemment documentées restent applicables.

Le comparatif audio `build/lunar-equipment/audio-comparison/comparatif-sans-puis-avec-casque.wav` contient le même tir et le même zombie : d’abord sans casque, une seconde de silence, puis avec casque. Il est rendu sans sortie sur les enceintes via OpenAL SOFT Loopback et utilise la classe `HelmetLowpass` du jeu. La génération est testée par `tools/tests/HelmetAudioLoopbackTest.java`.

La protection contre le vide commence dès l’acceptation de la pose du casque, avant la fermeture de la visière. Un casque retiré ne protège plus après son ouverture. Mort définitive, déconnexion, début et fin de partie révoquent cet équipement secondaire et annulent son animation côté client ; un respawn ne le conserve pas.

La visière se dessine avant le HUD et le tchat, sans écrire dans le tampon de profondeur. La jauge d’oxygène et l’état du casque se dessinent après le GUI complet, même lorsque la hotbar vanilla est masquée en partie. Les trajets de poursuite visent le sol sous un joueur en saut (gravité normale ou lunaire), en respectant les surfaces de collision.

`tools/tests/LunarCorrectionsClientTest.java` vérifie ces corrections dans un client intégré muet avec hotbar vanilla annulée, captures du HUD et de la visière, oxygène à zéro pendant la pose, révocation à la mort/reset et conservation de la cible zombie aux deux hauteurs de saut.

La gravité des volumes est partagée avec les créatures, grenades/lancés, flèches, objets lâchés, TNT, blocs en chute, orbes, véhicules et projectiles balistiques TACZ. Un instantané par dimension synchronise les volumes côté client, y compris pour un projectile situé dans une autre zone que le joueur. Le retour à la gravité normale restaure les attributs sans multiplier les modifications à chaque tick ; les entités configurées sans gravité conservent ce comportement.

En faible gravité, seule la nouvelle impulsion de recul du joueur est réduite (facteur racine carrée de la gravité locale), sans réduire la vitesse verticale qu’il avait déjà pendant son saut.

Pendant la pose ou le retrait, les actions d’arme sont bloquées côté client et serveur : tir, charge, visée, recharge, mêlée, équipement, interaction et changement d’arme. Les actions en cours sont annulées avant de lancer le casque. La marche reste disponible. Le rendu TACZ est suspendu pour laisser apparaître les mains et le casque en première personne. Les contrôles client utilisent une vraie AK-47 et vérifient aussi les refus de tir/visée côté serveur.

Le casque acquis apparaît comme une quatrième icône à côté des grenades et équipements dans le HUD de partie, et dans le même groupe du HUD hors partie. La touche configurable est indiquée comme pour les autres équipements ; le trait sous l’icône est turquoise lorsque le casque est porté, gris lorsqu’il est retiré et doré pendant la manipulation. Aucun rappel textuel permanent ne s’affiche en bas à gauche.

La protection des réceptions en faible gravité dépend de la zone au point d’atterrissage, jamais du casque ni d’un ancien état de saut. À la sortie aérienne d’une zone lunaire, la chute normale repart de zéro puis accumule sa distance en gravité terrestre ; une longue chute hors zone peut donc tuer, casque porté ou non. `tools/tests/LunarLandingRegressionTest.java` vérifie les sorties de zone, les états client/serveur encore anciens au moment de la réception, les atterrissages lunaires et les petites chutes terrestres.


Le menu Map Config regroupe maintenant la gravité et la respiration dans **Environment**, les apparences et la nage dans **Zombies & creatures**, et les réglages de boss dans **Bosses**. Les paramètres d’introduction sans manches se trouvent dans **Rounds** ; voir [INTRO_PHASE.md](INTRO_PHASE.md).


## Pressuriser un bloc Atmosphere Zone par son ID

Un ID aléatoire stable est généré automatiquement pour chaque bloc (ex. `zone_a14f0729bc85`), y compris les anciens blocs sans ID. Le bouton **+ ID** génère une autre proposition dans le brouillon ; enregistrer pour la valider. Il est aussi possible de saisir un nom comme `laboratoire`. Il reste invisible en partie, et son volume conserve le feedback coloré en créatif. `getAtmosphereZone(id)` lit sa configuration ; `configureAtmosphereZone(id, patch)` applique un changement partiel au bloc existant, y compris hors du chunk du joueur. Un ID inconnu renvoie false. Les modifications Lua du bloc pendant la partie sont restaurées par le journal de map en fin de partie.

```lua
local pressureQuests = {}

function OnGameStart()
    pressureQuests = {}
    ZombieroolAPI:configureAtmosphereZone("laboratoire", {vacuum=true})
end

function OnSoulObjectiveCompleted(id)
    if id ~= "pompe" and id ~= "energie" and id ~= "sas" then return end
    pressureQuests[id] = true
    if pressureQuests.pompe and pressureQuests.energie and pressureQuests.sas then
        ZombieroolAPI:configureAtmosphereZone("laboratoire", {vacuum=false})
    end
end
```

Chaque objectif doit avoir un ID distinct. Le patch `vacuum=false` conserve les dimensions et la gravité. Il rend l'air respirable dans une salle sèche ; il ne retire pas l'eau physique de Minecraft. Pour d'autres types de quêtes, appeler le même changement depuis leurs événements de completion. Intégrer les fonctions aux callbacks existants de la map pour ne pas les remplacer.
