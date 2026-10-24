# Révision des ennemis de Stratholme

Les acteurs utilisent maintenant RouteNavigation, DoorwayMoveControl, les path/limit blocks et les passages à sens unique. Les coups utilisent la distance aux collisions et le système FixedPlayerDamage du mod. Rivendare et Balnazzar évitent de recommencer une incantation lorsqu'ils sont à portée de mêlée. Les profils ajoutent `meleeReach`, `knockbackResistance` et `sounds`.

Balnazzar est à l'échelle 2,1, avec les jambes écartées et une marche adaptée. Rivendare est attaché au cheval, dont le corps, les jambes, le crâne, le cou, la selle et la queue sont reconstruits. Crypt Fiend a un thorax reliant la tête au corps. Les chutes des quatre modèles sont calculées autour des pieds et corrigées pour rester sur le sol. Trois variantes écarlates portent épée/bouclier, hache et hallebarde. Les mouvements adaptés sont générés par `polish_map_actors.py` ; les pistes WoW compatibles restent utilisées pour les ailes.

Le catalogue expose aussi Goule, Zombie du Fléau, Squelette, Abomination et Geist à partir des modèles existants. Les définitions de boss restent dans la map. Un œuf générique enregistré sert tous les profils avec un identifiant NBT ; des variantes nommées apparaissent dans le créatif et le bouton « Obtenir un œuf » en ajoute un à l'inventaire. Les menus courants utilisent des formulaires et un sélecteur de noms. Le code est dans une vue avancée, avec sélection corrigée, coloration, ligne active et numéros de ligne.

La nécropole est reconstruite à partir des triangles et UV du WMO WoW local 111729 et de ses cinq groupes. Le modèle est réduit uniformément sur une grille de 42 cellules puis fusionné par matière en 5 593 cubes ; les textures donnent les matières et les runes vertes. Cette géométrie statique est chargée une fois depuis les ressources et n'alourdit pas les paquets de configuration. La décoration reste un objet du ciel.

Les fichiers de voix sont extraits localement et restent dans Stratholme. Les voix nommées Balnazzar disponibles viennent de Retail ; le son d'incantation vient de Classic. La provenance exacte figure dans `assets-source/26.7/map-actors/stratholme-audio-provenance.json`.

Validation de cette révision :

- 46 contrôles fonctionnels serveur, dont catalogue, invocations, sauvegarde, quota, marqueurs et rotation musicale.
- 33 contrôles de combat : déplacement réel sur zone marquée, refus d'une destination non marquée, acquisition dans le rayon, dégâts de mêlée et immunité au recul ; cinq types testés, callbacks Lua actifs.
- 31 contrôles client natifs, dont œufs dans l'inventaire créatif, synchronisation audio, formulaires, sélection d'une goule, éditeur et animations. Captures des quatre morts inspectées.
- Démarrage du JAR remappé sur un serveur Forge normal sans TaCZ : attributs, dimensions du boss, état d'incantation, animations et armes vérifiés.

Les tests ne remplacent pas l'équilibrage dans les salles de Stratholme. Les entités déjà présentes conservent leur ancien profil jusqu'à leur remplacement ; aucun marqueur ni région de la sauvegarde n'est déplacé par l'installation.
