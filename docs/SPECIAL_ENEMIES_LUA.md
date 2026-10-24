# Ennemis spéciaux : configuration et LuaJ

Dans le menu de configuration de la map, l’onglet **Ennemis spéciaux** rassemble le type unique de mini-boss, son calendrier, les loots, l’équilibrage et les règles des deux minions. Le bouton `…` d’un spawner spécial ouvre cet onglet. Les spawners conservent leur position, zone, canaux, alimentation et poids de sélection. Les paramètres de leur ancien deuxième menu sont désormais communs à toute la map, séparément pour le napalm et le squelette.

Un seul type de mini-boss est sélectionné pour la map : `panzer`, `brutus` ou une définition ajoutée par datapack. Le choix synchronise les spawners chargés ; un spawner rechargé adopte ce choix. Le calendrier et le nombre de boss par rencontre restent indépendants du nombre de blocs spawners. Les boss déjà vivants conservent leur identité et leurs PV.

## Loot

Chaque récompense possède un slot : glisser un objet depuis l’inventaire copie sa quantité et ses NBT complets (nom, données de quête, arme TacZ, camo…), sans consommer l’objet. L’arme de départ utilise le même sélecteur, avec une quantité de un. Le bouton Vider désactive la récompense. Les tables de datapack restent une alternative avancée : choisir une table vide le slot, choisir un objet désactive la table. Les tables manquantes ne produisent aucun objet ; une table ajoutée ensuite devient utilisable après rechargement du datapack. Leur contexte est `minecraft:entity` avec l’entité, la position, la source de dégâts et, lorsqu’il existe, le joueur responsable et sa chance.

- `firstLoot` : premier mini-boss tué de chaque partie, une seule fois pour toute la map.
- `recurringLoot` : tous les kills suivants.
- `recurringOnFirst` : ajoute également la table récurrente au premier kill.

Le compteur se réinitialise au redémarrage de la partie, pas entre deux manches. Les futurs objets de quête pourront être référencés dans un datapack ; cette version n’invente pas de clé ni de pièce de bâton.

## API

Les fonctions ci-dessous sont publiques dans `ZombieroolAPI`, utilisables avec `:`. Les coordonnées des spawners sont des entiers. Les identifiants d’entités et de joueurs sont des UUID sous forme de chaîne. Une entité absente renvoie une table vide, une action impossible renvoie `false`.

```lua
local cfg = ZombieroolAPI:getSpecialEnemiesConfig()
ZombieroolAPI:configureSpecialEnemies({
    type = "brutus",
    firstWave = 6,
    interval = 4,
    firstLoot = "my_map:boss_first",
    recurringLoot = "my_map:boss_recurring",
    recurringOnFirst = false,
    napalmFirstWave = 7,
    napalmUseRadius = false,
    skeletonUseRadius = true,
    skeletonRadius = 16
})
```

Les modifications partielles conservent les autres valeurs. Les valeurs numériques sont bornées ; NaN et les identifiants malformés sont rejetés. `napalmFirstWave` est une valeur de configuration, pas une restriction fixe à la manche cinq.

- `getSpecialEnemiesConfig()`, `configureSpecialEnemies(table)`, `setMiniBossType(type)` : paramètres de la map et synchronisation des spawners.
- `getMapConfig()`, `configureMap(table)` : lecture/modification des paramètres persistants, avec la sous-table `specialEnemies` ; les autres effets visuels de la map sont appliqués par leurs systèmes habituels.
- `listBossLootTables()`, `getMiniBossKillCount()`, `resetMiniBossLoot()` : tables disponibles, progression et réinitialisation explicite du premier loot.
- `setMiniBossLootItem(first,itemID,count,nbtSNBT)` : configure un objet exact comme première récompense (`true`) ou récompense récurrente (`false`). `minecraft:air` vide le slot. Exemple : `ZombieroolAPI:setMiniBossLootItem(true, "minecraft:tripwire_hook", 1, '{QuestKey:"warden"}')`. Lecture dans `getSpecialEnemiesConfig().firstLootStack` / `recurringLootStack`.
- `getAddonMapConfig()` : réglages des extensions déclarées ; `configureMap({addonConfig={...}})` permet de les modifier.
- `listSpecialSpawners()`, `getSpecialSpawner(x,y,z)`, `configureSpecialSpawner(x,y,z,table)` : spawners chargés. `FirstWave`, `WaveInterval` pour les boss ; `FirstWave`, `Radius`, `Cooldown`, `MaxAlive`, `Batch`, `UseRadius` pour les minions modifient les règles **de la map**. Les champs `zone`, `startChannels`, `stopChannels`, `requirePower`, `weight` restent locaux. `type` choisit le boss global ou le type local d’un minion.
- `listSpecialEnemies()`, `getSpecialEnemy(uuid)` : UUID, type, position, PV/max, `boss`, `helmet`, `reactorCover`, `reactorCore`, `capturedPlayer`, `descending`, `enraged`.
- `spawnSpecialEnemy(x,y,z)` : apparition explicite au spawner pendant une partie. Cette action de script est distincte du budget des apparitions automatiques.
- `releasePanzerGrab(uuid)` : libère le joueur retenu.
- `shockMiniBoss(uuid)` : choc du téléporteur, dégâts configurés et interruption des attaques.
- `damageSpecialEnemy(uuid,damage,hitX,hitY,hitZ,playerUUID)` : dégâts attribués au joueur, avec les règles du blindage/point faible correspondant à la position d’impact.
- `spawnNapalmFire(x,y,z,seconds)` : tapis de feu circulaire temporaire, sans poser de blocs de feu ni enflammer le joueur façon vanilla.
- `shakePlayerScreen(playerUUID,ticks,strength)` : secousse bornée pour le joueur visé.

