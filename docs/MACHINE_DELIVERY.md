# Landing Site et Sky Delivery

Le bloc **Landing Site** se trouve dans l’inventaire ZombieRool → **Machines & traps**. Il se pose **sur le sol comme un tapis**, à la base de la future machine. Il est sans collision, navigable pour les entités et invisible en jeu. Le bloc sous le tapis reste libre pour votre redstone, activator ou autre source de courant. Son apparence et son icône reprennent le marqueur d’emplacement de l’établi.

## Roulette déclenchée par une porte

1. Posez un Landing Site sur le sol derrière la porte. Clic droit en créatif.
2. Dans **Recevoir une machine par**, choisissez **Floating Roulette**.
3. Choisissez **Canal de porte**, puis indiquez le même **numéro de canal** que celui configuré sur la porte achetable. Il n’est pas nécessaire de saisir les coordonnées de la porte. Un canal peut déclencher plusieurs emplacements ; chaque roulette ne livre qu’une machine par partie.
4. Dans **Machines et prix**, choisissez les perks possibles et leurs prix.
5. Réglez la durée et la hauteur du flottement dans **Animation**, puis enregistrez.

Pendant une partie, l’achat de la porte ouvre son canal et lance les roulettes correspondantes. La machine flotte avec des particules, les modèles défilent puis elle se pose. Le résultat reste définitif jusqu’à la remise à zéro. **Signal / redstone** et **Lua uniquement** restent disponibles pour les systèmes de portes personnalisés. Les anciennes configurations par coordonnées continuent de fonctionner.

## Chutes toutes les X manches

1. Posez les Landing Sites aux emplacements autorisés et laissez leur utilisation sur **Chute du ciel**.
2. Ouvrez **Map Config → Waves / Manches → Sky Delivery**.
3. Activez le système et réglez la première manche et l’intervalle. Exemple : première manche 3 et intervalle 4 → 3, 7, 11, 15… Les manches spéciales comptent aussi.
4. Dans **Machines et prix**, sélectionnez les perks et éventuellement le Pack-a-Punch.
5. Dans **Animation**, réglez la durée et la hauteur de chute. La durée par défaut est de **10 secondes** ; l’ancienne valeur par défaut de 5 secondes est portée à 10 secondes au chargement des réglages Sky Delivery. Enregistrez.

Il n’y a **ni bloc contrôleur à poser ni groupe à renseigner**. Les réglages concernent tous les Landing Sites de chute activés dans cette dimension. Les points utilisés pour une roulette de porte sont exclus. La machine et un point libre sont choisis avant le début de la chute ; aucun roulement n’est visible.

**Sans doublon** exclut les machines déjà livrées ou réservées par le même système (chutes et roulettes ont leurs sélections indépendantes). Une livraison en cours réserve son emplacement. Les livraisons prévues pendant une chute attendent la fin de l’animation. Une notification répétée de la même manche ne crée pas de doublon.

Le tapis est remplacé par la vraie machine à l’atterrissage. Ses réglages et son résultat restent enregistrés dans les données de la carte. La remise à zéro retire la machine et restitue le tapis avec sa configuration. Le courant sous le tapis est conservé. Les achats et l’alimentation suivent les règles habituelles des perks et du Pack-a-Punch.

Laissez trois blocs libres à partir du tapis pour une perk, deux pour le Pack-a-Punch. Les blocs Path sont acceptés et rétablis lors de la remise à zéro. Un obstacle solide apparu pendant la chute annule l’atterrissage sans le détruire.

**Tester la livraison** permet un essai sans lancer de partie. **Réinitialiser les chutes** nettoie les essais du système de chute sans retirer les roulettes de porte. **Réinitialiser ce point** concerne seulement le point sélectionné. Le début et la fin d’une partie nettoient l’ensemble des livraisons. Les anciens blocs encastrés dans le sol conservent leur ancien décalage vertical pour ne pas déplacer une machine existante ; les nouveaux tapis se posent directement au-dessus du sol.

## Protection à l’atterrissage et effets

