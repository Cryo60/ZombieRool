# Objets dâ€™ambiance

Inventaire crÃ©atif ZombieRool â†’ **Ambiance**. Clic droit en crÃ©atif sur le bloc pour le configurer. Les effets sont dÃ©coratifs : aucune collision dâ€™avion, aucun dÃ©gÃ¢t, aucun projectile, aucun feu posÃ© et aucune entitÃ© Ã  simuler. Le gÃ©nÃ©rateur doit rester dans un chunk chargÃ© ; il ne force pas de chargement.

## DCA

Poser **DCA dÃ©corative**, cliquer **Orienter la DCA dans le monde**, puis, une fois le menu fermÃ©, viser librement un bloc ou le ciel et faire un clic droit. Lâ€™outil liÃ© au canon est sÃ©lectionnÃ© automatiquement ; le prÃ©cÃ©dent objet est conservÃ©. Il est possible de se dÃ©placer et de refaire un clic pour corriger la cible. RÃ©gler ensuite puis lâ€™intervalle entre salves en ticks (20 ticks = une seconde ; 40 par dÃ©faut). Le Flak 88 mm possÃ¨de un seul tube. Chaque salve fait reculer le tube, Ã©met une brÃ¨ve lueur et de la fumÃ©e, puis plusieurs courts traceurs lumineux espacÃ©s : ils se dÃ©placent et sâ€™Ã©teignent entre **30 et 40 blocs de hauteur au-dessus du canon** par dÃ©faut. Ces deux hauteurs sont rÃ©glables ; le trajet visible est bornÃ© Ã  160 blocs pour les visÃ©es presque horizontales. La cible sert Ã  orienter le canon, sans dessiner un rayon continu jusquâ€™Ã  elle. Le son de tir provient du son de DCA dÃ©jÃ  conservÃ© dans les ressources dâ€™Origins, raccourci et attÃ©nuÃ© pour cet usage.

Lâ€™alimentation est requise par dÃ©faut : redstone ou activator local, ou mode global/local via le bouton habituel. **Sans alimentation** la laisse active en permanence. Le volume initial est **0,2**. Il reste rÃ©glable ; 0 coupe le son.

Le menu **Tirs** propose trois comportements : **réguliers**, **variés** (rafales, tirs soutenus et pauses), ou **suivi des avions et dirigeables**. Le mode varié est le défaut, y compris pour les anciennes DCA sans réglage de mode. Chaque position possède son propre rythme déterministe : les canons ne partent pas tous ensemble. Le suivi choisit aléatoirement parmi les aéronefs décoratifs actifs à moins de 192 blocs, sans donner la priorité au plus proche. Chaque canon conserve son choix plusieurs secondes, anticipe le déplacement avec un écart de visée et alterne les tirs et les pauses avec des rythmes propres. Il cesse le feu sans cible. La rotation est limitée à 22 degrés par seconde par défaut, l’élévation à 65 % de cette vitesse ; le menu permet de régler la vitesse. Les poses sont interpolées entre les ticks. La recherche est mise en cache quatre ticks ; aucune entité ni projectile physique n’est créé. Le son, le recul et l’événement Lua suivent les mêmes décisions de tir.

## Avions

Poser **GÃ©nÃ©rateur dâ€™avions**. Par dÃ©faut, quatre avions apparaissent autour de 50 blocs au-dessus et suivent des trajectoires distinctes. Ils tirent lorsquâ€™un autre avion se trouve devant eux. Altitude, rayon, vitesse, Ã©chelle, nombre et frÃ©quence des tirs sont rÃ©glables. Les moteurs sont des boucles sonores spatiales attachÃ©es aux avions ; les tirs ont aussi un son. Le niveau sonore varie avec la distance, indÃ©pendamment de la direction du regard. Les boucles sâ€™arrÃªtent quand le gÃ©nÃ©rateur est coupÃ©, retirÃ© ou dÃ©chargÃ©. Le volume initial est **0,1**. Il reste rÃ©glable ; 0 coupe tous ses sons.

La variante **Dirigeable** suit un **passage en ligne droite, Ã  altitude constante**, sans les virages et le roulis des biplans. RÃ©gler **Direction du passage** en degrÃ©s (0 = sud, 90 = est, 180 = nord, -90 = ouest), **Longueur du passage**, vitesse et altitude. Le dirigeable continue au-delÃ  de la zone de passage sur au moins 256 blocs, puis sâ€™efface progressivement au loin. **Boucle** rÃ©pÃ¨te le passage dans le mÃªme sens, avec une arrivÃ©e progressive depuis lâ€™horizon ; **Une fois** termine seulement aprÃ¨s cette sortie prolongÃ©e. Sa boucle de moteur est plus grave et il ne tire pas.

Les modÃ¨les sont des crÃ©ations du mod. Aucune physique dâ€™IA ni de combat rÃ©el nâ€™est exÃ©cutÃ©e. Le gÃ©nÃ©rateur est invisible hors du mode crÃ©atif/Ã©dition.

## Tornades

Poser **GÃ©nÃ©rateur de tornades**. Variante standard ou feu ; rÃ©gler vitesse, Ã©chelle et nombre (jusquâ€™Ã  quatre tornades visibles). Lâ€™outil de trajet liÃ© est fourni uniquement par le menu du gÃ©nÃ©rateur ; il nâ€™y a plus dâ€™outil vierge dans lâ€™inventaire. Cliquer **Obtenir lâ€™outil de trajet**, puis se dÃ©placer dans la carte et faire un clic droit au sol pour poser les points. Un clic dans le vide utilise la position du joueur ; Maj + clic retire le dernier point. Le menu propose aussi **Ajouter ma position au trajet** pour les points proches. Jusquâ€™Ã  32 points absolus Ã  moins de 192 blocs du gÃ©nÃ©rateur ; **Retirer le dernier point** permet dâ€™annuler. Le mouvement suit les segments Ã  la vitesse configurÃ©e. Boucle facultative : sinon la tornade reste au dernier point. Sans point, elle reste au-dessus du gÃ©nÃ©rateur. Aucun dÃ©gÃ¢t ni propagation du feu.

