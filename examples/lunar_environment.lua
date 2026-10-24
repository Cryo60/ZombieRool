-- Copier dans <monde>/zr_scripts/ pour utiliser ces fonctions.
-- Sans Lua : Map Config > General > Environnement lunaire,
-- ou placer un bloc Zone d'atmosphere et clic droit en creatif.

function activer_lune()
    ZombieroolAPI:configureLunarEnvironment(0.167, true, 5)
end

function ajouter_sas()
    ZombieroolAPI:setAtmosphereZone("sas", 10,64,10, 25,72,25, 1,false)
end

function depressuriser_sas()
    ZombieroolAPI:setAtmosphereZone("sas", 10,64,10, 25,72,25, 0.167,true)
end

function supprimer_sas()
    ZombieroolAPI:removeAtmosphereZone("sas")
end

-- Pour automatiser au debut de partie, appeler activer_lune() depuis
-- votre fonction OnGameStart deja existante. Les zones des blocs sont
-- sauvegardees directement, il n'est pas necessaire de les recreer ici.