Pendant la roulette et la chute, le volume final de la machine est réservé côté serveur. Un joueur qui entre dedans est déplacé vers un sol libre à côté, jamais vers le haut de la machine. Les sauts ne permettent pas de rester dans ce volume. Si les murs ou d’autres joueurs empêchent de trouver une sortie sûre, la machine attend avant de se matérialiser. La vérification est répétée juste avant la pose des blocs.

Les chutes jouent l’alarme d’arrivée, l’approche, la boucle de descente et l’impact d’origine. Les références ont été vérifiées dans le script compilé local `maps/mp/zm_nuked_perks.gsc` et les alias de la banque `zmb_nuked_real.all`. Le manifeste `assets-source/26.7/audio/machine-delivery-manifest.json` indique les fichiers, leur durée et leur empreinte. Les sources sont celles de l’installation Black Ops II locale ; la carte de chute n’est pas présente dans Black Ops I.

L’alarme vient du point d’atterrissage. L’approche et le souffle suivent la position de la machine en trois dimensions et leur volume diminue avec la distance. Les sources d’approche sont converties en mono pour préserver la spatialisation de Minecraft. Les sons de vol s’arrêtent à l’atterrissage, à l’annulation ou à la remise à zéro. Des couches de flammes animées entourent les quatre faces et toute la hauteur de la machine ; des particules et une traînée de fumée accompagnent la chute ; il s’agit d’effets visuels, sans incendier la carte ni le joueur.

## Lua

Les fonctions concernent la dimension du script. Les coordonnées d’un emplacement désignent le tapis.

```lua
local z = ZombieroolAPI
-- Sky Delivery : aucun contrôleur physique, aucun groupe.
z:configureSkyDelivery({enabled=true, firstRound=3, everyRounds=4,
    pool={"juggernog", "speed_cola", "quick_revive", "pack_a_punch"},
    prices={juggernog=2500, pack_a_punch=5000},
    noRepeats=true, duration=100, dropHeight=48})

z:placeMachineDelivery(8, 64, 0, "south", {role="landing"})
z:placeMachineDelivery(-8, 64, 0, "east", {role="landing"})
local settings = z:getSkyDelivery()
local error = z:triggerSkyDelivery() -- chaîne vide = succès
error = z:deliverSkyMachineAt("juggernog", 8, 64, 0)
z:cancelSkyDelivery()
z:resetSkyDelivery()

-- Roulette reliée au canal 7 d’une porte achetable.
z:placeMachineDelivery(0, 64, 8, "north", {
    role="controller", mode="roll", trigger="door_channel", doorChannel=7,
    pool={"juggernog", "speed_cola"}, hoverHeight=.6, duration=100})
-- Pour une porte d’addon : signalez l’ouverture de son canal pendant la partie.
z:notifyDeliveryDoorChannel(7)

local blocks = z:listMachineDeliveries()
local machines = z:listDeliveryMachines()
local state = z:getMachineDelivery(0, 64, 8)
z:configureMachineDelivery(0, 64, 8, {prices={juggernog=2000}})
z:triggerMachineDelivery(0, 64, 8)
z:setMachineDeliveryEnabled(0, 64, 8, false)
z:cancelMachineDelivery(0, 64, 8)
z:resetMachineDelivery(0, 64, 8, false)
```

`duration` est en ticks (20 = une seconde), les hauteurs sont en blocs. `getSkyDelivery` et `getMachineDelivery` renvoient aussi `phase`, `selected`, `error`, `deliveries`, `queued` et `lastRound`. Le numéro de canal de porte est un entier positif. Les anciens champs `role=controller/mode=roll` désignent une roulette locale ; ils ne créent plus de contrôleur de chute dans le monde.

Les callbacks `OnMachineDeliveryStarted`, `OnMachineDeliveryLanded`, `OnMachineDeliveryFailed` et `OnMachineDeliveryCancelled` conservent la signature `(cx,cy,cz,x,y,z,machine,mode,error)`. Les coordonnées du contrôleur global sont `(0,-2048,0)`, un identifiant virtuel qui ne correspond pas à un bloc du monde. `mode` vaut `roll` ou `drop`. Les destinations sont toujours les coordonnées réelles du tapis. Les erreurs de déclenchement sont `disabled`, `busy`, `empty_pool`, `occupied` ou `no_site` ; une obstruction en cours de vol produit `blocked_landing`.
