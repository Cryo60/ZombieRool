-- Map-owned combat. Detection, retaliation, factions and game-mode restrictions live in the engine.
local api = ZombieroolAPI

local function prepare(uuid, minimum, maximum)
    api:setMapActorData(uuid, { nextShot = api:getMapActorTime() + api:getRandomInt(minimum, maximum) })
end

local function ranged(uuid, style, damage, speed, windup, minimum, maximum)
    local actor, state = api:getMapActor(uuid), api:getMapActorData(uuid)
    local now = api:getMapActorTime()
    if not actor.health or actor.health <= 0 or actor.target == '' or not actor.canSeeTarget or actor.inMeleeRange or now < (state.nextShot or 0) then return end
    local target = actor.target
    api:setMapActorData(uuid, { nextShot = now + api:getRandomInt(minimum, maximum) })
    api:setMapActorCasting(uuid, windup)
    api:playMapActorAnimation(uuid, 'cast')
    if style == 'fire' then
        api:spawnParticleAdv('minecraft:flame', actor.x, actor.y + 1.3, actor.z, 8, .2, .15, .2, .01)
    end
    api:registerTimer('risen_shot_' .. uuid, windup, function()
        local current = api:getMapActor(uuid)
        if not current.health or current.health <= 0 or current.target ~= target or current.inMeleeRange then return end
        api:setMapActorCasting(uuid, 0)
        if api:shootMapActor(uuid, target, style, damage, speed) then
            api:playMapActorAnimation(uuid, 'attack')
            if style == 'arrow' then
                api:playSound(current.x, current.y + 1, current.z, 'zombierool:map_bow_release', .6, 1)
            end
        end
    end)
end

function risen_archer_spawn(uuid) prepare(uuid, 30, 70) end
function risen_archer_tick(uuid) ranged(uuid, 'arrow', 2, 2.2, 20, 90, 140) end
function risen_mage_spawn(uuid) prepare(uuid, 50, 100) end
function risen_mage_tick(uuid) ranged(uuid, 'fire', 3, .7, 25, 140, 200) end

local function stop(uuid) api:cancelTimer('risen_shot_' .. uuid) end
function risen_archer_death(uuid) stop(uuid) end
function risen_mage_death(uuid) stop(uuid) end
