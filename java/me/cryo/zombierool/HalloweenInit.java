package me.cryo.zombierool;

import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.configuration.HalloweenConfig;

/**
 * Classe d'initialisation pour les fonctionnalités Halloween
 * Cette classe s'exécute automatiquement au démarrage du mod
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
public class HalloweenInit {
    
    static {
        // Enregistrement automatique de la configuration Halloween
        HalloweenConfig.register();
    }
}