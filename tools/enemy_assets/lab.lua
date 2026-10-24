-- Enemy Encounter Lab. Ordinary map Lua, editable in Map Config.
local API = ZombieroolAPI
local busy = false
local function reset()
    for _, enemy in ipairs(API:listSpecialEnemies()) do API:despawnEncounter(enemy.uuid) end
    API:killAllZombies()
end
function OnGameStart()
    busy = false
    API:finishIntroPhase()
    API:broadcastMessage("Encounter Lab : boutons au sud, armes murales gratuites. Testez un ennemi a la fois.")
    for _, uuid in ipairs(API:getNearbyPlayers(0, 65, 0, 150)) do API:addPoints(uuid, 25000) end
end
function OnBlockInteract(playerUUID, x, y, z)
    if y ~= 65 or busy then return end
    local index = z == 23 and (x + 28) / 8 or -1
    if not (index >= 0 and index <= 7 and index == math.floor(index)) and not (x == 0 and z == 29) then return end
    busy = true
    API:registerTimer("lab_button_debounce", 12, function() busy = false end)
    if x == 0 and z == 29 then reset(); return end
    if index == 0 then
        reset()
        API:configureSpecialEnemies({specialWaveEnemy="pentagon_thief"})
        API:setWave(5)
        API:broadcastMessage("Voleur : gardez une arme en main. Tuez-le apres le vol pour la recuperer.")
    elseif index <= 4 then
        local kinds = {"george_romero", "astronaut", "abomination", "geist"}
        local kind = kinds[index]
        if kind == "geist" then API:configureSpecialEnemies({crawlerBehavior="leaper"}) end
        if API:spawnEncounter(kind, 0.5, 65, 4.5) == "" then
            API:broadcastMessage("Demarrez la partie avant d'utiliser les boutons.")
        else API:broadcastMessage("Test : " .. kind) end
    else
        local preset = "scourge"
        API:setEnemySkinPreset("zombie", preset, true)
        API:broadcastMessage("Apparence de la horde : " .. preset)
    end
end
function OnThiefWeaponStolen(enemyUUID, playerUUID)
    API:broadcastMessage("Arme volee : eliminez le Voleur avant sa fuite !")
end
function OnThiefWeaponReturned(enemyUUID, playerUUID)
    API:broadcastMessage("Arme restituee avec ses ameliorations.")
end
function OnRomeroRageChanged(enemyUUID, enraged)
    API:broadcastMessage(enraged and "Romero est furieux : attirez-le dans le bassin." or "Romero est calme.")
end
