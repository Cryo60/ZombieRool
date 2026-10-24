# Crédits audio

Sons téléchargés le 10 octobre 2026 depuis les pages de leur auteur, **Kenney**. Les trois packs et leurs fichiers `License.txt` indiquent **Creative Commons Zero (CC0)**. Les licences sont incluses dans le JAR sous `assets/zombierool/licenses/`. Les archives originales restent dans le dossier privé local `assets-source/26.7/audio/`.

- [Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds) : `forceField_002.ogg` pour le départ d’une âme ; `forceField_000.ogg`, inversé et accéléré, pour son absorption ; trois couches de `forceField_003.ogg` avec hauteurs décalées pour la complétion du coffre. `engineCircular_002.ogg` et `thrusterFire_002.ogg` sont mélangés pour la boucle de câble pendant la tyrolienne.
- [RPG Audio](https://kenney.nl/assets/rpg-audio) : `metalLatch.ogg` pour l’ouverture du coffre ; `metalClick.ogg` pour l’accrochage à la tyrolienne.
- [Impact Sounds](https://kenney.nl/assets/impact-sounds) : `impactMetal_light_000.ogg` pour l’arrêt de la tyrolienne.

Tous les exports sont des **Ogg Vorbis mono, 44,1 kHz** pour permettre le placement sonore dans le monde. Les effets ponctuels ont des fondus de bord de 12 ms ; les sources de boucle de cinq secondes sont raccordées par un fondu croisé de 50 ms (export de 4,95 secondes) pour éviter un clic lors de la répétition. Le pic des exports est normalisé à 0,8 avant compression. Les sous-titres utilisent des clés de langue ; les sept événements sont enregistrés dans `sounds.json` et le registre Forge.

Le son de câble suit chaque entité transportée, varie légèrement en hauteur selon sa vitesse et s’arrête à l’arrivée, à la mort, lors d’un changement de monde ou d’un rechargement du moteur sonore. Les effets d’accrochage et d’arrivée sont joués par le serveur pour les joueurs proches.

Reproduction : `python tools/download-267-audio.py`, puis `python tools/prepare-267-audio.py` (dépendances de préparation : NumPy et SoundFile dans `build/audio-python`). Ces dépendances ne sont pas incluses dans le mod. Les sources, durées et empreintes des exports sont conservées dans `assets-source/26.7/audio/prepared-manifest.json`.

## Équipement et M1014 (développement local)

`tools/prepare-equipment-assets.py` construit les modèles et animations, puis convertit les sons en Vorbis mono 44,1 kHz, avec un pic limité à 0,7 et un fondu de 5 ms. Le manifeste local est `assets-source/26.7/audio/equipment-manifest.json`.

L’explosion C4 provient de `sound/exp/c4/c4_det.wav` dans `pluto_t5_full_game/main/iw_28.iwd` ; le souffle Flash de `sound/wpn/grenade/flashbang/fizzle/fizzle.wav` dans `iw_24.iwd`. Les archives du jeu restent intactes. Le format MS-ADPCM des fichiers T5 est vérifié avec le [lecteur T5 de Harmony](https://github.com/g-rl/harmony/blob/main/src/hm/sound/t5.rs). Les sons natifs du M1014 proviennent du pack TacZ déjà installé (`m1014_shoot_3p.ogg`, `m1014_reload_once.ogg`). Ces ressources sont utilisées dans l’environnement local de développement.

La Semtex et la Claymore utilisent uniquement des sons T5 extraits de `D:/Jeux/pluto_t5_full_game/zone/Common/common_mp.ff` : `semtex_charge`, `gren_throw`, `stick_00`, `semtex_alert`, `fly_gear_pull_out_00`, `claymore_plant_00`, `alert_00` et les explosions `explode_00`/`explode_01`. La lecture des seuls assets audio utilise la structure `snd_asset` et le flux zlib documentés dans [OpenAssetTools](https://github.com/Laupetin/OpenAssetTools), puis un conteneur XWMA décodé par FFmpeg. Les modèles et textures T5 ne sont pas exportés. Les noms internes exacts et les empreintes sont conservés dans le manifeste audio.

Les sons provisoires des autres équipements (tissu, pose, adhésion, clic de télécommande et pulsation phéromonale) utilisent les packs Kenney CC0 déjà présents : `clothBelt`, `cloth2`, `impactMetal_light_000`, `metalLatch`, `metalClick`, `forceField_002`.

## Winter’s Howl et V-R11 (développement local)

Les neuf sons proviennent uniquement de l’installation T5 : `common_zombie.ff` (tir, gel, éclatement et gestes de recharge du Winter’s Howl), `zombie_coast.ff` (tir, conversion, explosion et recharge du V-R11), et `iw_23.iwd` (cri humain). `tools/prepare-wonder-assets.py` exporte les seuls fichiers audio nommés dans `assets-source/26.7/audio/wonder-weapons-manifest.json`, avec mono 44,1 kHz, fondus de bord et vérification du pic après compression. Les textures d’armes sont des créations propres à ZombieRool.

## Piège électrique

`zombierool:electric_trap_loop` utilise [Electric Buzz](https://opengameart.org/content/electric-buzz) de themightyglider, publié sous CC0. L’auteur crédite aussi [soundtracvkradio](https://freesound.org/people/soundtracvkradio/sounds/394679/) pour la source originale. Adaptation : mono, normalisation et fondu croisé de 80 ms pour boucler sans coupure.

Cerberus: audio from the local T6 Mob of the Dead sound banks. See assets-source/26.7/audio/cerberus-manifest.json. No game models or textures extracted. SABS/SABL layout verified against https://github.com/master131/Black-Ops-II-Sound-Studio/blob/master/Format/SndAliasBankReader.cs

## Flaques de sang

`zombierool:blood_splat` utilise `sfx/character/gibs/gibs_02.wav`, extrait de l’installation locale T4, dans `zone/english/nazi_zombie_prototype.ff` (Nacht der Untoten). Conversion du XWMA corrigé en OGG Vorbis mono 44,1 kHz. Le son est joué à l’impact des grosses flaques au sol, avec un délai local de 8 ticks pour regrouper les gouttes d’un même jet. Provenance et empreinte : `assets-source/26.7/audio/blood-splat-manifest.json`.


## Buildables

Les trois sons originaux T6 `zmb_building_lp.SL65`, `zmb_build_add_piece.LN65` et `zmb_build_completed.LN65` viennent des banques de l’installation locale `D:/Jeux/pluto_t6_full_game/sound`. Lecture seule, conversion OGG mono, fondus de 12 ms ; le son d’assemblage est limité aux deux secondes du geste. Provenance et empreintes : `assets-source/26.7/audio/buildables-manifest.json`. Extraction reproductible : `tools/prepare-buildable-assets.py`. Aucun modèle ni texture T6 n’est extrait pour cette mécanique.

Zombie Shield: original local T6 TranZit aliases `fly_riotshield_zm_impact_zombies` (4 variants) and `wpn_riotshield_zm_destroy`. Clips read from `D:/Jeux/pluto_t6_full_game/sound/cmn_root.all.sabl`; alias mapping verified in `zmb_classic_transit.all.aliases.csv`. Mono OGG conversion with 5 ms fades. Source and output hashes: `assets-source/26.7/audio/shield-manifest.json`. Reproduce with `tools/prepare-shield-audio.py`.
