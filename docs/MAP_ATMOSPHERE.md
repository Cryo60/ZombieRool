# Skybox, image et ambiances de carte

Ouvrir **Map config â†’ Environnement â†’ Skybox, image et ambiances** en crÃ©atif avec les permissions opÃ©rateur. Les rÃ©glages sont enregistrÃ©s dans le monde, par dimension, et synchronisÃ©s aux joueurs Ã  la connexion et au changement de dimension. Les cartes existantes conservent leur prÃ©sentation : le systÃ¨me est dÃ©sactivÃ© par dÃ©faut.

Les modifications de lâ€™Ã©diteur dÃ©clenchent une prÃ©visualisation locale aprÃ¨s une courte pause de saisie. **TerminÃ©** enregistre pour tout le monde ; **Annuler** restaure les rÃ©glages enregistrÃ©s. Lâ€™onglet **Presets** propose **Nuketown Zombies**, **Moon**, **Nacht der Untoten** et **Die Rise**. Choisir un preset applique immÃ©diatement son aperÃ§u et remplace le brouillon (ciel, image et sons) ; les sources positionnelles se placent autour du crÃ©ateur. **Image** expose les curseurs courants, **Ambiances** gÃ¨re chaque son. **RÃ©glages avancÃ©s** donne accÃ¨s aux identifiants de ressources et aux paramÃ¨tres techniques. Les rÃ©glages de brouillard existants restent dans leur propre menu.

## Ciel personnalisÃ©

DÃ©poser un fichier PNG dans **`<monde>/zombierool/assets/skyboxes/`**, par exemple `evening.png`. Il apparaÃ®t dans **Image â†’ Ciel** Ã  la prochaine entrÃ©e ou aprÃ¨s le rechargement des ressources de carte. Son identifiant Lua est `zombierool:world_sky/evening`. Le transport dynamique envoie uniquement les fichiers concernÃ©s aux joueurs, sans rechargement global de resource pack. Limites : 16 images, 8 Mio par image, 32 Mio au total, dimensions jusquâ€™Ã  4096 et 8 millions de pixels. Les ressources sont libÃ©rÃ©es Ã  la dÃ©connexion.

Dans **AvancÃ©**, le champ texture accepte aussi un identifiant de ressource PNG, par exemple `example:textures/sky/evening.png`. Pour cet identifiant standard, le fichier doit Ãªtre disponible dans le mod, un addon ou un resource pack installÃ© chez les joueurs. Un datapack seul ne fournit pas de textures ni de fichiers audio au client.

- **Cube** : atlas de six carrÃ©s, trois colonnes et deux lignes, dans lâ€™ordre `+X, -X, +Y` puis `-Y, +Z, -Z`. Choisir lâ€™axe vertical Y pour un cubemap standard, Z pour les ressources du jeu source.
- **Panorama** : image Ã©quirectangulaire, normalement au format 2:1.
- RÃ©gler la rotation horizontale, lâ€™inclinaison et la luminositÃ© indÃ©pendamment de lâ€™image du monde.
- Pour un filtrage doux, ajouter un fichier `evening.png.mcmeta` avec `{"texture":{"blur":true,"clamp":true}}`.

Chaque preset utilise les six faces de son cubemap local T5 ou T6. Les noms visibles des presets restent en anglais. Lâ€™alias Lua historique `ashfall` sÃ©lectionne Nuketown Zombies.

Le laboratoire est une petite scÃ¨ne de banlieue destinÃ©e aux tests ; sa gÃ©omÃ©trie nâ€™est pas une reproduction du niveau dâ€™origine. Les alias et chemins originaux sont conservÃ©s dans le manifeste de provenance, pas dans les identifiants du nouveau systÃ¨me.

## Image et shaders

Les contrÃ´les couvrent les multiplicateurs rouge/vert/bleu, lâ€™exposition, le contraste, la saturation, le gamma, la vignette, sa largeur, les salissures et le grain. Les intensitÃ©s des trois derniers effets vont de 0 Ã  1 ; la valeur neutre de saturation, contraste, gamma et teinte est 1, celle dâ€™exposition est 0.

Sans shader pack, le ciel personnalisÃ© est dessinÃ© juste aprÃ¨s le ciel natif, dans le framebuffer actif du monde, avant les bÃ¢timents et les particules translucides. Le sang, la fumÃ©e et les bords transparents se mÃ©langent ainsi au bon fond, sans conserver un anneau du ciel natif. Avec Oculus/Photon, qui reconstruit son ciel en fin de rendu, le ciel personnalisÃ© est composÃ© aprÃ¨s le shader, puis les gouttes de sang sont dessinÃ©es une seule fois sur ce fond en utilisant leur physique, leur texture et la projection du monde habituelles. Les rÃ©glages de couleur, la vignette et les effets dâ€™Ã©cran utilisent ensuite lâ€™image finale aprÃ¨s le rendu 3D et avant le HUD. Le systÃ¨me ne charge pas de post-chain vanilla et ne remplace pas le programme dâ€™Oculus. Les bÃ¢timents et les mains sont conservÃ©s. La vignette de la carte remplace la vignette native quand ce systÃ¨me est actif. DÃ©sactiver le systÃ¨me rÃ©tablit la prÃ©sentation normale du jeu ou du shader pack.

