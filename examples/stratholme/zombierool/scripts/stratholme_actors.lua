-- These callbacks are local to Stratholme. The engine supplies navigation and melee.
local api = ZombieroolAPI

local function alive(uuid)
    local actor = api:getMapActor(uuid)
    return actor.health and actor.health > 0, actor
end

local function warn(actor, particle, sound)
    api:spawnParticleAdv(particle, actor.x, actor.y + 1, actor.z, 24, 1.2, 0.4, 1.2, 0.02)
    api:playSound(actor.x, actor.y, actor.z, sound, 0.8, 1)
end

function crypt_fiend_tick(uuid)
    local actor = api:getMapActor(uuid)
    local data = api:getMapActorData(uuid)
    local now = api:getMapActorTime()
    if actor.target ~= '' and not actor.inMeleeRange and now >= (data.nextWeb or 0) then
        api:setMapActorData(uuid, { nextWeb = now + 140 })
        api:setMapActorCasting(uuid, 20)
        api:spawnParticleAdv('minecraft:cloud', actor.x, actor.y + 1, actor.z, 16, .5, .2, .5, .01)
        local target = actor.target
        api:registerTimer('web_' .. uuid, 20, function()
            if alive(uuid) then api:shootMapActor(uuid, target, 'web', 0, 0.7) end
        end)
    end
end

function baron_rivendare_spawn(uuid)
    api:setMapActorData(uuid, { nextAura = 0, nextSpell = 0, cast = 0, minions = {} })
end

function baron_rivendare_tick(uuid)
    local ok, actor = alive(uuid)
    if not ok or actor.target == '' then return end
    local now, data = api:getMapActorTime(), api:getMapActorData(uuid)
    if now >= (data.nextAura or 0) then
        api:mapActorAttack(uuid, 4, 1, '', 0)
        api:spawnParticleAdv('minecraft:soul', actor.x, actor.y + 0.2, actor.z, 20, 2, 0.1, 2, 0.01)
        data.nextAura = now + 40
    end
    if not actor.inMeleeRange and now >= (data.nextSpell or 0) then
        data.nextSpell, data.cast = now + 120, (data.cast or 0) + 1
        local target, summon = actor.target, data.cast % 3 == 0
        api:setMapActorCasting(uuid, 30)
        warn(actor, 'minecraft:soul', 'minecraft:entity.wither.ambient')
        api:registerTimer('baron_cast_' .. uuid, 30, function()
            local present, current = alive(uuid)
            if not present then return end
            if summon then
                local state = api:getMapActorData(uuid)
                local living = {}
                for _, id in ipairs(state.minions or {}) do
                    if alive(id) then living[#living + 1] = id end
                end
                if #living < 4 then
                    for _, offset in ipairs({ -2, 2 }) do
                        if #living < 4 then
                            local id = api:spawnMapActor('raised_skeleton', current.x + offset, current.y, current.z + 2)
                            if id ~= '' then living[#living + 1] = id end
                        end
                    end
                end
                api:setMapActorData(uuid, { minions = living })
            else
                api:shootMapActor(uuid, target, 'shadow', 5, 0.65)
            end
        end)
    end
    api:setMapActorData(uuid, data)
end

function baron_rivendare_death(uuid)
    local data = api:getMapActorData(uuid)
    for _, id in ipairs(data.minions or {}) do api:despawnMapActor(id) end

end

function balnazzar_spawn(uuid)
    api:setMapActorData(uuid, { nextCast = 0, cast = 0 })
end

function balnazzar_tick(uuid)
    local ok, actor = alive(uuid)
    if not ok or actor.target == '' then return end
    local now, data = api:getMapActorTime(), api:getMapActorData(uuid)
    if actor.inMeleeRange or now < (data.nextCast or 0) then return end
    data.cast, data.nextCast = (data.cast or 0) + 1, now + 140
    api:setMapActorData(uuid, data)
    local spell, target = data.cast % 3, actor.target
    api:setMapActorCasting(uuid, 40)
    warn(actor, 'minecraft:witch', 'minecraft:entity.evoker.prepare_attack')
    api:registerTimer('balnazzar_cast_' .. uuid, 40, function()
        local present, current = alive(uuid)
        if not present then return end
        if spell == 0 then
            -- Sleep adaptation: a short, strong slow and obscured vision, not forced bed sleep.
            api:mapActorEffect(uuid, 7, 'minecraft:slowness', 50, 3)
            api:mapActorEffect(uuid, 7, 'minecraft:darkness', 60, 0)
        elseif spell == 1 then
            api:shootMapActor(uuid, target, 'shadow', 6, 0.6)
        else
            local hits = api:mapActorAttack(uuid, 6, 3, 'minecraft:weakness', 60)
            api:healMapActor(uuid, hits * 5)
            api:spawnParticleAdv('minecraft:witch', current.x, current.y + 1, current.z, 32, 2, 0.5, 2, 0.02)
        end
    end)
end

function balnazzar_death(uuid)

end

-- Configure an existing Aircraft Spawner at these block coordinates.
-- The same options are available in its normal menu; no Lua is required.
function StratholmeNecropolis(x, y, z)
    return api:configureAmbientDevice(x, y, z, {
        enabled = true, requires_power = false,
        scenery = { variant = 'necropolis', altitude = 24, heading = 35,
                    size = 24, flightSpeed = 0, amount = 1 }
    })
end
