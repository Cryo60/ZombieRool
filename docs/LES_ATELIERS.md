# Les Ateliers — Pièces de survie

Petite carte industrielle ZombieRool pour 1 à 4 joueurs, créée pour essayer le système de buildables en jouant des manches Zombies.

La sauvegarde est installée dans `run/saves/ZombieRool - Les Ateliers v2`. L’archive transférable est `build/buildable-demo-v2/Les-Ateliers-Buildables-v2.zip`. Utilisez le JAR `build/libs/zombierool-26.7-dev.jar`, Minecraft 1.20.1, Forge et Player Animator. TacZ est facultatif.

Ouvrez **Les Ateliers — Pièces de survie** en solo, puis lancez `/zombierool start`. La touche d’action est celle configurée dans le mod, F par défaut. Pour arrêter : `/zombierool stop`.

- Atelier du nord : canon, culasse et crosse donnent une MP5. Un seul exemplaire pour l’équipe. Le canon a deux emplacements possibles, choisis aléatoirement.
- Atelier de l’ouest : établi libre. Porte, cadre et poignée donnent un bouclier récupérable à nouveau après sa perte.
- Atelier de l’est : batterie, bobine et fusible donnent un relais fixe. Récupérez le résultat pour ouvrir le local technique au nord-est ; sa lampe s’allume aussi.
- Local technique : interrupteur du courant, Juggernog et Pack-a-Punch. Quick Revive et deux armes murales se trouvent dans la cour de départ.

Une seule pièce est transportée à la fois. F sur une autre pièce échange les deux sur place. Tab montre toutes les pièces et leur état. L’assemblage est partagé entre les joueurs. À la fin de la partie, les pièces reviennent et le local technique se referme.

La difficulté démarre doucement : cinq zombies de base, huit entrées, chiens toutes les cinq manches, sprinteurs à partir de la sixième. Les quatre points d’arrivée se trouvent dans la cour sud. Les huit entrées Zombies ont été vérifiées avec le véritable calcul de chemins du mod.

Pour modifier la carte, passez en créatif et faites un clic droit sur un établi ou un support de pièce, hors partie. Les trois tables sont de vrais établis libres de deux cases. Les supports affichent directement les modèles des pièces. Les recettes sont enregistrées dans les données du monde. `zr_scripts/main.lua` contient le petit objectif du relais. Voir le [guide des buildables](BUILDABLES.md).

Validation : compilation complète du mod, 123 vérifications du système en serveur Forge isolé, puis 33 vérifications sur une copie de cette sauvegarde avec démarrage d’une partie, construction des trois objets, activation du relais et remise à zéro. Le client Minecraft passe aussi 488 vérifications, avec des captures des modèles, des deux éditeurs, de Tab, de la barre et du résultat posé sur la table.

La génération est reproductible avec `tools/build-buildable-demo.py` et `tools/BuildableDemoMap.java`, après préparation du runtime par `tools/tests/run-buildable-tests.py`. Le générateur refuse de remplacer une sauvegarde existante. Le test de partie utilise `tools/tests/playtest-buildable-map.py` et travaille sur une copie.
