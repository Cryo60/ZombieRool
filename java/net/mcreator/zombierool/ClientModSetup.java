package net.mcreator.zombierool.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.client.render.ZombieMoonRenderer;
import net.minecraftforge.event.entity.player.PlayerEvent; // Import PlayerEvent
import net.minecraft.network.chat.Component;
import net.mcreator.zombierool.WorldConfig; // Import WorldConfig
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModSetup {
    
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
            new ResourceLocation("overworld"), // Remplace l'overworld vanilla.
            new ZombieMoonRenderer()
        );
    }

    // New event handler to send config to players when they log in
    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE) // This part needs to be FORGE bus
    public static class ServerEvents {
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerLevel level = player.server.overworld(); // Assuming the config applies to the overworld
                WorldConfig worldConfig = WorldConfig.get(level);
                String currentPreset = worldConfig.getFogPreset();
                
                // Send the fog preset to the newly logged-in player
                player.sendSystemMessage(Component.literal("ZOMBIEROOL_FOG_PRESET:" + currentPreset), true);
            }
        }
    }
}