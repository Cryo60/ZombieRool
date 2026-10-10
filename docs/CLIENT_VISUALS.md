# Visuels client

Le menu de configuration client propose Halloween (`Automatique`, `Toujours activé`, `Toujours désactivé`) à chaque joueur, sans permission opérateur. Le changement s'applique aux zombies déjà présents. Les costumes sont fournis aux couches de rendu ; ni l'inventaire des entités, ni leurs attributs, ni les autres clients ne sont modifiés. Les crawlers utilisent leur apparence Halloween côté client. Les morceaux et particules de sang deviennent des sucreries ; les flaques et blessures visibles sont masquées. Le réglage de gore réduit reste disponible.

`Ennemis restants` est désactivé par défaut dans `zombierool-client.toml` (`showEnemiesRemaining`). Une fois activé, un compteur rouge apparaît à droite de la hotbar, ou juste au-dessus si la largeur disponible est insuffisante. Il inclut les ennemis encore à apparaître dans la manche. Le serveur fournit le total ; Mr.Chief et les alliés n'y contribuent pas.

Lua : `ZombieroolAPI:getRemainingEnemies()` expose le même total, avec zéro hors partie. Les paramètres et événements de Mr.Chief sont documentés dans [MR_CHIEF.md](MR_CHIEF.md).

Les fonds de menu utilisent trois captures intégrées depuis `run/screenshots`, avec déplacement lent et fondu. Deux images au maximum sont conservées en mémoire graphique. En partie, le monde reste visible sous l'habillage sombre. Les menus vanilla et les menus du mod partagent l'habillage des boutons, curseurs et listes. Les slots d'inventaire, icônes et outils d'édition conservent leurs fonctions.

La fiesta est synchronisée par l'horloge du serveur : poses face au joueur, tours lents, pauses, tours rapides. Les entités vivantes, dont les gardes Royal Beer, ainsi que les autres entités rendues par le répartiteur Minecraft participent. Il s'agit d'une animation visuelle ; elle ne modifie pas leur IA ou leurs dégâts. La musique garde sa portée locale de 32 blocs et sa durée de 60 secondes.

Le protocole réseau est 5. Le serveur et les clients doivent utiliser le même JAR. Ces changements sont locaux et non publiés.

Le piège électrique affiche des arcs bleus ramifiés en géométrie lumineuse, avec halo cobalt, corps cyan et cœur pâle. Le tracé respecte les obstacles utilisés par les dégâts du piège et se renouvelle dix fois par seconde.

Chaque session de développement lancée via Gradle dispose de son propre dossier de ressources. Une compilation ultérieure ne peut plus supprimer les fichiers audio lus en streaming par le client déjà ouvert.