## Lua et addons

`zr:placeMapDevice(x,y,z,"DCA","north")` ; types Ã©galement disponibles : `AIRCRAFT`, `TORNADO`.

`zr:getAmbientDevice(x,y,z)` retourne la configuration complÃ¨te, y compris la table `scenery`. `zr:configureAmbientDevice(x,y,z,changes)` fusionne et valide les rÃ©glages. `zr:setAmbientDeviceEnabled(x,y,z,enabled)` active/dÃ©sactive un objet.

Champs communs : `enabled`, `requires_power`, `interval`, `volume`. Table `scenery` : `variant` (`normal`, `fire`, `airship`), `altitude`, `radius`, `heading`, `pathLength`, `soundRange`, `tracerMinHeight`, `tracerMaxHeight`, `fireMode` (`regular`, `varied`, `tracking`), `trackingTurnSpeed` (degrés/seconde, 5–90), `flightSpeed`, `size`, `amount`, `targetX`, `targetY`, `targetZ`, `loop`, `route` (liste de tables `{x=..., y=..., z=...}`). Les nombres non finis sont rejetÃ©s ou ramenÃ©s aux valeurs par dÃ©faut ; dimensions et listes sont bornÃ©es.

Ã‰vÃ©nements : `OnAmbientDeviceState(x,y,z,kind,active)` et `OnAmbientDcaShot(x,y,z,targetX,targetY,targetZ)`.

### ModÃ¨les dÃ©coratifs

La DCA reprend la silhouette du 88 mm Flak allemand : affÃ»t cruciforme, bouclier fixe en Ã©lÃ©vation, tube unique orientable, culasse et volant de pointage. Les biplans ont des ailes nervurÃ©es, des haubans, un cockpit ouvert, un train et une hÃ©lice animÃ©e. Le dirigeable utilise une enveloppe facettÃ©e avec nervures, empennages, nacelle vitrÃ©e et quatre moteurs Ã  hÃ©lices animÃ©es. Ces gÃ©omÃ©tries sont crÃ©Ã©es pour le mod et partagÃ©es en mÃ©moire entre les gÃ©nÃ©rateurs.

### Rendu et shaders

Les surfaces opaques utilisent le rejet des faces arriÃ¨re ; chaque avion est testÃ© contre le champ de vision avant son dessin. Les fumÃ©es, flammes et traceurs dÃ©coratifs sont exclus de la passe dâ€™ombres dâ€™Iris/Oculus, en conservant les ombres des modÃ¨les opaques. Les anneaux des tornades ne recrÃ©ent plus leurs tableaux UV et vecteurs pour chaque face. Les aperÃ§us des Ã©quipements portables sont dessinÃ©s aprÃ¨s le ciel et le traitement des shaders, avec une source de sommets sÃ©parÃ©e et le test de profondeur conservÃ©.

Le post-traitement global nâ€™exÃ©cute plus les calculs de salissure, grain et correction gamma lorsquâ€™ils sont dÃ©sactivÃ©s. Une correction colorimÃ©trique entiÃ¨rement neutre Ã©vite sa passe de copie, et le rendu tardif de sang/particules est omis quand il nâ€™y a rien Ã  dessiner. Les sons suivent le curseur Ambiance de Minecraft et le volume propre du gÃ©nÃ©rateur.

Les nouveaux volumes et hauteurs par dÃ©faut sâ€™appliquent aux gÃ©nÃ©rateurs nouvellement posÃ©s. Les rÃ©glages explicites des cartes existantes restent conservÃ©s. Les traceurs utilisent une texture blanche uniforme et une Ã©paisseur minimale de deux pixels pour le cÅ“ur et quatre pour le halo, afin de rester lisibles sous le filtrage temporel des shaders.

Les dessins des Ã©quipements sur les armes murales sont rasterisÃ©s une seule fois en silhouettes et images dâ€™achat, puis conservÃ©s dans un cache bornÃ© (32 types). Le rendu des Ã©quipements rÃ©els et leur gÃ©omÃ©trie restent inchangÃ©s. Le calcul des icÃ´nes se fait en arriÃ¨re-plan aprÃ¨s une copie des donnÃ©es du modÃ¨le ; la recharge des ressources invalide ce cache.

Les traceurs, fumées et tornades sont dessinés après la composition du ciel et des shaders, avec le test de profondeur du monde conservé. Cela empêche le ciel de recouvrir ces effets translucides, y compris sans shader.

Le cœur et le halo des tirs utilisent un matériau additif lumineux, sans assombrissement par les normales du modèle. Ils conservent le brouillard et les occultations par le monde.

La passe tardive échantillonne séparément la profondeur du monde et celle des mains/armes. Les tornades, fumées et traceurs sont rejetés derrière la géométrie de premier plan, même lorsque le shader a remplacé la profondeur du framebuffer final. Ces copies ne sont demandées que si des effets visibles ou le traitement d’atmosphère en ont besoin.

Les cadres des zones d’atmosphère apparaissent uniquement pendant leur édition : bloc de zone en main, ou zone visée. Le passage en créatif seul ne les affiche plus. Leur caméra est reconstruite dans la passe de présentation, pour éviter les transformations de matrice propres aux shaders.
