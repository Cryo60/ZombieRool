-- Live script of the Les Ateliers demonstration map.
-- Recipes and physical bindings are already saved in the world's buildable SavedData.
function OnGameStart()
    ZombieroolAPI:broadcastMessage("§6LES ATELIERS §7— Une pièce à la fois. MP5 au nord, bouclier à l’ouest, relais à l’est.")
    ZombieroolAPI:broadcastMessage("§eObjectif : §fassembler le relais et le récupérer pour ouvrir le local technique.")
end

function OnBuildableCompleted(uuid, recipe, x, y, z)
    ZombieroolAPI:broadcastMessage("§aConstruction terminée : §f" .. recipe .. "§a. Récupérez le résultat sur l’établi.")
end

function OnBuildableClaimed(uuid, recipe, x, y, z, copiesTaken)
    if recipe ~= "relais" then return end
    for dx = 16, 18 do
        for dy = 65, 67 do
            ZombieroolAPI:setBlock(dx, dy, -9, "minecraft:air")
        end
    end
    ZombieroolAPI:broadcastMessage("§6Relais connecté. §fLocal technique ouvert : courant, Juggernog et Pack-a-Punch.")
end
