-- Choose the first box after the engine has finished its initial random selection.
local api = ZombieroolAPI
local TIMER = 'stratholme_initial_mystery_box'
local generation = 0
local previousStart = OnGameStart

function OnGameStart(...)
    generation = generation + 1
    local version = generation
    api:cancelTimer(TIMER)
    if previousStart then previousStart(...) end
    api:registerTimer(TIMER, 1, function()
        if version ~= generation or not api:isGameRunning() then return end
        api:forceMysteryBox(-119, 92, 100, false)
    end)
end
