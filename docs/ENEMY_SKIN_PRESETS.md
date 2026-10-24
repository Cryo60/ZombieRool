# Apparences et galerie de contrôle

Map Config → Ennemis propose quinze ensembles de zombies et l’ensemble classique.
Le défaut est Soldats allemands. Les chiens suivent automatiquement la génération
source du preset, ou un choix séparé. Les identifiants internes des sources restent
stables ; les libellés du menu sont descriptifs.

Les 61 conversions de zombies utilisent une texture 128×128, sans réduction globale
de palette ni bande noire peinte sur les yeux. Les quatre chiens convertis sont en
128×64. Les cinq skins classiques et le chien classique restent disponibles.
Les yeux des nouvelles variantes sont une couche lumineuse étroite, colorable ;
les yeux personnalisés des maps restent prioritaires.

Les casques, casquettes et chapeaux sont associés explicitement aux variantes. Les
formes adaptées à la tête cubique sont construites dans ZombiePresetGearLayer ;
le casque spatial conserve son maillage importé. La perte de tête/crâne masque
l’accessoire. Les apparences attribuées sont synchronisées dans CustomSkin et
conservées au rechargement, y compris sur les membres détachés. Les skins
personnalisés continuent de suivre les règles d’override de la map.

La galerie contient 66 zombies et cinq chiens, immobiles, invulnérables et étiquetés.
Elle est en créatif et autorise les commandes. Chaque ligne correspond à un ensemble.
Les spécimens marqués zr_skin_gallery et explicitement NoAI/Invulnerable sont
préservés par le nettoyage de fermeture de session. Ce monde sert à vérifier le
rendu réel ; les planches générées hors jeu ne constituent pas une validation visuelle.

Sources locales : dossiers T4/T5/T6 de l’utilisateur. La sélection et les empreintes
sont dans assets-source/26.7/enemy-skins. Ces conversions et adaptations ne confèrent
pas de droits de redistribution des ressources originales. Aucun envoi public n’est
réalisé par ces outils.

Reproduction : tools/extract-enemy-presets.py, tools/prepare-enemy-presets.py et
 tools/prepare-preset-headgear.py. Le générateur du monde est le mod de test isolé
 tools/tests/SkinGalleryWorldBuilder.java ; il n’est pas inclus dans le JAR distribué.
Le contrôle NBT est tools/tests/check-skin-gallery-world.py.