Lâ€™Ã©clairage, les ombres et les rÃ©flexions restent calculÃ©s par le shader pack ; changer le ciel de prÃ©sentation ne rÃ©Ã©crit pas ses paramÃ¨tres internes. Les tests visuels utilisent notamment Photon avec Oculus/Embeddium. Un pack qui transforme entiÃ¨rement la profondeur du monde demande sa propre vÃ©rification.

## Sons dâ€™ambiance

Jusquâ€™Ã  32 sources persistantes possÃ¨dent chacune un identifiant, un Ã©vÃ©nement `sounds.json`, une position, un volume, une hauteur, une variation de hauteur, une portÃ©e, une distance Ã  plein volume, une attÃ©nuation derriÃ¨re les murs et des fondus.

- **Boucle** : un son continu par joueur, positionnel ou non positionnel.
- **AlÃ©atoire** : intervalles minimum/maximum en ticks ; 20 ticks valent une seconde Ã  vitesse normale.
- **Rayon** : la source devient silencieuse Ã  sa limite. Le volume varie progressivement selon la distance Ã  la camÃ©ra, sans dÃ©pendre de la portÃ©e habituelle de 16 blocs des sons vanilla.
- **Occlusion** : multiplicateur de volume en prÃ©sence dâ€™un obstacle, de 0 pour le silence Ã  1 pour aucune attÃ©nuation. La ligne de vue est rÃ©Ã©valuÃ©e pendant lâ€™Ã©coute.
- **Ã‰clair** : intensitÃ© de flash associÃ©e au dÃ©clenchement.
- **Vitesse du son** : 0 pour jouer immÃ©diatement, ou par exemple 343 blocs/s pour entendre le tonnerre aprÃ¨s le flash selon la distance.
- Le volume global sâ€™ajoute au volume de source et au rÃ©glage Minecraft **Ambiance / environnement**.

Les intervalles sont gÃ©rÃ©s par le serveur. Les dÃ©clenchements portent une graine commune pour choisir la mÃªme variante sonore et la mÃªme variation de hauteur. Le retard et le volume restent propres Ã  chaque auditeur. Les boucles sâ€™arrÃªtent avec un fondu quand on les retire ou les dÃ©sactive. Les horloges de lecture sont locales Ã  la session et reprennent aprÃ¨s rechargement Ã  partir des dÃ©finitions persistantes.

Ã‰vÃ©nements inclus : `zombierool:ambient_ash_wind`, `ambient_cloud_rumble`, `ambient_distant_thunder`, `ambient_distant_alarm`, `ambient_wind_gust`, tous dans le namespace `zombierool`. Les 12 clips proviennent de lâ€™installation locale indiquÃ©e par lâ€™utilisateur. Une nouvelle source accepte les Ã©vÃ©nements dâ€™autres namespaces.

Le bouton **Ã‰couter** dans le menu permet un essai manuel. Un dÃ©clenchement manuel est une lecture unique ; il nâ€™ajoute pas de boucle permanente Ã  celles dÃ©jÃ  configurÃ©es.

## Lua

La rÃ©fÃ©rence intÃ©grÃ©e Ã  lâ€™Ã©diteur Lua contient les nouvelles mÃ©thodes, leurs paramÃ¨tres et leurs exemples en franÃ§ais et en anglais. Voir aussi [ashfall_atmosphere.lua](../examples/ashfall_atmosphere.lua).

```lua
local zr = ZombieroolAPI
zr:setMapAtmospherePreset("ashfall")
zr:configureMapAtmosphere({skyYaw=30, saturation=0.7, vignette=0.25, dirt=0.1})
zr:setSkybox("example:textures/sky/evening.png", "panorama", 0, 0.8)
zr:setColorGrading(-0.2, 1.1, 0.6, 1.0)
zr:setScreenEffects(0.3, 0.1, 0.03)
zr:configureAmbientSource("alarm", {
  sound="zombierool:ambient_distant_alarm", enabled=true, loop=false,
  x=32, y=70, z=-24, volume=0.7, near=6, radius=140,
  intervalMin=900, intervalMax=2200, occlusion=0.3, fadeIn=20, fadeOut=30
})
zr:configureAmbientSource("thunder", {flash=0.55, soundSpeed=343})
zr:triggerAmbientSound("thunder")
zr:setAmbientVolume(0.6)
```

