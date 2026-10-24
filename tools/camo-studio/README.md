# ZombieRool Camo Studio

Le dossier « ZombieRool Creator » sur le bureau contient un raccourci vers l’accueil commun du studio et du générateur de codes. Vous pouvez aussi lancer `python studio.py --page /home.html`.

Double-cliquez sur `start.cmd` pour ouvrir directement le studio. Le studio tourne uniquement sur votre PC, sur http://127.0.0.1:8769. Python et un navigateur avec WebGL suffisent. Minecraft peut rester fermé.

- Choisissez un camo et une arme de référence.
- Choisissez un profil partagé (A : emblèmes / Lordaeron, B : effets larges / Supernova, etc.). Modifiez ses curseurs et enregistrez : tous les camos associés reçoivent les nouveaux réglages.
- Pour créer un profil, saisissez son nom et cliquez sur « Créer depuis ces réglages ».
- Pour associer plusieurs camos, ouvrez « Associer plusieurs camos », cochez-les et appliquez le profil. La recherche filtre aussi cette liste.
- « Réglage individuel » permet de détacher un camo d'un profil lors de la sauvegarde.
- Pour un petit emblème : activez « Emblème sur fond uni », réduisez « Taille de l’emblème » (ce curseur active automatiquement le mode emblème) (par exemple 0,25) et positionnez-le avec les décalages.
- Le fond est propre au camo : automatique depuis le coin supérieur gauche de son image, ou couleur choisie manuellement. Changer le profil partagé ne remplace pas les fonds des autres camos.
- « Voir le masque » affiche en bleu les surfaces peintes et en gris celles qui gardent leur texture. Le bouton de texture originale permet de comparer.
- Glissez sur l'arme pour tourner et utilisez la molette pour zoomer.

Les paramètres enregistrés vont dans les ressources du projet et leurs miroirs. Recompilez le JAR pour distribuer le résultat. Les familles restent dans `presets.json`, hors du JAR. L'éditeur est réservé au travail de création et aucun bouton n'est ajouté au jeu des joueurs.

L'aperçu utilise six modèles TACZ de référence avec leur masque de peinture, en pose de repos et sous éclairage neutre. Les shaders et animations du jeu peuvent modifier le rendu final.

## Codes récompenses

Depuis l’accueil, ouvrez le générateur. Cochez plusieurs camos, entrez les ZRF et/ou l’XP, puis générez un ou plusieurs codes. Chaque code contient la totalité des récompenses choisies. L’UUID et les dates de validité sont facultatifs. Les codes se copient ou s’exportent en JSON ; la clé privée reste sur ce PC, hors des fichiers du mod. Le nombre de codes ne constitue pas une limite globale d’utilisations. Sans UUID, toute personne possédant le code peut le réclamer sur son profil local.

## Camos aléatoires et recherche

Le générateur permet d'ajouter un nombre de camos aléatoires par code et de choisir leur rareté. Le tirage a lieu à la génération, séparément pour chaque code, sans doublon avec les camos précis du même code. Un code partagé donne donc le même contenu à ses destinataires. Le tirage classique exclut les exclusifs, prestiges et maîtrises ; ces catégories restent sélectionnables explicitement. Le résultat de chaque tirage est affiché avant transmission. La recherche filtre effectivement la liste, sans distinction d'accents, par nom, identifiant ou rareté.
