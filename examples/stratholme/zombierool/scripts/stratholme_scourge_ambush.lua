-- Map-wide hidden Scourge encounters, suspended during each player's Risen music.
local api = ZombieroolAPI
local TIMER = 'stratholme_scourge_ambush'
local RISEN_VOLUME = 'music_74_82_-46'
local STEP = 40
local MAX_ALIVE = 4
local MAX_TOTAL = 16
local PROFILES = {
    'scourge_ambush_zombie', 'scourge_ambush_zombie',
    'scourge_ambush_ghoul', 'scourge_ambush_ghoul',
    'scourge_ambush_skeleton', 'scourge_ambush_geist'
}
local rosters = {}
local generation = 0

local function risenActive(player)
    for id, volume in pairs(api:getMusicVolumes()) do
        if (id == RISEN_VOLUME or id == 'stratholme_risen' or volume.preset == 'damned')
            and api:isMusicVolumeActive(player, id) then return true end
    end
    return false
end

local function prune(state)
    local living = {}
    for _, uuid in ipairs(state.enemies) do
        local actor = api:getMapActor(uuid)
        if actor.health and actor.health > 0 then living[#living + 1] = uuid end
    end
    state.enemies = living
    return #living
end

local schedule
schedule = function(version)
    api:registerTimer(TIMER, STEP, function()
        if generation ~= version or not api:isGameRunning() then return end
        -- Keep retrying even if an expensive first path search hits the Lua time budget.
        schedule(version)
        local total = 0
        for _, state in pairs(rosters) do total = total + prune(state) end
        local available, known = {}, {}
        for _, id in ipairs(api:getMapActorProfiles()) do known[id] = true end
        for _, id in ipairs(PROFILES) do
            if known[id] then available[#available + 1] = id end
        end
        for _, player in ipairs(api:getActivePlayers()) do
            local state = rosters[player]
            if not state then
                state = { enemies = {}, remaining = api:getRandomInt(600, 900) }
                rosters[player] = state
            end
            if api:getPlayerHealth(player) > 0 and not api:isPlayerDown(player) then
                if risenActive(player) then
                    -- Do not accumulate a burst while the Risen encounter is active.
                    state.remaining = api:getRandomInt(500, 800)
                else
                    state.remaining = state.remaining - STEP
                    if state.remaining <= 0 then
                        local spawned = 0
                        local amount = math.min(api:getRandomInt(1, 2), MAX_ALIVE - #state.enemies, MAX_TOTAL - total)
                        if #available > 0 then
                            for _ = 1, amount do
                                local profile = available[api:getRandomInt(1, #available)]
                                local uuid = api:spawnMapActorNearPlayer(profile, player, 6, 24)
                                if uuid ~= '' then
                                    state.enemies[#state.enemies + 1] = uuid
                                    api:setMapActorTarget(uuid, player)
                                    total = total + 1
                                    spawned = spawned + 1
                                end
                            end
                        end
                        -- The engine only places enemies on safe, hidden path blocks.
                        state.remaining = spawned > 0 and api:getRandomInt(500, 800) or api:getRandomInt(200, 300)
                    end
                end
            end
        end
    end)
end

local previousStart = OnGameStart
function OnGameStart()
    generation = generation + 1
    api:cancelTimer(TIMER)
    rosters = {}
    if type(previousStart) == 'function' then previousStart() end
    schedule(generation)
end
