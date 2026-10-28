# ZombieRool 26.7 Bêta 6
Minecraft 1.20.1 | Forge | Java 17

## Performances et stabilité

- Réduction des calculs répétés et des allocations mémoire des animations du Fléau et des autres ennemis utilisant les modèles cubiques, en conservant leurs modèles, équipements et poses.
- Les ennemis personnalisés réutilisent leurs données d'animation préparées et leurs modèles visuels compatibles au lieu de les reconstruire régulièrement.
- Les profils d'ennemis personnalisés identiques partagent leurs données décodées côté client, ce qui réduit les chargements répétés à leur apparition ou au retour dans une zone.
- Correction des déconnexions multijoueurs provoquées par des données de modèles personnalisés dépassant la limite NBT des paquets Minecraft.
- Le brouillard opaque simple utilise désormais le rendu natif lorsque ni la hauteur, ni le masque extérieur, ni un ciel personnalisé ou un shader externe ne nécessitent de composition.
- Le brouillard entièrement transparent ne déclenche plus sa passe plein écran inutile.

## Ennemis et déroulement des parties

- Correction des cadavres persistants qui se relevaient ou rejouaient leur animation de mort lorsqu'un joueur revenait dans une zone ou commençait à les recevoir côté client.
- Les joueurs à terre quittent désormais immédiatement cet état à la fin de la partie.
- Ces optimisations ne réduisent ni le nombre d'ennemis, ni leurs dégâts, ni leur ciblage, ni la fréquence de leurs animations.

## Pause et interface

- Ajout de notifications visibles lorsqu'un joueur demande une pause ou une reprise.
- Amélioration de l'affichage des votes de pause et de reprise, ainsi que des états de pause et de reprise de la partie.
- Ajout d'un petit bouton VANILLA PAUSE MENU en bas à gauche, avec accès au menu pause ZombieRool pour revenir. Les libellés de pause/reprise sont en majuscules.

## Scripts et création de maps

- Correction des profils d'ennemis personnalisés formatés qui pouvaient bloquer tout un import et laisser les spawners concernés sans leurs profils.
- Amélioration du démarrage Lua pour les maps effectuant de nombreuses initialisations, afin de ne plus interrompre trop tôt des séquences valides.
- Rétablissement des fonctions Lua sécurisées manquantes pour vérifier les canaux et envoyer les dialogues d'entités à un joueur précis.
- Amélioration du signalement des erreurs de timers dans la console Lua pour faciliter le diagnostic des scripts interrompus.

## Mise à jour

Fermez complètement Minecraft avant de remplacer le JAR. Gardez un seul JAR ZombieRool dans mods et conservez Player Animator.
L'hôte/serveur et tous les joueurs doivent utiliser le même JAR ZombieRool. Rejoindre une map ne charge pas un nouveau JAR.

Les optimisations ciblent des surcoûts mesurés. Elles ne garantissent pas la disparition de tous les blocages côté client.
