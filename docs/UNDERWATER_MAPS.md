# Maps sous-marines

L’eau Minecraft et les poches d’air réelles sont reconnues automatiquement. Aucun script ni volume d’atmosphère n’est nécessaire pour une immersion normale.

Dans Map Config > Environment > Atmosphere & breathing, conserver Gravité = 1 et Vide = non. La réserve sans casque vaut 5 secondes par défaut et est commune à l’eau et au vide. L’oxygène diminue lorsque la tête est immergée, pas lorsque seuls les pieds sont mouillés. La jauge ZombieRool remplace les bulles vanilla : il n’y a pas de seconde réserve ou de deuxième source de noyade.

Le casque lunaire existant est également étanche. Il protège dès le début de sa pose, puis recharge la réserve. Pendant le retrait, la protection cesse à l’ouverture du casque. Sans casque, l’oxygène remonte dans une pièce sèche ou à la surface. Les potions de respiration aquatique et le pouvoir de conduit protègent dans l’eau, mais pas dans une zone configurée comme vide. Les effets, les déplacements et les interactions conservent leur comportement Minecraft ; ce changement n’ajoute pas de gestion de pression ou de décompression.

Pour un sas, construire une pièce contenant de vrais blocs d’air et empêcher l’entrée d’eau avec des parois/portes adaptées. Dans une map dont l’environnement global est configuré sans air, ajouter en plus un bloc Zone d’atmosphère réglé Vide = non et Gravité = 1 dans la pièce. Une zone avec Vide = non ne transforme pas physiquement l’eau en air.

Les blocs de navigation restent nécessaires pour les zombies. Par défaut, ils marchent sur le fond et ne se noient pas. La nage peut être activée pour cette map (voir plus bas). En mode marche, prévoir une hauteur d’eau et des passages qui restent accessibles depuis le fond ; une cible qui nage très loin au-dessus peut être hors de portée. Les tirs TACZ et les grenades restent disponibles lorsque le casque n’est pas en cours de manipulation ; l’eau conserve la traînée et les collisions propres à chaque projectile.

## Essai local

Le scénario `tools/tests/UnderwaterRegressionTest.java` construit un bassin vitré à x=160..180, z=-6..6, fond y=64, eau y=65..72. Une pièce sèche se trouve à x=175..178, z=-2..2, y=65..70 ; sa porte est à (176,65,-3), avec plaques de pression. Une station de casque est placée sur la passerelle à (165,74,-8). Entrée sous l’eau : `/tp @s 165.5 65 0.5`. Pièce sèche : `/tp @s 177 65 0.5`. Retour sur la passerelle : `/tp @s 163.5 74 -9`.

Le test utilise un vrai joueur serveur, l’immersion réelle, la pose/retrait du casque, une poche d’air, une transition eau/vide, les effets de respiration, les trajets de zombie, une grenade réelle et une AK-47 TACZ côté client. Les essais se font dans une copie de monde dédiée avec le son coupé.

Les blocs techniques (dont les spawners et marqueurs de navigation), les portes de défense, barricades, plateformes et principaux équipements peuvent être waterloggés. Chaque partie d’un bloc multiple conserve sa propre eau ; les mises à jour de stage ou de configuration ne la suppriment pas. Les zombies avancent sous l’eau à environ 84 % de leur vitesse au sol, sans poussée artificielle liée aux entrées du contrôleur de déplacement.

### Compatibilité des anciennes maps

Tous les blocs compatibles avec l’eau ont un état par défaut sec (`waterlogged=false`). Les palettes des anciennes maps qui ne contiennent pas cette propriété restent donc sèches. L’eau n’est conservée que si le bloc est posé dans l’eau, rempli avec un seau, ou chargé avec un état explicitement immergé. Une map déjà sauvegardée avec la version au défaut immergé peut conserver ses états `waterlogged=true` ; ce correctif ne les efface pas automatiquement, pour préserver les zones aquatiques volontaires.

### Zombies nageurs et passages vers l’air

`Map Config > Zombies & creatures > Underwater enemies > Swimming zombies` active la nage des zombies ordinaires pour la map. Désactivé par défaut : les maps existantes conservent les zombies qui coulent et marchent au fond. Les hellhounds et ennemis spéciaux gardent leur locomotion propre. Le réglage est sauvegardé avec la map.

En mode nage, les zombies cherchent un trajet en trois dimensions vers le joueur et peuvent sortir vers un passage sec. Les Path Blocks restent nécessaires dans les colonnes navigables ; la nage n’autorise pas à traverser les murs ni à survoler le vide à l’air libre. Pour une porte en fer, une plaque de pression actionnable par un zombie doit se trouver immédiatement devant la porte sur le côté d’approche. La porte s’ouvre réellement grâce à la plaque ; le zombie ne la traverse pas fermée.

```lua
ZombieroolAPI:setZombieSwimming(true) -- nage vers le joueur
ZombieroolAPI:setZombieSwimming(false) -- retour à la marche au fond
local swimming = ZombieroolAPI:getZombieSwimming()
```

Sur sol sec en gravité normale, les dégâts de chute conservent leur valeur liée à la hauteur même pendant une partie : ils ne sont plus ramenés à un point. Les protections de PHD et de la gravité lunaire restent applicables.