Les interfaces Lua précédentes des portes, téléporteurs, liens de navigation, coffres à âmes, objectifs Cerberus et canaux restent disponibles.

## Événements

```lua
function OnMiniBossKilled(playerUUID, bossType, bossUUID, firstKill, killCount)
    -- premier kill à l’échelle de la partie
end
function OnMiniBossLoot(bossUUID, rewardID) end
function OnBossArmorBroken(bossUUID, bossType, part) end
function OnPanzerGrab(bossUUID, playerUUID) end
function OnPanzerRelease(bossUUID, playerUUID) end
function OnMinionKilled(entityUUID, minionType) end
```

`part` vaut `helmet`, `reactor_cover` ou `reactor_core`. Les événements de mort/armure sont émis une seule fois pour chaque transition.
`rewardID` est l’identifiant de table ou `item:<namespace>:<objet>` lorsqu’un slot fournit la récompense.

## Menu dynamique et addons

Les armes proviennent des définitions chargées et des index TacZ. Les grenades proviennent des objets enregistrés dérivant de `ThrowableCore.BaseThrowableItem`, ou du tag d’objets `zombierool:map_throwables` pour les addons. Le sélecteur d’inventaire accepte tout objet enregistré, avec ses NBT.

Un addon peut déclarer ses réglages lors de son initialisation. Le serveur envoie le schéma à l’ouverture du menu ; les lignes sont générées automatiquement dans l’onglet choisi. Cette déclaration décrit les réglages : le comportement de l’ennemi reste fourni par l’addon.

```java
CompoundTag defaults = new CompoundTag();
defaults.putBoolean("enabled", true);
defaults.putInt("firstWave", 12);
defaults.putDouble("healthMultiplier", 1.5);
MapConfigExtensions.register(new ResourceLocation("my_addon", "extra_enemy"),
    "enemies", defaults);
// Dans le gameplay de l’addon :
CompoundTag settings = WorldConfig.get(level).getAddonConfig()
    .getCompound("my_addon:extra_enemy");
```

Catégories : `general`, `enemies`, `weapons`, `extras`. Types de lignes : booléen, entier, nombre décimal, chaîne. Traductions : `config.my_addon.extra_enemy.<clé>`. Les réglages persistent dans les données de la map et son JSON ; les valeurs d’un addon temporairement absent sont conservées.

`panzerFlameDamage` règle les dégâts en PV par pulsation (défaut 2). `panzerHoldTicks` règle la durée d’immobilisation après traction (défaut 60 ticks, soit 3 secondes). Les flammes du Panzer n’appliquent pas le feu vanilla ; les explosions de sa mort sont uniquement visuelles et sonores.

## Valeurs d’équilibrage par défaut

Panzer : 480 PV ; Brutus : 360 PV. Les impacts sur blindage conservent 25 % des dégâts, la tête exposée conserve tous les dégâts. Progression : +8 % des PV de base par manche après la première apparition configurée, +50 % par joueur vivant supplémentaire. Ces valeurs donnent environ 90 tirs de 7 dégâts au Panzer et 56 à Brutus en visant leur tête, sans Pack-a-Punch ni Double Tap. Les scripts et le menu peuvent les modifier.

Les autres clés de `getSpecialEnemiesConfig()` correspondent aux champs du menu : vitesse de chaque ennemi, pince, secousses, dégâts du téléporteur, résistance du napalm, vitesse enragée, rayon de chaleur, détonation, durée du tapis de feu et règles d’apparition de chaque minion.


## Changer de mini-boss pendant la partie

```lua
local old = ZombieroolAPI:getMiniBossType()
local available = ZombieroolAPI:listMiniBossTypes()
assert(ZombieroolAPI:setMiniBossType("brutus"))
-- Variante déclarée dans un datapack :
assert(ZombieroolAPI:setMiniBossType("my_map:warden"))
```

La fonction renvoie `false` pour un type non enregistré. Le changement est commun à la map et ses spawners ; les boss déjà présents conservent leur identité. Il ne réinitialise ni le quota de la manche, ni les récompenses du premier kill, et ne force pas une apparition. Les prochains spawns respectent le calendrier actuel.