Consultation : `getMapAtmosphere()`, `getAmbientSources()`, `getAmbientSource(id)`. Commandes supplÃ©mentaires : `setMapAtmosphereEnabled(bool)`, `setAmbientSourceEnabled(id,bool)`, `removeAmbientSource(id)`. La modification des sources sâ€™effectue avec les mÃ©thodes dÃ©diÃ©es, indÃ©pendamment des rÃ©glages dâ€™image.

`configurePlayerAtmosphere(uuid, changes)` et `resetPlayerAtmosphere(uuid)` permettent une surcharge visuelle temporaire pour un joueur. Une synchronisation globale conserve dÃ©sormais les surcharges de voyage. Elles restent liÃ©es Ã  leur dimension et sont retirÃ©es Ã  la mort ou au reset de partie.

Ã‰vÃ©nements serveur : `OnMapAtmosphereChanged()` aprÃ¨s modification persistante ; `OnAmbientSound(id, sound)` aprÃ¨s un dÃ©clenchement automatique ou manuel. Ces callbacks sont protÃ©gÃ©s contre les appels rÃ©cursifs.

## Ressources et tests

Conversion reproductible : `tools/prepare-map-atmosphere.py`, aprÃ¨s extraction locale de `zm_nuked.ff` avec OpenAssetTools. Les six faces sont conservÃ©es sans synthÃ¨se dâ€™une image de remplacement. Manifeste : `assets-source/26.7/environment/ashfall-manifest.json` (alias, durÃ©es, chemins et SHA-256).

CrÃ©ation de la carte sÃ©parÃ©e : `tools/build-atmosphere-demo.py`, qui choisit un nouveau dossier si le nom existe dÃ©jÃ . Aucun monde existant nâ€™est Ã©crasÃ©. La carte contient une route, des maisons, des abris et une source de sirÃ¨ne repÃ©rÃ©e.

Tests serveur/Lua : `tools/tests/run-portable-equipment-tests.py --atmosphere`. Tests du framebuffer et de lâ€™Ã©diteur : `tools/tests/render-map-atmosphere.py`, Ã©ventuellement suivi du nom dâ€™un shader pack dÃ©jÃ  installÃ©. Les clients de vÃ©rification utilisent des copies de la carte et des fenÃªtres masquÃ©es.

## Ajouter un son sans saisir dâ€™identifiant

1. Ouvrir **Ambiances â†’ Ajouter un son**.
2. Choisir un son dans la liste dÃ©filante (molette et recherche disponibles). Chaque ligne propose **Ã‰couter** pour prÃ©visualiser sans sÃ©lectionner. Le champ **Son sÃ©lectionnÃ©** ouvre Ã©galement une liste des ambiances dÃ©jÃ  prÃ©sentes, plutÃ´t quâ€™un bouton cyclique. Les sons des quatre presets y figurent.
3. RÃ©gler **Volume** et **PortÃ©e**. Cliquer sur **Placer ici** pour un son localisÃ©.
4. **Audible partout** ignore la distance ; **Lecture en continu** crÃ©e une boucle. Sinon les deux durÃ©es sont le dÃ©lai minimal et maximal **en secondes** entre deux lectures.
5. **Ã‰couter** donne un aperÃ§u depuis le joueur ; **TerminÃ©** enregistre.

Pour votre propre audio : placer un `.ogg` mono dans **`<monde>/zombierool/audio/custom/`**, par exemple `sirens/alarm.ogg`. Ã€ la prochaine entrÃ©e (ou aprÃ¨s rechargement des ressources de carte), il apparaÃ®t dans la mÃªme liste ; identifiant Lua : `zombierool:custom/sirens/alarm`. Aucun `sounds.json` ni pack nâ€™est nÃ©cessaire. Les sources locales conservent lâ€™attÃ©nuation par distance, les murs et les fondus du systÃ¨me dâ€™ambiance. La limite existante du chargeur audio est 15 Mio par fichier.

BibliothÃ¨ques Lua : `getMapAtmospherePresets()` renvoie les identifiants et noms, `getAmbientSoundLibrary()` les sons fournis et ceux du monde, `getMapSkyboxes()` les images fournies et celles du monde. `setMapAtmospherePreset("moon")`, `"nacht_der_untoten"`, `"die_rise"`, `"nuketown_zombies"` ou `"none"` sÃ©lectionne un preset. Le paramÃ¨tre `presetId` dÃ©crit le dernier point de dÃ©part ; les modifications restent libres.

La carte de test nâ€™embarque plus de **resources.zip**. Les tests clients vÃ©rifient quâ€™entrer dans la carte ne provoque aucun rechargement de packs, que les PNG/OGG du dossier `zombierool/` arrivent, et que la silhouette de la main reste visible devant le ciel avec et sans Photon.

