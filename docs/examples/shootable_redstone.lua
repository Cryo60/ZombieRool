-- Paste this at the end of main.lua, then Check > Save > Reload.
local zr_shootable_done = false
local zr_previous_block_shot = OnBlockShot

function OnBlockShot(playerUUID, x, y, z)
    if zr_previous_block_shot then
        zr_previous_block_shot(playerUUID, x, y, z)
    end

    if zr_shootable_done or x ~= -7 or y ~= -50 or z ~= -7 then
        return
    end
    zr_shootable_done = true

    ZombieroolAPI:spawnParticle("minecraft:explosion_emitter", -6.5, -49.5, -6.5)
    ZombieroolAPI:setBlock(-7, -50, -7, "minecraft:air")
    ZombieroolAPI:setBlock(-2, -62, -12, "minecraft:redstone_block")
end
