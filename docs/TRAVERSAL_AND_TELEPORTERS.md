# Franchissement et téléporteurs — ZombieRool 26.7

## Point de franchissement

L'objet `zombierool:traversal_node` est disponible dans l'onglet créatif ZombieRool. Avec le même objet, poser successivement le point d'entrée, le sommet et le point de sortie. La quatrième pose commence un nouveau parcours. Placer l'entrée et la sortie au niveau des pieds du zombie, avec un sol sous la sortie, et le sommet au-dessus du mur. Le sommet doit être au moins aussi haut que les deux extrémités.

Un clic droit sur l'entrée en créatif ouvre les réglages : activation, durée en ticks (20 ticks = une seconde), marge au-dessus du sommet et application aux autres mobs. Par défaut, le système concerne les zombies ZombieRool et les monstres. Les marqueurs utilisent la visibilité habituelle des blocs techniques ; tenir l'objet en créatif affiche aussi une prévisualisation de la courbe.

Le parcours est à sens unique par défaut. Activer **Bidirectional** dans son menu pour rendre la même liaison utilisable dans les deux sens. L'entrée et la sortie deviennent une liaison utilisable par la recherche de chemin. Quand le mob atteint l'entrée, sa position suit exactement une courbe passant par les trois points. Pendant ce mouvement, navigation, gravité, déplacement et poussées sont neutralisés, puis leurs états sont restaurés à l'arrivée. Ce principe reprend celui du déplacement forcé de la pomme de pin du projet Fol.

La sortie doit être dégagée. Le créateur de la carte doit prévoir la place nécessaire au corps du zombie autour du sommet et le long du trajet : le franchissement désactive les collisions pendant le saut.

Jump Link et Zipline se placent en deux temps : Entry puis Exit. Bidirectional autorise le sens inverse. Pour Zipline, Access → Allow players contrôle les joueurs séparément de Allow all mobs. Crawler Wall Link se pose au bord du toit et détecte son mur ; One-way Gate interdit le retour après franchissement. Casser une partie d’un lien retire ses autres marqueurs.

Les marqueurs sont navigables par défaut. Le terrain autour conserve les règles de la carte : Path Blocks ou blocs autorisés, sans rayon automatique autour des liens. Dans l’eau, les zombies suivent le fond et remontent par des marches ; un mur vertical ne devient pas un chemin de nage.

## Plateforme radar

Poser `zombierool:teleporter` sur une surface libre et soutenue de 3×3 blocs. La position visée est le centre. Les neuf cases se placent ensemble et portent un cercle vert animé qui part du centre et s’étend sur toute la surface, plus lumineux lorsque la plateforme est disponible. La surface est désormais un disque de trois blocs de diamètre, sur fond vert avec une onde vert clair. Casser une case retire toute la plateforme. Un seul contrôleur stocke les réglages.

Le clic droit en créatif ouvre un éditeur à plusieurs pages. En partie, le clic droit ou l'interaction habituelle active le téléporteur. Les joueurs créatifs et spectateurs sont exclus des groupes de voyageurs.

Chaque plateforme doit avoir un identifiant unique dans sa dimension. Les destinations et retours sont ces identifiants. Un canal rassemble un réseau pour les liaisons, conditions et déblocage du Sacré Punch. Les trajets restent dans une même dimension ; Terre et Lune peuvent donc être deux zones d'une carte.

Les réglages couvrent notamment le courant, le coût payé une seule fois par le joueur qui déclenche le départ, la liaison chronométrée, la présence de tous les joueurs vivants, le départ automatique, la durée du séjour, les salles secrètes, le retour, la recharge et la nécessité de refaire la liaison. Les durées du téléporteur sont en secondes. Avant le départ, les transferts secrets et le retour, une charge électrique envahit l’écran. La durée par défaut est de trois secondes, réglable de 1 à 10. Les sons teleport_start et teleport_in fournis sur le Bureau accompagnent respectivement la charge et l’arrivée. La source revalide les voyageurs, le courant, la destination et les points à la fin de la charge. La charge électrique laisse les joueurs libres de bouger et d’agir. Ils sont immobilisés uniquement pendant le tunnel visuel de téléportation. Tir, rechargement, mêlée, grenades, interactions et changements d’arme sont bloqués côté client et serveur. Les actions redeviennent disponibles pendant le séjour dans la salle. Une annulation, une mort ou un reset libère le verrou. Pour un voyage temporaire, le retour doit être renseigné et dégagé. Les groupes présents sur la plateforme voyagent ensemble.

L’option Invisible platform / Plateforme invisible, dans Salles / aspect, masque toute la plateforme, sa collision, sa lumière et ses invitations d’interaction. Elle conserve le volume de déclenchement 3×3 et les arrivées, ce qui convient aux salles secrètes. En créatif, son contour reste ciblable pour ouvrir les réglages. Une réception n’affiche la confirmation que si une véritable liaison attend vers elle ; sans liaison, elle reste silencieuse.