Le brouillard de carte a prioritÃ© : tant quâ€™il est activÃ©, la skybox personnalisÃ©e est masquÃ©e. Elle revient automatiquement quand le brouillard est dÃ©sactivÃ©. Les rÃ©glages dâ€™image et les sons restent actifs.

## Presets personnalisÃ©s et tÃ©lÃ©porteurs

Dans **Presets**, le seul bouton **Nouveau preset** dÃ©marre une ambiance vide nommÃ©e. Modifier un preset officiel crÃ©e une copie personnalisÃ©e. Modifier un preset personnalisÃ© met Ã  jour ce mÃªme preset, sans nouvelle copie ; les changements restent un brouillon jusquâ€™Ã  TerminÃ©. **TerminÃ©** enregistre les nouvelles dÃ©finitions et la sÃ©lection dans la carte. **Annuler** abandonne les nouvelles copies. Les presets officiels restent protÃ©gÃ©s. Annuler abandonne aussi les modifications dâ€™un preset personnalisÃ© existant. La bibliothÃ¨que personnalisÃ©e contient au maximum 32 presets par dimension. **Supprimer ce preset** retire un preset personnalisÃ© lors de **TerminÃ©** ; **Annuler** abandonne la suppression. Les presets fournis ne sont pas supprimables. Le rÃ©glage **Afficher le ciel** est dans **Image** et ne coupe ni les couleurs ni les sons. Pour dÃ©sactiver toute la prÃ©sentation, choisir **Aucun** dans les presets.

Le sÃ©lecteur prÃ©sente les presets fournis et personnalisÃ©s dans une liste avec recherche. Les contrÃ´les numÃ©riques dâ€™image et dâ€™audio utilisent des curseurs avec valeur affichÃ©e : inclinaison, rotations, gamma, exposition, teintes, pitch, variation, portÃ©e, fondus, flash et vitesse du son. Les fondus et intervalles sâ€™affichent en secondes ; les coordonnÃ©es restent des champs prÃ©cis.

Dans le tÃ©lÃ©porteur, **Voyage â†’ Preset Ã  lâ€™arrivÃ©e** ouvre la mÃªme bibliothÃ¨que. Le rÃ©glage du dÃ©part a prioritÃ© ; sinon le rÃ©cepteur peut dÃ©finir son ambiance. Les changements concernent uniquement les voyageurs : ciel, couleurs, boucles et sons alÃ©atoires. **Conserver lâ€™ambiance** laisse les rÃ©glages actuels ; **RÃ©tablir lâ€™ambiance de la carte** retire la surcharge. Un retour automatique restaure lâ€™ambiance prÃ©cÃ©dente si le point de retour nâ€™a aucun preset dâ€™arrivÃ©e. Les sources des presets fournis sont placÃ©es autour de lâ€™arrivÃ©e ; celles des presets personnalisÃ©s conservent leurs coordonnÃ©es de carte. Les rÃ©glages de brouillard existants gardent la prioritÃ© sur la skybox.

Lua : `saveMapAtmospherePreset(name)` crÃ©e une copie nommÃ©e de lâ€™ambiance de carte et renvoie son ID. `getMapAtmospherePresets()` inclut les copies enregistrÃ©es, `setMapAtmospherePreset(id)` les sÃ©lectionne. `setPlayerAtmospherePreset(uuid,id)` applique une ambiance Ã  un joueur ; `resetPlayerAtmosphere(uuid)` rÃ©tablit la carte. Ces surcharges sont conservÃ©es lors dâ€™une reconnexion dans la mÃªme dimension et supprimÃ©es aprÃ¨s la mort.

Les rÃ©glages de prÃ©sentation prÃ©sents au dÃ©but dâ€™une partie sont sauvegardÃ©s avec la session. La fin de partie et la rÃ©cupÃ©ration dâ€™une session interrompue restaurent cette base et retirent les surcharges des voyageurs. Les nouveaux presets de la bibliothÃ¨que restent enregistrÃ©s.


## Import depuis lâ€™Ã©diteur

**Image â†’ Importer une skybox** ouvre le sÃ©lecteur de fichier : PNG en panorama 2:1, atlas cubique 3:2 ou croix horizontale 4:3, jusquâ€™Ã  4096 pixels par dimension et 8 Mo. Une croix 4096Ã—3072 est convertie sans redimensionnement en atlas 3072Ã—2048 ; les six faces sont conservÃ©es pixel pour pixel et orientÃ©es en Y-up. Le format est dÃ©tectÃ© automatiquement ; lâ€™orientation reste rÃ©glable. **Ambiances â†’ Importer un son** accepte un fichier Ogg Vorbis `.ogg` jusquâ€™Ã  15 Mo et crÃ©e une source que lâ€™on peut positionner et rÃ©gler.

