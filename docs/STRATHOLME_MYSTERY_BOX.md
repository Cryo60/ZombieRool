# Première Mystery Box de Stratholme

Le script `scripts/stratholme_mystery_box.lua` sélectionne la boîte principale en **-119 92 100** un tick après `OnGameStart`, après la sélection aléatoire du moteur.

Il transmet les autres callbacks de démarrage, ignore les anciens rappels et les parties terminées, et laisse la boîte déverrouillée pour ses déplacements suivants. Aucun changement Java ni réglage JSON n’est nécessaire. Les scripts sont rechargés au lancement de chaque partie.