Le menu **Ennemis spéciaux → Changement de boss en partie** configure une transition automatique. Elle est désactivée par défaut, se déclenche une fois par partie et se réarme au redémarrage d’une partie. Le snapshot de configuration de partie rétablit le type initial pour la partie suivante. Les options sont :

- `wave` : dès que la manche est au moins `bossSwitchWave` ;
- `power` : quand le courant global est activé ;
- `activator` : quand l’activateur aux coordonnées X/Y/Z est actif (activateur global ou activateur à canal) ;
- `soul_checker` : quand le checker aux coordonnées est activé et que tous ses objectifs sont complets. Le champ facultatif `bossSwitchSoulIds` permet de fournir directement des IDs d’objectifs à la place du bloc checker ;
- `none` : désactivé.

```lua
ZombieroolAPI:configureSpecialEnemies({
    bossSwitchTrigger = "wave",
    bossSwitchWave = 16,
    bossSwitchTarget = "brutus"
})
-- Pour un activateur : bossSwitchTrigger="activator", bossSwitchX/Y/Z.
-- Pour le courant : bossSwitchTrigger="power".
-- Pour un checker : bossSwitchTrigger="soul_checker", bossSwitchX/Y/Z.
```

L’événement `OnMiniBossTypeChanged(previousType, nextType)` est émis pour un changement effectif via les fonctions de configuration. Changer le type par Lua après une transition automatique ne réarme pas celle-ci.

## Brutus et les machines

Brutus cherche une machine à atout alimentée ou la boîte mystère active visible dans un rayon de 6 blocs sur le chemin de sa poursuite, rejoint la machine, la frappe et la verrouille. L'approche doit rester courte (8 blocs au maximum), sans lien de déplacement. Un joueur visible à proximité ou des tirs reçus interrompent cette action. Après un verrouillage, Brutus poursuit les joueurs pendant au moins 30 secondes avant d'en tenter un autre. Des chaînes entourent la machine verrouillée. Maintenir l'action pendant la durée configurée déverrouille la machine (2 000 points par défaut, `brutusUnlockCost`, prélevés à la fin) ; une nouvelle interaction est nécessaire pour acheter. Un joueur sans assez de points ne peut pas commencer. Les verrouillages sont sauvegardés et nettoyés au redémarrage de la partie.

Les trajets des boss tiennent compte de la longueur des liens et de leur coût d'embarquement. Leur historique de déplacement persiste lors des interruptions par une machine et des mouvements du joueur du même côté du lien, afin d'éviter les circuits répétés. Un retour est autorisé lorsque la cible passe réellement du côté du départ.

```lua
local locked = ZombieroolAPI:isMachineLocked(x, y, z)
local success = ZombieroolAPI:lockMachine(x, y, z)
```

Utiliser les coordonnées du bloc principal (centre de la machine à atout, base de la boîte). Événements : `OnBrutusMachineLocked(x,y,z)` et `OnBrutusMachineUnlocked(playerUUID,x,y,z)`.

## Créer une variante de boss sans Java

Ajouter `data/my_map/mini_bosses/warden.json` à un datapack 1.20.1 :

```json
{
  "template": "brutus",
  "name": "Warden",
  "health": 320,
  "speed": 0.42,
  "damage": 3
}
```

Le boss apparaît sous `my_map:warden` dans le sélecteur et possède sa propre section dépliable pour ses statistiques. `template` accepte `panzer` ou `brutus` et reprend son modèle cubique, ses animations et ses capacités. `health` est la base avant la progression par manche et joueurs. `damage` règle la mêlée ; les capacités du Panzer gardent leurs paramètres communs.

Un champ facultatif `texture` (ex. `my_map:textures/entities/warden.png`) référence une texture du resource pack, avec les UV du modèle choisi. `name` peut être un nom lisible ou une clé de traduction de ce resource pack. Recharger les datapacks puis rouvrir le menu pour voir les définitions ajoutées. Les entités déjà créées sauvegardent leur profil.

L’exemple complet se trouve dans `examples/mini-boss-datapack`. Il crée une variante avec le modèle Brutus existant. Un modèle inédit ou des capacités entièrement nouvelles nécessitent encore un addon ; cette définition ne constitue pas un éditeur de géométrie.

Chaque boss enregistré, le napalm, le squelette et les entités de l’onglet Mobs disposent d’une section dépliable. Les entrées fournies par `MapConfigExtensions` sont également regroupées individuellement.

La poursuite des boss utilise uniquement les trajets complets du pathfinder. Avant un embarquement, un court chemin � pied vers un joueur sur la m�me plateforme remplace un ancien trajet de transport. Le recalcul ne ram�ne plus le boss au centre du bloc de d�part avant de poursuivre.

Le d�verrouillage affiche une barre interpol�e par image et joue le craquement des mains adapt� de `viewmodel_zombie_packopunch` (T6), avec les sons originaux. Le d�p�t accept� au Pack-a-Punch joue la m�me animation. LuaJ : `ZombieroolAPI:playKnuckleAnimation(playerUUID)` d�clenche cette animation pour un joueur (retour bool�en).
