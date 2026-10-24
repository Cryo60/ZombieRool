local state = { running = false, paused = false, mode = 'SURVIVAL', down = false, active = true, hidden = true }
local timers, enemies, spawned, checks = {}, {}, {}, 0
local previous = { toggle = 0, death = 0, start = 0 }
local player = 'test-player'
local timer = 'stratholme_risen_' .. player
local random = 0
local failSpawn = false
local function check(ok, message)
    assert(ok, message)
    checks = checks + 1
    print('RISEN LUA CHECK: ' .. message)
end
ZombieroolAPI = {
    isGameRunning = function() return state.running end,
    getPlayerHealth = function() return 100 end,
    getActivePlayers = function() if state.mode == 'SURVIVAL' or state.mode == 'ADVENTURE' then return {player} end; return {} end,
    isPlayerDown = function() return state.down end,
    isMusicVolumeActive = function() return state.active end,
    getMusicVolume = function(_, id) return id == 'music_-100_96_118' and {preset = 'damned'} or {} end,
    registerTimer = function(_, id, ticks, callback) timers[id] = {ticks = ticks, callback = callback} end,
    cancelTimer = function(_, id) timers[id] = nil end,
    getRandomInt = function(_, minimum, maximum) random = random + 1; return random % 2 == 0 and maximum or minimum end,
    getMapActorProfiles = function() return {'scarlet_zombie', 'scarlet_zombie_swordsman', 'scarlet_zombie_axeman', 'scarlet_zombie_pikeman', 'scarlet_zombie_female', 'scarlet_zombie_swordsman_female', 'balnazzar'} end,
    getMapActor = function(_, uuid) return enemies[uuid] or {} end,
    spawnMapActorNearPlayer = function(_, profile, uuid, minimum, maximum)
        if failSpawn then error('Simulated transient path search failure') end
        assert(profile:match('^scarlet_zombie') and uuid == player and minimum == 6 and maximum == 18)
        if not state.hidden then return '' end
        local id = 'enemy-' .. (#spawned + 1)
        enemies[id] = {health = 100, profile = profile}
        spawned[#spawned + 1] = id
        return id
    end,
    setMapActorTarget = function(_, id, uuid) enemies[id].target = uuid end
}
OnMusicVolumeToggle = function() previous.toggle = previous.toggle + 1 end
OnPlayerDeath = function() previous.death = previous.death + 1 end
OnGameStart = function() previous.start = previous.start + 1 end
dofile(SCRIPT_PATH)
local function toggle(active, id) state.active = active; OnMusicVolumeToggle(player, id or 'music_74_82_-46', active, 'damned') end
local function pulse() local task = assert(timers[timer], 'No pending timer');timers[timer] = nil;task.callback() end
toggle(true)
check(not timers[timer], 'No encounter outside an active match')
state.running = true;state.mode = 'SURVIVAL';toggle(true, 'another_volume')
check(not timers[timer], 'Other music volumes do not start this encounter')
toggle(true)
check(timers[timer] and timers[timer].ticks >= 60 and timers[timer].ticks <= 140, 'First appearance waits a random 3 to 7 seconds')
toggle(false)
toggle(true, 'music_-100_96_118')
check(timers[timer] ~= nil, 'The actual relocated Stratholme music zone starts Risen')
state.mode = 'ADVENTURE';pulse();check(#spawned >= 1 and #spawned <= 2, 'Adventure gameplay spawns one or two random Risen')
check(timers[timer].ticks >= 300 and timers[timer].ticks <= 600, 'Success schedules another appearance after 15 to 30 seconds')
local beforeExit = #spawned
state.active = false
if type(OnMusicVolumeExit) == 'function' then OnMusicVolumeExit(player, 'music_74_82_-46') end
pulse()
check(#spawned > beforeExit and timers[timer] ~= nil, 'Leaving the music box does not stop the encounter or require presence')
for _, id in ipairs(spawned) do assert(enemies[id].target == player) end
check(true, 'The ambush enemies pursue the triggering player')
for _, id in ipairs(spawned) do assert(not enemies[id].profile:match('_female$')) end
check(true, 'Ambush requests unified Risen roles; appearance is selected by the actor engine')
for _ = 1, 12 do pulse() end
check(#spawned == 4, 'Four living encounter enemies cap repeated appearances')
enemies[spawned[1]].health = 0;pulse();check(#spawned == 5, 'A killed encounter enemy frees one place')
enemies[spawned[2]].health = 0;state.hidden = false;local before = #spawned;pulse()
check(#spawned == before and timers[timer].ticks >= 100 and timers[timer].ticks <= 180, 'No hidden space skips the spawn and retries in 5 to 9 seconds')
local oldTask = timers[timer];toggle(false);check(not timers[timer], 'Restoring the old music cancels further appearances')
toggle(true);oldTask.callback();check(#spawned == before, 'A callback from an earlier activation cannot spawn enemies')
state.hidden = true;pulse();local after = #spawned;toggle(false);toggle(true);pulse()
check(#spawned == after, 'Reactivation retains the living-enemy cap from the previous activation')
OnPlayerDeath(player);check(not timers[timer] and previous.death == 1, 'Death cancels the encounter and preserves the previous event handler')
toggle(true);OnGameStart();check(not timers[timer] and previous.start == 1, 'A new match clears timers and preserves the previous start handler')
toggle(true);state.running = false;before = #spawned;pulse()
check(#spawned == before and not timers[timer], 'A delayed callback after match end cannot spawn anything')
check(previous.toggle > 0, 'Existing music toggle handlers remain connected')
state.running = true; OnGameStart(); toggle(true); failSpawn = true
check(not pcall(pulse) and timers[timer] ~= nil, 'A failed native path search preserves the Risen retry')
failSpawn = false; before = #spawned; pulse()
check(#spawned > before, 'Risen recover after a failed path search')
print('RISEN LUA PASS: ' .. checks .. ' encounter checks')
