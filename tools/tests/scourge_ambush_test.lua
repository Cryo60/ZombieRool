local timers, actors, players = {}, {}, {'p1'}
local running, hidden, down, music, health = true, true, {}, {}, {}
local failSpawn = false
local spawned, checks = 0, 0
local timer = 'stratholme_scourge_ambush'
local function check(ok, message)
    assert(ok, message)
    checks = checks + 1
    print('SCOURGE LUA CHECK: ' .. message)
end
ZombieroolAPI = {
    isGameRunning = function() return running end,
    getActivePlayers = function() return players end,
    getPlayerHealth = function(_, p) return health[p] or 100 end,
    isPlayerDown = function(_, p) return down[p] or false end,
    getMusicVolumes = function() return {['music_-100_96_118'] = {preset = 'damned'}} end,
    isMusicVolumeActive = function(_, p, id) assert(id == 'music_-100_96_118'); return music[p] or false end,
    registerTimer = function(_, id, ticks, callback) timers[id] = {ticks = ticks, callback = callback} end,
    cancelTimer = function(_, id) timers[id] = nil end,
    getRandomInt = function(_, low, high) return high end,
    getMapActorProfiles = function() return {'scourge_ambush_zombie', 'scourge_ambush_ghoul', 'scourge_ambush_skeleton', 'scourge_ambush_geist', 'crypt_fiend'} end,
    getMapActor = function(_, id) return actors[id] or {} end,
    spawnMapActorNearPlayer = function(_, profile, p, low, high)
        if failSpawn then error('Simulated transient path search failure') end
        assert(profile == 'scourge_ambush_geist' and low == 6 and high == 24)
        if not hidden then return '' end
        spawned = spawned + 1
        local id = 'actor-' .. spawned
        actors[id] = {health = 40, owner = p}
        return id
    end,
    setMapActorTarget = function(_, id, p) actors[id].target = p end
}
local starts = 0
OnGameStart = function() starts = starts + 1 end
dofile(SCRIPT_PATH)
local function pulse(n)
    for _ = 1, n or 1 do
        local task = assert(timers[timer], 'Missing ambient timer')
        assert(task.ticks == 40)
        timers[timer] = nil
        task.callback()
    end
end
OnGameStart()
check(starts == 1, 'Existing game-start handler is preserved')
pulse(22); check(spawned == 0, 'Exploration gets a 30 to 45 second opening respite')
pulse(); check(spawned == 2, 'An encounter creates at most two enemies')
for _, actor in pairs(actors) do assert(actor.owner == actor.target) end
music.p1 = true
pulse(50); check(spawned == 2, 'Risen music suspends Scourge appearances')
music.p1 = false
pulse(19); check(spawned == 2, 'Ending Risen music preserves a full cooldown')
pulse(); check(spawned == 4, 'Ambient encounters resume as a small group')
pulse(100); check(spawned == 4, 'Four living enemies cap one player')
actors['actor-1'].health = 0
pulse(20); check(spawned == 5, 'One death frees only one place')
actors['actor-2'] = nil; hidden = false
pulse(20); check(spawned == 5, 'No hidden placement skips the appearance')
hidden = true
pulse(8); check(spawned == 6, 'Hidden placement failure retries safely')
OnGameStart(); actors = {}; spawned = 0
 down.p1 = true
pulse(60); check(spawned == 0, 'Downed players receive no ambient enemies')
down.p1 = false; health.p1 = 0
pulse(60); check(spawned == 0, 'Dead players receive no ambient enemies')
health.p1 = 100; players = {}
pulse(60); check(spawned == 0, 'No active player means no appearances')
players = {'p1', 'p2'}; music.p1 = true
pulse(100); check(spawned == 4, 'Another player can receive Scourge during Risen')
for _, actor in pairs(actors) do assert(actor.owner == 'p2') end
music.p1 = false; players = {'p1', 'p2', 'p3', 'p4', 'p5'}
pulse(500); check(spawned == 16, 'A global cap of sixteen bounds multiplayer pressure')
local old = timers[timer]
OnGameStart(); old.callback()
check(starts == 3 and timers[timer] ~= nil, 'Restart invalidates stale callbacks')
running = false; local before = spawned
pulse(); check(spawned == before and not timers[timer], 'Match end stops the scheduler')
running = true; players = {'p1'}; actors = {}; OnGameStart(); failSpawn = true
pulse(22)
check(not pcall(function() pulse() end) and timers[timer] ~= nil, 'A failed native path search preserves the next retry')
failSpawn = false; before = spawned; pulse()
check(spawned > before, 'The scheduler recovers after a failed path search')
print('SCOURGE LUA PASS: ' .. checks .. ' exploration balance checks')
