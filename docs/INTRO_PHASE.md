# Outnumbered et départ sans manches

Dans **Map Config > Rounds > Outnumbered**, activer **Start match in Outnumbered**, puis Apply. Désactivé par défaut.

**Outnumbered spawner channels** choisit les canaux des spawners participants, sous forme `0` ou `0,3,8`. La valeur par défaut est `0`. Le spawner doit aussi être actif (canaux de démarrage/arrêt, zone, alimentation). Les spawners des autres canaux n'alimentent pas Outnumbered.

Les zombies arrivent continuellement ; leur fréquence et leur plafond augmentent. Avant le sprint, ils conservent la vie de base de la manche 1 et restent tuables au couteau normal avec les réglages par défaut.

Dès le déclenchement du sprint, la vie suit des **manches virtuelles** : départ à la manche 2, ou à la manche réelle courante si elle est supérieure, puis +1 toutes les **Seconds per additional health round** (10 secondes par défaut). Cette progression continue après l’intensité maximale, avec les mêmes formules et plafonds de vie que les manches normales. Elle s’applique aux nouveaux ennemis et aux survivants déjà présents ; les blessés conservent leur proportion de vie. Ces manches virtuelles ne font pas avancer le compteur de manches de la partie.

La vitesse reste celle de base du zombie, puis passe d'un coup à la **Zombie sprint speed** déjà réglée dans Zombies au **Instant sprint threshold** (0,5 par défaut). Il n'y a plus d'accélération continue. **Seconds to reach full intensity** vaut 90 par défaut. Au retour, ce délai est divisé par `max(1, manche / 5)` : manche 20 = 22,5 secondes avec le réglage 90. Les autres seuils temporels de montée suivent cette intensité ; le délai d'apparition des chiens reste celui réglé en secondes.

Les chiens nécessitent un **spawner de hellhounds** placé, actif et dans les canaux Outnumbered. Aucun remplacement par un spawner de zombies. Leur probabilité et leur délai restent réglables.

## Téléporteurs

Terre vers Lune : activer **Finish Outnumbered** dans Advanced. Après l'arrivée, l'effet complet du téléporteur et une seconde, les ennemis Outnumbered sont retirés et la manche 1 commence.

Lune vers Terre : activer **Enter Outnumbered on arrival** dans Advanced. La manche courante est suspendue, avec son budget et les ennemis encore vivants. Le trajet Terre vers Lune reprend ensuite cette manche, sans la recommencer. La transition concerne la partie entière ; prévoir les voyages avec tous les joueurs pour une map multijoueur.

Les actions **Enable channels / Disable channels** s'appliquent comme d'habitude : par exemple départ Terre sur canal 0, Lune sur canal 1 ; Terre vers Lune désactive 0 et active 1, retour inverse. Les actions se configurent sur le téléporteur de départ. Si **Finish Outnumbered** et **Enter Outnumbered on arrival** sont tous les deux activés, le téléporteur bascule selon le mode courant : sortir d’Outnumbered s’il est actif, y entrer sinon. Une seule option conserve son action explicite.

**Unlock after match start (seconds)**, dans Timing, règle un déblocage natif après le début de partie : `60` pour une minute, `0` pour immédiatement. Il reste acquis pendant les allers-retours. **Required condition key** permet d'exiger en plus une condition du réseau, pilotée par un switch ou Lua. Délai et condition doivent tous les deux être satisfaits.

## Lua

```lua
function OnGameStart()
    ZombieroolAPI:configureIntroPhase(true, 90, 45)
    ZombieroolAPI:configureTeleporter("earth_to_moon", {
        unlockAfterSeconds = 60,
        startRoundsOnArrival = true,
        enableChannels = "1",
        disableChannels = "0"
    })
    ZombieroolAPI:configureTeleporter("moon_to_earth", {
        enterOutnumberedOnArrival = true,
        enableChannels = "0",
        disableChannels = "1"
    })
end

function OnIntroStart()
end

function OnIntroEnd(elapsedSeconds)
end
```

`startIntroPhase()` entre en Outnumbered pendant une partie et suspend une manche déjà commencée. Renvoie false hors partie ou si Outnumbered est déjà actif. `finishIntroPhase()` nettoie Outnumbered et démarre la manche 1 ou reprend la manche suspendue ; appel Lua immédiat. L'option du téléporteur ajoute automatiquement son délai d'arrivée.

`isIntroPhase()`, `getIntroElapsedSeconds()` et `getIntroPhaseState()` exposent l'état courant. Les réglages avancés peuvent être modifiés via `configureIntroPhaseSettings({spawnerChannels="0,3", sprintThreshold=0.5, healthRoundSeconds=10})`.


En créatif, **F** suit immédiatement le **Destination ID**, même si le téléporteur est désactivé, payant, sans courant ou encore en attente. Le raccourci ne dépense aucun point et ne déclenche ni changement de canaux ni changement de phase de partie. Le clic droit conserve l’éditeur. Une destination absente, ambiguë ou physiquement obstruée reste refusée.

L’indication de téléportation omet le coût lorsqu’il est nul et affiche le nombre de secondes restant pendant le déblocage initial ou la recharge.