## Préréglages

- **Direct** : destination fixe, sans liaison obligatoire. Régler courant, coût et recharge selon la carte.
- **Der Riese** : trois sources sur le même canal, toutes dirigées vers une plateforme de rôle Réception. Activer une source puis confirmer au noyau avant la fin du compte à rebours. Le seuil de trois liaisons débloque le Sacré Punch. Le réglage du seuil permet d'adapter ce nombre.
- **Kino** : source vers une salle de projection, avec une réception distincte renseignée dans Retour. La réception sert à valider la liaison et à terminer le voyage. Les identifiants des salles secrètes se séparent par des virgules ; leur probabilité et leur durée sont configurables. La recharge commence à la fin du voyage et une nouvelle liaison est requise par défaut.
- **Moon** : départ automatique lorsque tous les joueurs vivants de la dimension sont sur la plateforme, sans liaison. Le préréglage ne demande pas le courant ; l'activer sur la plateforme lunaire pour le trajet inverse.
- **DEFCON** : destination aléatoire parmi les autres plateformes disponibles du canal. Après activation du nombre requis d'interrupteurs, les portails envoient vers la destination Panic Room configurée. Renseigner le retour pour le séjour temporaire. Les conditions DEFCON retombent à la fin du voyage ; un script peut aussi terminer le séjour lors de l'ouverture des portes.
- **Portail naturel** : saut sur la plateforme, destination aléatoire parmi les portails naturels actifs du canal. Les conditions `energy` et `denizen` doivent être actives. Leur activation est raccordable au script de la carte : ce changement n'ajoute pas une entité Denizen ou un objet Turbine.
- **Fusée** : destination fixe avec courant et recharge. La condition `rocket_test` bloque le départ tant qu'elle est active ; le script de la carte décide quand le test commence et finit.

La page **Spawner channels** applique les actions d’une plateforme Source au départ. Une Réception peut appliquer des actions à l’arrivée. Si la destination est une Source avec sa propre destination (liaison aller-retour libre), ses actions sont réservées au départ de retour et ne sont pas exécutées immédiatement à l’arrivée. La durée est en secondes : zéro conserve l’action jusqu’au reset de partie ; une durée positive restaure l’état précédent. Un changement explicite ultérieur, par une porte ou un script, a priorité.

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

## Redistribution lors des changements de canaux

Dans la page **Canaux de spawner**, activer **Redistribuer les ennemis** retire les ennemis vivants des spawners qui deviennent inactifs, sans mort ni récompense. Le WaveManager conserve leur type et leurs PV et les fait réapparaître progressivement sur les spawners actifs, à raison d’un ennemi par demi-seconde. Un spawner du même type est préféré ; sinon, un spawner zombie actif peut recevoir l’ennemi. Les limites de la vague continuent de s’appliquer. Sans destination disponible, les ennemis restent en attente et ne sont pas comptés comme éliminés. La restauration des canaux temporaires déclenche aussi la redistribution inverse.

Exemple : sur la plateforme de la zone temporaire, activer `1`, désactiver `0,100`, cocher **Redistribuer les ennemis**. Pour un séjour de 30 secondes suivi de la charge de retour de 3 secondes, utiliser une durée des canaux de 33 secondes. La plateforme de départ/retour garde ses listes de canaux vides.

La recharge partagée concerne uniquement les plateformes réellement utilisées par le voyage : départ et arrivée pour un trajet direct ; les éventuelles salles secrètes et le retour sont ajoutés pour un séjour temporaire. Les autres plateformes du même canal restent disponibles. Un départ annulé ne met en recharge que sa source. Désactiver cette option conserve une recharge uniquement sur la source.

L’éditeur de téléporteur enregistre les modifications valides en quittant avec Échap ou avec Save. Cancel abandonne explicitement les modifications. Une confirmation serveur nomme le téléporteur sauvegardé ; une distance trop grande ou un voyage en cours affiche un refus. Les actions de canaux restent propres à chaque plateforme. Pour Lune → Eau → Lune : Source Lune, Enable channels=1 et Disable channels=0 ; Source Eau, Enable channels=0 et Disable channels=1 ; Redistribute enemies activé sur les deux, Channel duration=0.


## Players outside navigation columns

Player pursuit first checks the same allowed-block / limit-block column rules as the walk evaluator, before allocating a pathfinding region. Positions outside those columns do not trigger repeated broad searches while a survivor or noclip player moves off-map. Enemies can select another reachable player; returning to a valid column allows normal pursuit to resume without relocation. Supported platform edges and traversal endpoints remain valid. Column admission is shared across enemies for one server tick and invalidated on terrain notifications; unloaded columns are never loaded just for this check.
