package net.mcreator.zombierool;

import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MapCreationHandler {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            MinecraftServer server = serverLevel.getServer();

            if (server == null) return;

            GameRules gameRules = serverLevel.getGameRules();

            // Disable mob spawning if it's enabled by default
            if (gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                System.out.println("Gamerule doMobSpawning set to false for new world.");
            }

            // Temporarily commented out due to "cannot find symbol" error for GameRules.PVP
            // This suggests an issue with MCreator's mappings or libraries.
            /*
            if (gameRules.getBoolean(GameRules.PVP)) {
                gameRules.getRule(GameRules.PVP).set(false, server);
                System.out.println("Gamerule pvp set to false for new world.");
            }
            */
        }
    }
}