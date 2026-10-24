-- Stratholme only: the music trigger starts/stops a personal Risen encounter.
local api = ZombieroolAPI
local VOLUME = 'music_74_82_-46'
local PROFILES = {
    'scarlet_zombie', 'scarlet_zombie_swordsman',
    'scarlet_zombie_axeman', 'scarlet_zombie_pikeman',
    'scarlet_zombie_archer', 'scarlet_zombie_mage'
}
local MAX_ALIVE = 4
local encounters = {}
local rosters = {}

local function timerId(player)
    return 'stratholme_risen_' .. player
end

local function stop(player)
    api:cancelTimer(timerId(player))
    if encounters[player] then rosters[player] = encounters[player].enemies end
    encounters[player] = nil
end

local function livingEnemies(state)
    local alive = {}
    for _, uuid in ipairs(state.enemies) do
        local enemy = api:getMapActor(uuid)
        if enemy.health and enemy.health > 0 then alive[#alive + 1] = uuid end
    end
    state.enemies = alive
    return #alive
end

local schedule
schedule = function(player, state, minimum, maximum)
    api:registerTimer(timerId(player), api:getRandomInt(minimum, maximum), function()
        -- Game mode, downed state and pause are enforced centrally by the engine.
        -- Only the toggle event starts/stops the encounter. Leaving the box does nothing.
        if encounters[player] ~= state then return end
        if not api:isGameRunning() then
            stop(player)
            return
        end
        -- Register the retry before native pathfinding, which can be slow on a cold map.
        schedule(player, state, 100, 180)
        local count = livingEnemies(state)
        if count >= MAX_ALIVE then
            schedule(player, state, 100, 180)
            return
        end
        -- Pick among the actual map-owned Risen profiles; never substitute a base mob.
        local available = {}
        for _, id in ipairs(api:getMapActorProfiles()) do
            for _, risen in ipairs(PROFILES) do
                if id == risen then available[#available + 1] = id end
            end
        end
        if #available == 0 then stop(player); return end
        local amount = api:getRandomInt(1, 4) == 1 and 2 or 1
        local spawned = 0
        for _ = 1, math.min(amount, MAX_ALIVE - count) do
            -- The actor engine chooses its male/female appearance at spawn.
            local profile = available[api:getRandomInt(1, #available)]
            local enemy = api:spawnMapActorNearPlayer(profile, player, 6, 18)
            if enemy ~= '' then
                state.enemies[#state.enemies + 1] = enemy
                api:setMapActorTarget(enemy, player)
                spawned = spawned + 1
            end
        end
        -- No safe hidden position: skip this appearance and retry without popping into view.
        schedule(player, state, spawned > 0 and 300 or 100, spawned > 0 and 600 or 180)
    end)
end

local previousToggle = OnMusicVolumeToggle
function OnMusicVolumeToggle(player, volumeId, active, preset)
    if type(previousToggle) == 'function' then previousToggle(player, volumeId, active, preset) end
    -- Follow the authored zone when the builder moves/replaces its block.
    local volume = api:getMusicVolume(volumeId)
    if volumeId ~= VOLUME and volumeId ~= 'stratholme_risen' and volume.preset ~= 'damned' then return end
    if not active then stop(player); return end
    if not api:isGameRunning() then return end
    local state = encounters[player]
    if state then return end
    state = { enemies = rosters[player] or {} }
    encounters[player] = state
    schedule(player, state, 60, 140)
end

local previousDeath = OnPlayerDeath
function OnPlayerDeath(player)
    stop(player)
    if type(previousDeath) == 'function' then previousDeath(player) end
end

local previousStart = OnGameStart
function OnGameStart()
    for player in pairs(encounters) do api:cancelTimer(timerId(player)) end
    encounters = {}
    rosters = {}
    if type(previousStart) == 'function' then previousStart() end
end