Les fichiers sont copiÃ©s immÃ©diatement dans `zombierool/assets/skyboxes/imports/` et `zombierool/audio/custom/imports/` de la carte, y compris sur serveur distant. Le chargement dynamique les rend disponibles aux joueurs connectÃ©s sans recharger les packs ni quitter la carte. **TerminÃ©** enregistre les rÃ©glages qui les utilisent ; **Annuler** abandonne ces rÃ©glages mais conserve le fichier importÃ© dans la bibliothÃ¨que de ressources.

Le tunnel des tÃ©lÃ©porteurs utilise un compteur de ticks client indÃ©pendant de lâ€™heure du monde. Une correction horaire aprÃ¨s un retard serveur ne peut plus ramener lâ€™animation Ã  une phase prÃ©cÃ©dente.

Le rendu de profondeur suit Ã©galement lâ€™activation du stencil par les armes Ã  lunette : les tampons de la skybox et de la main adoptent le mÃªme format, y compris si lâ€™arme le change pendant le rendu de la main. Les crÃ©ations de textures de craie, de camouflage et dâ€™import prÃ©servent les liaisons de textures du monde.


## Ciels supplÃ©mentaires

**Revelations**, **The Giant** et **Shangri-La** sont disponibles dans les presets, les ciels de lâ€™onglet Image, les tÃ©lÃ©porteurs et la bibliothÃ¨que Lua. Leurs panoramas 4096Ã—2048 proviennent des archives locales de ces cartes. La conversion BC6H conserve les valeurs HDR jusquâ€™au rÃ©glage dâ€™exposition et au passage en sRGB ; The Giant reÃ§oit une compensation de luminositÃ© pour ses nuages de nuit. Les fichiers sources du jeu ne sont pas modifiÃ©s. Le manifeste `assets-source/26.7/environment/bo3-presets-manifest.json` conserve les rÃ©fÃ©rences et empreintes dâ€™extraction.

