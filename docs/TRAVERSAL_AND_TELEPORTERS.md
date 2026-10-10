# Franchissement et téléporteurs — ZombieRool 1.6.9

## Point de franchissement

L'objet `zombierool:traversal_node` est disponible dans l'onglet créatif ZombieRool. Avec le même objet, poser successivement le point d'entrée, le sommet et le point de sortie. La quatrième pose commence un nouveau parcours. Placer l'entrée et la sortie au niveau des pieds du zombie, avec un sol sous la sortie, et le sommet au-dessus du mur. Le sommet doit être au moins aussi haut que les deux extrémités.

Un clic droit sur l'entrée en créatif ouvre les réglages : activation, durée en ticks (20 ticks = une seconde), marge au-dessus du sommet et application aux autres mobs. Par défaut, le système concerne les zombies ZombieRool et les monstres. Les marqueurs utilisent la visibilité habituelle des blocs techniques ; tenir l'objet en créatif affiche aussi une prévisualisation de la courbe.

Le parcours est à sens unique. Pour franchir le mur dans les deux sens, créer un second parcours inversé. L'entrée et la sortie deviennent une liaison utilisable par la recherche de chemin. Quand le mob atteint l'entrée, sa position suit exactement une courbe passant par les trois points. Pendant ce mouvement, navigation, gravité, déplacement et poussées sont neutralisés, puis leurs états sont restaurés à l'arrivée. Ce principe reprend celui du déplacement forcé de la pomme de pin du projet Fol.

La sortie doit être dégagée. Le créateur de la carte doit prévoir la place nécessaire au corps du zombie autour du sommet et le long du trajet : le franchissement désactive les collisions pendant le saut.

## Plateforme radar

Poser `zombierool:teleporter` sur une surface libre et soutenue de 3×3 blocs. La position visée est le centre. Les neuf cases se placent ensemble et portent un cercle vert animé qui part du centre et s’étend sur toute la surface, plus lumineux lorsque la plateforme est disponible. Casser une case retire toute la plateforme. Un seul contrôleur stocke les réglages.

Le clic droit en créatif ouvre un éditeur à plusieurs pages. En partie, le clic droit ou l'interaction habituelle active le téléporteur. Les joueurs créatifs et spectateurs sont exclus des groupes de voyageurs.

Chaque plateforme doit avoir un identifiant unique dans sa dimension. Les destinations et retours sont ces identifiants. Un canal rassemble un réseau pour les liaisons, conditions, recharge commune et déblocage du Sacré Punch. Les trajets restent dans une même dimension ; Terre et Lune peuvent donc être deux zones d'une carte.

Les réglages couvrent notamment le courant, le coût payé une seule fois par le joueur qui déclenche le départ, la liaison chronométrée, la présence de tous les joueurs vivants, le départ automatique, la durée du séjour, les salles secrètes, le retour, la recharge et la nécessité de refaire la liaison. Les durées du téléporteur sont en secondes. Avant le départ, les transferts secrets et le retour, une charge électrique envahit l’écran. La durée par défaut est de six secondes, réglable de 1 à 10. Les sons teleport_start et teleport_in fournis sur le Bureau accompagnent respectivement la charge et l’arrivée. La source revalide les voyageurs, le courant, la destination et les points à la fin de la charge. Quitter la source annule le départ sans paiement. Pour un voyage temporaire, le retour doit être renseigné et dégagé. Les groupes présents sur la plateforme voyagent ensemble.

L’option Invisible platform / Plateforme invisible, dans Salles / aspect, masque toute la plateforme, sa collision, sa lumière et ses invitations d’interaction. Elle conserve le volume de déclenchement 3×3 et les arrivées, ce qui convient aux salles secrètes. En créatif, son contour reste ciblable pour ouvrir les réglages. Une réception n’affiche la confirmation que si une véritable liaison attend vers elle ; sans liaison, elle reste silencieuse.

## Préréglages

- **Direct** : destination fixe, sans liaison obligatoire. Régler courant, coût et recharge selon la carte.
- **Der Riese** : trois sources sur le même canal, toutes dirigées vers une plateforme de rôle Réception. Activer une source puis confirmer au noyau avant la fin du compte à rebours. Le seuil de trois liaisons débloque le Sacré Punch. Le réglage du seuil permet d'adapter ce nombre.
- **Kino** : source vers une salle de projection, avec une réception distincte renseignée dans Retour. La réception sert à valider la liaison et à terminer le voyage. Les identifiants des salles secrètes se séparent par des virgules ; leur probabilité et leur durée sont configurables. La recharge commence à la fin du voyage et une nouvelle liaison est requise par défaut.
- **Moon** : départ automatique lorsque tous les joueurs vivants de la dimension sont sur la plateforme, sans liaison. Le préréglage ne demande pas le courant ; l'activer sur la plateforme lunaire pour le trajet inverse.
- **DEFCON** : destination aléatoire parmi les autres plateformes disponibles du canal. Après activation du nombre requis d'interrupteurs, les portails envoient vers la destination Panic Room configurée. Renseigner le retour pour le séjour temporaire. Les conditions DEFCON retombent à la fin du voyage ; un script peut aussi terminer le séjour lors de l'ouverture des portes.
- **Portail naturel** : saut sur la plateforme, destination aléatoire parmi les portails naturels actifs du canal. Les conditions `energy` et `denizen` doivent être actives. Leur activation est raccordable au script de la carte : ce changement n'ajoute pas une entité Denizen ou un objet Turbine.
- **Fusée** : destination fixe avec courant et recharge. La condition `rocket_test` bloque le départ tant qu'elle est active ; le script de la carte décide quand le test commence et finit.

## Conditions et scripts

`zombierool:teleporter_switch` fournit un interrupteur configurable. Pour DEFCON, placer quatre interrupteurs du même canal avec des conditions distinctes `defcon_1`, `defcon_2`, `defcon_3`, `defcon_4`. Seules les conditions préfixées `defcon_` participent au compteur DEFCON.

L'API de script expose les méthodes suivantes sur l'objet `ZombieroolAPI` habituel :

```lua
local api = ZombieroolAPI
api:setTeleporterCondition("pentagone", "defcon_1", true)
local defcon = api:getTeleporterDefcon("pentagone")
api:endTeleporterTrip("pentagone")

api:setTeleporterCondition("brouillard", "energy", true)
api:setTeleporterCondition("brouillard", "denizen", true)
api:setTeleporterCondition("fusee", "rocket_test", true)
```

Les événements disponibles sont `OnTeleporterLinkStarted(sourceId, receiverId)`, `OnTeleporterLinked(sourceId, receiverId)`, `OnTeleporterUsed(sourceId, destinationId, payerUUID)`, `OnTeleporterTripEnded(channel)`, `OnTraversalStarted(mobUUID, entryX, entryY, entryZ)` et `OnTraversalFinished(mobUUID)`.

Les réglages et états de liaison/recharge sont sauvegardés. Une déconnexion pendant un séjour conserve une destination de retour pour la reconnexion. Une remise à zéro de la partie réinitialise liaisons, conditions et recharges. Les clients et le serveur doivent utiliser la même version du mod.

## Vérification

La compilation et 2 026 vérifications de courbe, configuration, registre et paquets ont été effectuées. Un serveur dédié isolé a vérifié la recherche de chemin à travers un mur, le saut malgré des déplacements et poussées externes, la pose en trois étapes, la plateforme 3×3 et sa suppression, ainsi qu'un voyage de groupe avec liaison, paiement unique, salle temporaire, salle secrète, retour, recharge et nouvelle liaison. L'affichage du radar reste à vérifier dans le client Minecraft.
