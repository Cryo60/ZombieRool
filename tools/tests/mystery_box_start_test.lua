local timers, calls, running, starts = {}, {}, true, 0
local key = 'stratholme_initial_mystery_box'
ZombieroolAPI = {
    cancelTimer = function(_, id) timers[id] = nil end,
    registerTimer = function(_, id, ticks, callback) timers[id] = {ticks=ticks, callback=callback} end,
    isGameRunning = function() return running end,
    forceMysteryBox = function(_, x, y, z, locked)
        assert(x == -119 and y == 92 and z == 100 and locked == false)
        calls[#calls + 1] = true
    end
}
OnGameStart = function() starts = starts + 1 end
dofile(SCRIPT_PATH)
OnGameStart()
assert(starts == 1 and #calls == 0 and timers[key].ticks == 1)
local stale = timers[key].callback
OnGameStart()
stale()
assert(starts == 2 and #calls == 0)
local task = timers[key]; timers[key] = nil; task.callback()
assert(#calls == 1 and timers[key] == nil)
OnGameStart(); running = false; timers[key].callback()
assert(#calls == 1)
print('BOX LUA PASS: chained start, next tick, coordinates, unlocked movement, stale and stopped game guards')
