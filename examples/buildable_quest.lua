-- Exemple à adapter à votre carte. Les quatre blocs ci-dessous doivent exister.
-- Ce fichier est un exemple : il n’est pas chargé automatiquement par le mod.
-- Une récompense event n’a pas besoin d’objet : {} suffit.
ZombieroolAPI:defineBuildable(
    "relay", "Relais de secours", "battery,coil,fuse", "{}", "event", 40, 1
)
ZombieroolAPI:bindBuildablePart("relay", "battery", 10, 64, 10)
ZombieroolAPI:bindBuildablePart("relay", "coil", 14, 64, 10)
ZombieroolAPI:bindBuildablePart("relay", "fuse", 18, 64, 10)
ZombieroolAPI:bindBuildableBench("relay", 22, 64, 10)

function OnBuildableCompleted(uuid, recipe, x, y, z)
    if recipe ~= "relay" then return end
    print("Relais assemblé par l’équipe à " .. x .. "," .. y .. "," .. z)
end

function OnBuildableClaimed(uuid, recipe, x, y, z, copiesTaken)
    if recipe ~= "relay" then return end
    -- Exemple : alimente un circuit existant par la pose d’un bloc de redstone.
    -- Le journal de partie de l’Overworld rétablit l’état original en fin de partie.
    ZombieroolAPI:setBlock(x, y - 1, z, "minecraft:redstone_block")
end
