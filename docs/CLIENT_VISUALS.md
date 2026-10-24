# Visuels client



Le menu de configuration client propose Halloween (`Automatique`, `Toujours activé`, `Toujours désactivé`) à chaque joueur, sans permission opérateur. Le changement s'applique aux zombies déjà présents. Les costumes sont fournis aux couches de rendu ; ni l'inventaire des entités, ni leurs attributs, ni les autres clients ne sont modifiés. Les crawlers utilisent leur apparence Halloween côté client. Les morceaux et particules de sang deviennent des sucreries ; les flaques et blessures visibles sont masquées. Le réglage de gore réduit reste disponible.



`Ennemis restants` est désactivé par défaut dans `zombierool-client.toml` (`showEnemiesRemaining`). Une fois activé, un compteur rouge apparaît à droite de la hotbar, ou juste au-dessus si la largeur disponible est insuffisante. Il inclut les ennemis encore à apparaître dans la manche. Le serveur fournit le total ; Mr.Chief et les alliés n'y contribuent pas.



Lua : `ZombieroolAPI:getRemainingEnemies()` expose le même total, avec zéro hors partie. Les paramètres et événements de Mr.Chief sont documentés dans [MR_CHIEF.md](MR_CHIEF.md).



Les fonds de menu utilisent trois captures intégrées depuis `run/screenshots`, avec déplacement lent et fondu. Deux images au maximum sont conservées en mémoire graphique. En partie, le monde reste visible sous l'habillage sombre. Les menus vanilla et les menus du mod partagent l'habillage des boutons, curseurs et listes. Les slots d'inventaire, icônes et outils d'édition conservent leurs fonctions.



La fiesta est synchronisée par l'horloge du serveur : poses face au joueur, tours lents, pauses, tours rapides. Les entités vivantes, dont les gardes Royal Beer, ainsi que les autres entités rendues par le répartiteur Minecraft participent. Il s'agit d'une animation visuelle ; elle ne modifie pas leur IA ou leurs dégâts. La musique garde sa portée locale de 32 blocs et sa durée de 60 secondes.



Le serveur et les clients doivent utiliser le même JAR.


Le piège électrique affiche des arcs bleus ramifiés en géométrie lumineuse, avec halo cobalt, corps cyan et cœur pâle. Le tracé respecte les obstacles utilisés par les dégâts du piège et se renouvelle dix fois par seconde.


Chaque session de développement lancée via Gradle dispose de son propre dossier de ressources. Une compilation ultérieure ne peut plus supprimer les fichiers audio lus en streaming par le client déjà ouvert.

Les 84 camos TACZ intégrés ont chacun un profil explicite dans TacZCamoOverlay.patternScale. La projection reste indépendante des UV des gunpacks et attachée à la pose de repos du modèle. Densités : 2 répétitions par bloc du modèle pour les grandes illustrations et effets fluides (Galaxy, Cryo, Black Ice, Hyper Beast, Marmot), 3 pour les particules et symboles répétés (Matrix, Bubbles), 4 pour les bandes et motifs géométriques, 5 pour les taches moyennes, 6 pour les emblèmes compacts (Lordaeron et les autres blasons/logos) et les motifs fins (Woodland, CADPAT, Flecktarn), 8 pour Wesker Coat et le reflet Pack-a-Punch. Le repli à 6 concerne uniquement les camos supplémentaires des packs de ressources. Le filtrage lissé et les animations sont conservés. Le test TacZCamoProjectionTest contrôle la couverture des textures présentes et la stabilité de leur projection pendant les transformations. Les proportions visuelles restent à vérifier en jeu selon le gunpack.


## Camos ajoutés depuis « nouveau camo »

15 textures : six variantes Caméléon (améthyste/émeraude, cuivre/turquoise, inferno, jade/or, océan/violet, sakura/cyan), Girafe, Léopard, Panda, Zèbre, Fleurs de cerisier animées, Halloween animé, Œil horrifique animé, Sponsors animés et Grotog. Les variantes animées conservent les fichiers `.mcmeta` originaux, leurs cadences et l’interpolation de Grotog. Les camouflages sont enregistrés dans la carrière/boutique, l’atlas des armes natives et le rendu de surface TACZ. Le motif Cherry Blossom statique existant reste distinct de sa nouvelle variante animée.

### Fitted TACZ artwork and local career catalog

Grotog, Halloween and Horror Eye use one illustration fitted to the paintable gun bounds in bind pose, with clamped UVs. Both Bedrock cubes and optional polygon meshes use this mapping. Animations retain the same projection; other patterns keep their existing tiling.

The career menu reads local gun-pack index metadata before a world is opened. Preview stacks use local client displays, without changing server gameplay availability.

Sponsor, Grotog, Halloween and Horror Eye are exclusive global camos, outside normal purchases. Display names omit animation suffixes; asset and unlock IDs stay stable.


## Native 2D weapon coatings

Native weapon sprites use composed textures on their original front/back faces, with original edge geometry. The bundled META-INF/zombierool/visuals/sprite-masks.bin.gz covers 101 weapon sprites and ammunition variants. Masks are prepared by tools/prepare-sprite-camo-masks.py using weapon families, muzzle position, contours, dark seams and energy accents; no player authoring step is needed. Source luminance preserves shading beneath the coating, while protected pixels and alpha remain unchanged. Built-in family settings (repeat, rotation, offsets, fitted artwork, background and opacity) also affect native sprites. Repeats are divided by three for the flat sprite silhouette to keep small-screen motifs legible. Animated camos use the same pattern frames as TACZ, with composed updates limited to one per game tick. Renderers using raw quads instead of Forge item passes retain the original sprite.

The five previously 1024-pixel sprites (HK21, M1 Carbine, Olympia, SVT-40, M1014) are reduced to the usual 256 x 256 size. The small 16-pixel Superbow sprite keeps its existing size. Resource-pack replacements with different dimensions use a conservative silhouette fallback. Prepared masks can be refined later without modifying the source weapon textures.


Packed sprite masks and the binary public verification key live under META-INF/zombierool, outside the resource-pack asset namespace. This is organization and compact storage, not encryption. The public key is intentionally distributable and cannot create signatures. Existing issued vouchers retain the same key and remain valid. The creator private key is never moved or regenerated.


The Camo Studio is retired. Camo appearance settings now come directly from CamoAppearance.Profile and TacZCamoOverlay.patternScale in code, with all 99 former JSON profiles checked for exact equivalence. No appearance JSON or compressed appearance file is shipped. The Creator home keeps the reward-code generator; old studio pages and its save endpoint are disabled. Packed sprite masks and the public verification key remain bundled under META-INF/zombierool.
