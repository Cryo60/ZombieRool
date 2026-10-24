-- Map sous-marine : l eau et les poches d air physiques sont detectees automatiquement.
-- Aucun script n est requis ; ces appels sont une alternative a Map Config.
function OnGameStart()
    ZombieroolAPI:configureLunarEnvironment(1.0, false, 5)
    ZombieroolAPI:setZombieSwimming(true) -- false : les zombies coulent comme sur les maps classiques
    -- Si la carte combine aussi des volumes sans air, un sas sec peut les remplacer :
    -- ZombieroolAPI:setAtmosphereZone("sas", 174,65,-3,180,72,4,1.0,false)
end
-- ZombieroolAPI:getLunarHelmetState(uuid) expose air/capacity (ticks),
-- underwater, vacuum, needsOxygen, owned, equipped et changing.
