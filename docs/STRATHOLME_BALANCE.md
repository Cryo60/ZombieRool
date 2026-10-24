# Progression dans Stratholme

Le réglage d’exploration conserve les spawners placés, leurs groupes, les factions et toutes les armes murales. Il réduit les renforts ajoutés pendant la traversée :

- Fléau : 1–2 apparitions après 30–45 secondes, puis toutes les 25–40 secondes ; plafond de 4 ennemis vivants par joueur et 16 pour toute l’équipe.
- Risen : plafond de 4 ennemis vivants par joueur ; 15–30 secondes entre apparitions réussies. Le Fléau reste suspendu pendant cette rencontre.
- Vagues : intensité normale, premiers sprinteurs à la manche 10 avec 15 % de probabilité.
- Boss de manche : première apparition à la manche 6, puis intervalle de 4 manches. Les boss placés sur la map ne sont pas déplacés.

Ces valeurs sont appliquées au démarrage par `stratholme_balance.lua`. Elles constituent un premier réglage pour les armes médiévales ; une traversée complète par un joueur reste nécessaire pour affiner les goulets et le prix des portes.

La nuke ne tue que les ennemis suivis par la vague. Les acteurs placés ou issus d’embuscades, les ennemis non suivis et les boss restent vivants. Le gain collectif de 400 points est conservé.

L’ouverture du menu des spawners utilise des réglages légers : les modèles, animations et alternatives d’apparence restent exclus du catalogue envoyé au client. Le défaut précédent reproduisait un dépassement de la limite NBT réseau ; le catalogue corrigé du test fait environ 29 Ko.