Ces presets ajoutent leurs rÃ©glages dâ€™image et utilisent des ambiances de la bibliothÃ¨que existante. Lâ€™extracteur reproductible est `tools/prepare-bo3-skies.py` ; son lecteur XPAK suit le format documentÃ© par [Greyhound](https://github.com/Scobalula/Greyhound) et le dÃ©codage HDR utilise [DirectXTex](https://github.com/microsoft/DirectXTex).

RÃ©gression des anneaux clairs du sang : `tools/tests/render-map-atmosphere.py --blood-death` (et avec `photon_v1.3b.zip`). Le test tue de vraies entitÃ©s avec et sans skybox, relÃ¨ve les types de particules, compare les pixels du ciel avant/aprÃ¨s un groupe de gouttes et vÃ©rifie leur masquage derriÃ¨re un mur opaque. Validation sans shader et avec Photon : 2 727 pixels de sang visibles, zÃ©ro pixel de halo clair, zÃ©ro pixel de sang Ã  travers le mur.


## Ambiances originales de Nuketown Zombies et Die Rise

Les presets fournis incluent dÃ©sormais lâ€™ensemble des alias environnementaux rÃ©solus dans leurs banques locales : **22 sources / 61 variantes pour Nuketown Zombies**, **34 sources / 99 variantes pour Die Rise**. Les sons partagÃ©s sont dÃ©dupliquÃ©s : **140 fichiers OGG mono distincts**, environ 6,8 Mo au total. Les variantes dâ€™un mÃªme alias sont regroupÃ©es dans un Ã©vÃ©nement sonore alÃ©atoire. Les alias vides prÃ©sents dans les banques sont inventoriÃ©s sans gÃ©nÃ©rer de faux sons ; les voix de zombies, les dialogues et les effets dâ€™armes ne sont pas des ambiances et ne sont pas ajoutÃ©s au fond sonore.

La bibliothÃ¨que sonore du menu et `getAmbientSoundLibrary()` exposent toutes ces ambiances. Le plafond des sources par atmosphÃ¨re passe Ã  64 pour conserver toutes les sources de Die Rise et permettre lâ€™ajout de sources personnalisÃ©es. Les boucles restent continues ; les Ã©vÃ©nements ponctuels ont des dÃ©lais alÃ©atoires indÃ©pendants. Le tonnerre conserve son flash lumineux et son dÃ©lai de propagation. Les paramÃ¨tres sont modifiables dans Map Config, et les sources locales doivent Ãªtre positionnÃ©es selon les zones de votre carte. Les positions proposÃ©es sont un point de dÃ©part, pas une copie de la gÃ©omÃ©trie des cartes originales.

Pour une carte ayant dÃ©jÃ  sauvegardÃ© les anciens rÃ©glages, rÃ©appliquez le preset fourni puis enregistrez pour charger toutes les nouvelles sources. Les presets personnalisÃ©s et les rÃ©glages existants ne sont pas Ã©crasÃ©s automatiquement.

Inventaire complet et provenance : `assets-source/26.7/audio/original-map-ambience-manifest.json`. Extraction ciblÃ©e reproductible depuis les banques originales et leurs fichiers dâ€™alias : `tools/prepare-original-map-ambience.py`.

## PrioritÃ© du brouillard Hellhound

La manche spÃ©ciale utilise un brouillard blanc temporaire (proche 1 bloc, loin 12 blocs), mÃªme si le brouillard de carte est dÃ©sactivÃ© ou limitÃ© Ã  lâ€™extÃ©rieur. La skybox personnalisÃ©e est masquÃ©e pendant cet effet. Il ne remplace ni les valeurs du brouillard de carte, ni le preset dâ€™atmosphÃ¨re : leur Ã©tat normal revient dÃ¨s la fin de la manche, la mort, le passage en spectateur ou la fin de la partie. Les rÃ©glages de couleur, de distance et le mode extÃ©rieur de la carte restent conservÃ©s.


## Objets et effets 3D du ciel

Ouvrir **Map Config â†’ Atmosphere â†’ Image â†’ Objets et effets du ciel**. Ajouter un effet dans la liste ou importer un modÃ¨le JSON. SÃ©lectionner lâ€™objet, puis utiliser **Placer dans mon regard** pour choisir sa direction. Les sliders rÃ¨glent azimut, hauteur, taille, distance, orientation, rotation continue, trajet autour du ciel, flottement, vitesse, dÃ©calage et opacitÃ©. Les flÃ¨ches sous la liste sÃ©lectionnent aussi les objets hors de la zone visible. **TerminÃ©** revient Ã  lâ€™Ã©diteur dâ€™atmosphÃ¨re ; son bouton **TerminÃ©** enregistre le preset et la carte. Annuler le sous-menu rÃ©tablit son Ã©tat prÃ©cÃ©dent.

Les presets fournis chargent dÃ©sormais les ressources originales adaptÃ©es au rendu Minecraft : **Nuketown Zombies**, les 23 Ã©lÃ©ments du champignon avec leurs courbes et flipbooks de fumÃ©e ; **Moon**, la texture originale de la Terre projetÃ©e sur un cube ; **Origins**, le vÃ©ritable modÃ¨le de 20 avions avec son squelette, ses textures et ses 350 images dâ€™animation Ã  30 images/s ; **Shangri-La**, les couches de lâ€™effet dâ€™Ã©clipse original ; **Die Rise**, des mÃ©tÃ©ores utilisant les textures et les courbes originales de flammes, fumÃ©es et impacts. Les gros rochers du dÃ©cor restent ceux peints dans son cubemap original : les Ã®lots procÃ©duraux du prototype ont Ã©tÃ© retirÃ©s du preset.

Les anciens presets fournis sans scÃ¨ne reÃ§oivent automatiquement les objets correspondants. Les anciennes escadrilles et les Ã®lots du prototype fourni sont migrÃ©s vers cette configuration. Les presets personnalisÃ©s conservent leurs objets et leurs positions ; les types `nuclear_cloud`, `earth`, `dogfight`, `eclipse` et `meteor` utilisent dÃ©sormais ce rendu texturÃ©. Pour rÃ©cupÃ©rer la nouvelle disposition complÃ¨te dans une copie personnalisÃ©e, sÃ©lectionner le preset fourni comme nouveau point de dÃ©part puis enregistrer une nouvelle copie.

Les presets officiels ne proposent aucun bouton de suppression et le serveur refuse toute demande de suppression de leurs identifiants. Seuls les presets personnalisÃ©s de la carte sont supprimables.

Le champignon utilise une Ã©chelle de 42 Ã  une distance de 80, avec un volume de volutes montantes qui se chevauchent autour des couches verticales dâ€™origine. Le combat aÃ©rien reste dans un plan horizontal, centrÃ© au-dessus du joueur (hauteur angulaire de 85Â°). Die Rise utilise quatre Ã©metteurs de mÃ©tÃ©ores dÃ©calÃ©s dans le temps : chute de 12 secondes depuis environ 80Â° jusquâ€™Ã  -80Â°, puis extinction de la traÃ®nÃ©e. Le cap change Ã  chaque cycle et couvre 360Â° ; les machines et bÃ¢timents continuent de masquer les effets. Les curseurs restent disponibles pour les rÃ©glages propres Ã  une carte. Les anciennes scÃ¨nes officielles restÃ©es aux rÃ©glages fournis sont mises Ã  jour ; les scÃ¨nes personnalisÃ©es conservent leurs rÃ©glages.

Les particules suivent leurs paramÃ¨tres dâ€™Ã©mission, durÃ©es de vie, variations, couleurs, tailles et rotations dâ€™origine. Le champignon dispose de ses 35 secondes de prÃ©chauffage pour Ãªtre visible dÃ¨s lâ€™arrivÃ©e. Le lecteur Minecraft adapte les sprites orientÃ©s, les traÃ®nÃ©es et le mÃ©lange des matÃ©riaux Ã  sa propre scÃ¨ne du ciel ; il ne reproduit pas le pipeline de shaders natif du jeu source. Les avions utilisent les trajectoires du vÃ©ritable squelette dâ€™origine : les dÃ©placements relatifs de chaque racine dâ€™avion sont ajoutÃ©s Ã  sa position de rÃ©fÃ©rence distincte. Les positions locales des sockets et des hÃ©lices restent inchangÃ©es. Ce dÃ©codage empÃªche le rassemblement des 20 avions Ã  lâ€™origine lors de la boucle. Les sockets animÃ©s suivent les armes et les fumÃ©es. La colonne du champignon ajoute des volumes de fumÃ©e montants avec les textures originales pour restituer son Ã©paisseur ; cette Ã©mission est adaptÃ©e au rendu Minecraft.

Les sliders restent disponibles pour chaque effet. Les mÃ©tÃ©ores se succÃ¨dent pÃ©riodiquement ; `phase` permet de les dÃ©synchroniser. `floating_rocks` reste une option de gÃ©omÃ©trie personnalisÃ©e dans lâ€™Ã©diteur, distincte des effets fournis. Les modÃ¨les JSON des map makers utilisent le mÃªme rendu et restent intÃ©grÃ©s au preset.

PrÃ©paration reproductible : `tools/prepare-sky-runtime.py`, Ã  partir de `assets-source/26.7/sky-originals`. Les fichiers de lecture sont `sky/original_effects.json`, `sky/air_battle.zrs.gz` et `textures/environment/sky_scene_atlas.png`. Les textures et flipbooks sont regroupÃ©s dans un atlas statique ; aucune extraction, lecture du jeu source ni gÃ©nÃ©ration de pack nâ€™a lieu pendant une partie.

Ces dÃ©cors sont centrÃ©s sur la camÃ©ra et placÃ©s derriÃ¨re la carte : aucun bloc, aucune collision, aucun chunk supplÃ©mentaire. Lâ€™azimut est indÃ©pendant du yaw de la texture de skybox : 0 = sud, 90 = est, -90 = ouest. La taille apparente dÃ©pend de `size / distance`. Le brouillard de carte et le brouillard Hellhound masquent aussi cette couche. Les objets suivent le preset dâ€™arrivÃ©e des tÃ©lÃ©porteurs, les changements par joueur et la restauration dâ€™atmosphÃ¨re de fin de partie. Ils ne rendent jamais par-dessus les mains, les armes ou les bÃ¢timents. Le rendu normal se fait avant le monde transparent ; avec un shader pack, il utilise les masques de profondeur dÃ©jÃ  capturÃ©s par lâ€™atmosphÃ¨re. Les shaders qui modifient la profondeur peuvent demander une adaptation ; Photon fait partie des essais visuels.

### ModÃ¨les personnalisÃ©s

Lâ€™import utilise un JSON compact contenant `name` et `boxes`, avec 1 Ã  64 cubes/pavÃ©s colorÃ©s. Exemple prÃªt Ã  importer : `docs/examples/sky-beacon.json`. Chaque pavÃ© possÃ¨de `x,y,z` (centre), `sx,sy,sz` (dimensions complÃ¨tes), `r,g,b` (couleur de 0 Ã  1), et Ã©ventuellement `alpha` et `emissive` (0 Ã  1). Les coordonnÃ©es sont locales au modÃ¨le, centrÃ©es autour de 0 ; position limitÃ©e Ã  Â±4, dimension Ã  8. Il ne sâ€™agit pas dâ€™un importeur OBJ, glTF ou `.bbmodel` : les textures externes et les animations de squelette ne sont pas prises en charge. La gÃ©omÃ©trie est intÃ©grÃ©e au preset puis synchronisÃ©e aux autres joueurs, sans resource pack ni rechargement Minecraft. Limite de 12 objets par atmosphÃ¨re. Les importations ne conservent aucun lien vers le fichier source.

### Lua

```lua
local zr = ZombieroolAPI
zr:configureSkyObject("earth", {
    type="earth", yaw=-35, elevation=38, size=19,
    spin=0.65, tilt=18, enabled=true
})
zr:configureSkyObject("earth", {orbit=0.2, opacity=0.9})
local objects = zr:getSkyObjects()
zr:removeSkyObject("earth")
-- JSON gÃ©omÃ©trique directement depuis un addon :
zr:importSkyObject("beacon", [[{"name":"Beacon","boxes":[
    {"sx":0.35,"sy":1.8,"sz":0.35,"r":0.15,"g":0.65,"b":0.85}
]}]])
```

`configureMapAtmosphere` et `configurePlayerAtmosphere` acceptent Ã©galement la liste complÃ¨te `skyObjects`. `speed` contrÃ´le lâ€™animation interne ; `spin` et `orbit` sont des vitesses indÃ©pendantes en degrÃ©s par seconde. `phase` dÃ©synchronise les animations internes. Une vitesse interne de zÃ©ro fige la fumÃ©e/les avions, sans annuler les rotations explicitement configurÃ©es.

Les objets appartiennent au preset complet. Dans la configuration du tÃ©lÃ©porteur, **Arrival Atmosphere preset** remplace aussi sa scÃ¨ne 3D pour le joueur : **Moon** retire le champignon et affiche la Terre ; **None** retire le ciel personnalisÃ© et ses objets ; un preset personnalisÃ© peut conserver son ciel avec une liste dâ€™objets vide. Un champ vide conserve lâ€™atmosphÃ¨re actuelle. Ce changement est individuel ; les fonctions Lua ci-dessous modifient la configuration de la carte.

```lua
-- Retirer le champignon de la scÃ¨ne de la carte pendant la partie :
for _, object in ipairs(ZombieroolAPI:getSkyObjects()) do
    if object.type == "nuclear_cloud" then
        ZombieroolAPI:removeSkyObject(object.id)
        -- Ou configureSkyObject(object.id, {enabled=false}) pour le dÃ©sactiver.
    end
end
```

Le rendu rÃ©utilise ses tableaux de sommets et les taches de sang dÃ©jÃ  regroupÃ©es, sans rÃ©duire les textures, le nombre de faces ou la frÃ©quence dâ€™animation. Les collisions des morceaux immobiles sont recalculÃ©es lorsque leur support statique change ; les blocs Ã  forme dynamique et les blocs avec entitÃ© conservent leur vÃ©rification complÃ¨te. Les caches de rendu sont libÃ©rÃ©s au changement de scÃ¨ne ou Ã  la sortie de la carte. La gÃ©omÃ©trie des avions est chargÃ©e Ã  sa premiÃ¨re utilisation.

Le champignon utilise une montÃ©e de colonne sur environ 222 secondes, avec des volutes arrondies sombres. Le chapeau combine les couches extraites avec 48 grandes volutes suivant une circulation toroÃ¯dale lente (environ 140 secondes par tour) : montÃ©e intÃ©rieure, Ã©talement au sommet et descente sur le bord. Le raccord avec la colonne devient progressivement plus large. Les flammes restent partiellement masquÃ©es par cette fumÃ©e ; 18 foyers internes utilisent les images originales, interpolÃ©es entre deux cellules, et un halo local pour Ã©clairer le repli infÃ©rieur. Les positions et tailles personnalisÃ©es restent inchangÃ©es. Le pipeline FX natif nâ€™est pas reproduit entiÃ¨rement : cette version reste une adaptation et son aspect ne correspond pas encore exactement Ã  la rÃ©fÃ©rence.


## Sélection et visibilité des particules ambiantes

Le sélecteur propose deux catégories, Vanilla et Mods, avec recherche dans les particules enregistrées. Une recherche remet la liste en haut pour conserver les résultats visibles. Les volumes techniques invisibles (Path, Restrict, etc.) ne sont pas traités comme un toit pour les émissions ambiantes. Sans shader, les particules ambiantes conservent le rendu natif après le ciel. Avec un shader pack, elles utilisent un passage après le ciel personnalisé tout en restant masquées par les vrais murs. Les rendus spéciaux fournis par des mods conservent leur propre moteur de rendu.

Les huit sprites génériques de fumée vanilla ont été rétablis : les anciennes surcharges transparentes rendaient notamment Cloud et Smoke invisibles, indépendamment du ciel.

Les émissions ambiantes ont un budget global de 128 / 384 / 640 / 896 particules vivantes selon la densité. Les options vidéo Réduites et Minimales divisent ce budget par deux et quatre. Les particules très lointaines sont libérées ; les particules hors champ ne sont pas dessinées dans le passage shader. Le tri est réutilisé entre les ticks, les vérifications de ressources sont faites une fois par image et les hauteurs sous les markers techniques sont mises en cache pendant une seconde.

Régression de charge : `tools/tests/render-map-atmosphere.py --ambient-particles` vérifie 10 000 demandes d’émission, le budget adapté aux options vidéo, l’absence d’allocations dans 50 000 vérifications de report du rendu et le masquage derrière un mur. Il ne mesure pas les FPS de la carte du joueur.
