-- Leave the authored streets and wall arsenal intact; reduce overlapping pressure.
local api = ZombieroolAPI
local previousStart = OnGameStart
function OnGameStart(...)
    if previousStart then previousStart(...) end
    api:configureMap({
        spawnIntensity = 'normal',
        zombieSprintWave = 10,
        zombieSprintChance = 0.15
    })
    api:configureSpecialEnemies({firstWave = 6, interval = 4})
end
