# Mr.Chief — notes locales

Rencontre pacifique, sans œuf ni commande de chat ordinaire. Console secrète : `mrchief` (alias `cat`, permission opérateur ; programme une rencontre garantie à la prochaine manche et marque la partie comme utilisant des cheats).

Par défaut : une tentative par manche, chance 1/100000, sur un spawner actif PLAYER/HELLHOUND/CRAWLER. Au maximum une rencontre naturelle par partie. Un seul chat par dimension, fête comprise. Autorisé par défaut dans allowedMobs et caché dans le menu. Seuls les spawns autorisés par le système sont exemptés du nettoyage. Santé 12 ; aucune attaque, aucun apprivoisement, aucun miaulement ni recul de tir. Il fuit à moins de 8 blocs, puis disparaît après 20 secondes. Les spectateurs ne déclenchent pas la fuite. Ses déplacements respectent les mêmes path blocks et limit blocks que les zombies.

Le joueur qui le tue gagne 2500 points bonus. Le chat et toutes les entités de la dimension, dont les gardes Royal Beer, alternent poses face au joueur, tours lents et tours rapides pendant 60 secondes, avec confettis, notes et un extrait du MP3 fourni par le créateur. La musique est diffusée localement (32 blocs), en streaming. Aucun dégât de fiesta. La danse est intouchable et ne compte pas pour terminer la manche. Fin/restart de partie nettoie le chat et arrête sa musique.

## Lua

```lua
ZombieroolAPI:configureMrChief({
    enabled = true,
    chance = 100000,          -- une chance sur N, par manche (100000..1000000)
    flee_radius = 8,       -- blocs (1..32)
    flee_seconds = 20,     -- secondes après le début de fuite (1..300)
    reward = 2500,         -- points bonus (0..1000000)
    party_seconds = 60     -- durée fixe synchronisée avec le fichier audio
})
ZombieroolAPI:queueMrChief() -- programme la prochaine manche
local settings = ZombieroolAPI:getMrChiefConfig()
local uuid = ZombieroolAPI:spawnMrChief(10, 64, 10)
-- uuid vide si le chunk est absent, l'emplacement bloqué ou un chat déjà présent.
```

Le chat ne produit aucun gore, sang, cadavre ou XP. Même une mort environnementale lance sa danse. Une apparition est signalée par un message discret : « Un ronronnement inquiétant traverse les rangs des morts… ».

Les réglages sont persistés dans les données du monde et copiés dans chaque nouvelle rencontre.

Événements :

- `OnMrChiefSpawned(catUUID, x, y, z, forced)`
- `OnMrChiefFled(catUUID, fleeSeconds)`
- `OnMrChiefEscaped(catUUID)`
- `OnMrChiefKilled(catUUID, playerUUID, reward, x, y, z)`

Ressources : texture 64×32 au format UV du chat vanilla, deux yeux rouges alignés sur la tête animée ; son `zombierool:mr_chief_party` (60 secondes), extrait du fichier local W_W - OIIA OIIA (Spinning Cat).mp3 fourni par l'utilisateur.

Le protocole réseau passe à 5 : tous les clients et le serveur doivent utiliser le nouveau JAR.
