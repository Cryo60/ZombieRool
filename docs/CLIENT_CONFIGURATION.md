# Configuration client ZombieRool

Depuis le menu principal : **MODS â†’ ZombieRool â†’ Config**. Le mÃªme Ã©cran est disponible dans les options Minecraft.

- **Apparence** : Halloween, modÃ¨les classiques, animation de lâ€™arsenal et gore rÃ©duit.
- **HUD** : notifications XP et compteur dâ€™ennemis restants.
- **En ligne** : requÃªtes rÃ©seau, prÃ©sence Discord et identifiant dâ€™application Discord.

**Enregistrer** applique les changements et les Ã©crit dans `config/zombierool-client.toml`. **Annuler** ou Ã‰chap abandonne les changements. **Valeurs par dÃ©faut** rÃ©initialise le brouillon ; il faut ensuite enregistrer pour appliquer les valeurs.

En partie, **Configuration de la map** ouvre les rÃ©glages du monde pour les opÃ©rateurs. Ces rÃ©glages restent propres au monde et sont vÃ©rifiÃ©s cÃ´tÃ© serveur.

Dans **Options â†’ Controls â†’ ZombieRool**, `Equipment` utilise **X** par dÃ©faut, `Tactical Grenade` **Y** et `Lethal Grenade` **G**. Les trois emplacements sont indÃ©pendants et leurs compteurs sont affichÃ©s dans le HUD. C4 : maintenir puis relÃ¢cher pour lancer, appuyer de nouveau pour dÃ©toner ses charges. Claymore et Bouncing Betty : maintenir pour prÃ©parer, relÃ¢cher pour poser devant soi. Flash et Pheromone : maintenir puis relÃ¢cher pour lancer. Semtex utilise lâ€™emplacement lÃ©tal ; sa mÃ¨che de deux secondes dÃ©marre au lancer.

Le map maker choisit les Ã©quipements de dÃ©part dans la configuration de la map. Les items de la catÃ©gorie Weapons permettent de sÃ©lectionner un emplacement par clic droit en crÃ©atif et de configurer les achats muraux ou les rÃ©compenses. Les achats donnent des charges dans lâ€™emplacement, sans ajouter dâ€™objet Ã  lâ€™inventaire du joueur. Max Ammo remplit aussi ces deux nouveaux emplacements. Les cartes existantes gardent leurs rÃ©glages et commencent sans Ã©quipement supplÃ©mentaire.

La prÃ©sence Discord se met Ã  jour aprÃ¨s lâ€™enregistrement. Un identifiant vide la dÃ©sactive.

## Health display and injury feedback

The HUD tab contains **Hide health display** (on by default: the health panel starts hidden). This hides the health and absorption panel only. During a match, damage still produces a brief camera shake, progressive blood at the screen edges, a subtle darkening, and impaired hearing at critical health. Heartbeat, tinnitus, and badly-wounded audio fade out with recovery and stop after death, disconnection, or the end of the match. Suit audio filtering remains compatible. Normal combat hits no longer knock players backward; scripted pulls and launches retain their movement.
