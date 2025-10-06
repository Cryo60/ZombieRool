package net.mcreator.zombierool;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.network.chat.Component; // Import for Component.literal
import net.minecraft.server.level.ServerPlayer; // Import for ServerPlayer

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ZombieWorldHandler {

    private static final TagKey<EntityType<?>> ALLOWED_MOBS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("zombierool", "allowed_mobs"));
    
    private static final TagKey<Item> ALLOWED_ITEMS =
            TagKey.create(Registries.ITEM, new ResourceLocation("zombierool", "allowed_items"));

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                // Get the WorldConfig for the current level
                WorldConfig worldConfig = WorldConfig.get(level);
                String dayNightMode = worldConfig.getDayNightMode();

                // Apply time based on the configured mode
                switch (dayNightMode) {
                    case "day":
                        level.setDayTime(6000);
                        break;
                    case "night":
                        level.setDayTime(18000);
                        break;
                    case "cycle":
                        // Do nothing, let Minecraft handle the natural cycle
                        break;
                    default:
                        level.setDayTime(18000);
                        System.err.println("[ZombieWorldHandler] Invalid dayNightMode in config: " + dayNightMode + ". Defaulting to night.");
                        break;
                }
                
                if (level.getGameTime() % 40 == 0) {
                    List<Entity> entities = new ArrayList<>();
                    level.getAllEntities().forEach(entities::add);
                    
                    for (Entity entity : entities) {
                        if (shouldDespawn(entity)) {
                            entity.remove(Entity.RemovalReason.DISCARDED);
                        }
                    }
                }

                // Removed the periodic sending of particle state messages
                // This will now only happen when a player joins, or via the command.
            }
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().remove(Entity.RemovalReason.DISCARDED);
        }
    }
    
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) { // Changed to ServerPlayer for clarity
            // If it's a server player joining, send them the current particle config
            ServerLevel level = player.serverLevel();
            WorldConfig worldConfig = WorldConfig.get(level);

            boolean particlesEnabled = worldConfig.areParticlesEnabled();
            ResourceLocation particleTypeId = worldConfig.getParticleTypeId();
            String particleDensity = worldConfig.getParticleDensity();
            String particleMode = worldConfig.getParticleMode(); // NOUVEAU: Obtenir le mode des particules

            if (particlesEnabled && particleTypeId != null) {
                // Envoyer TOUS les paramètres au client (ID, Densité, Mode)
                player.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_ENABLE:" + particleTypeId.toString() + ":" + particleDensity + ":" + particleMode), true); 
            } else if (!particlesEnabled) {
                player.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_DISABLE"), true);
            }

            // Also send fog preset when player joins
            String fogPreset = worldConfig.getFogPreset();
            player.sendSystemMessage(Component.literal("ZOMBIEROOL_FOG_PRESET:" + fogPreset), true);

            if (shouldDespawn(event.getEntity())) {
                event.getEntity().remove(Entity.RemovalReason.DISCARDED);
                event.setCanceled(true);
            }
        } else if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
            event.getEntity().remove(Entity.RemovalReason.DISCARDED);
            event.setCanceled(true);
        }
    }

    private static boolean shouldDespawn(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        
        // Entités toujours autorisées
        if (entity instanceof Player ||
            entity instanceof net.minecraft.world.entity.decoration.ArmorStand ||
            entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart ||
            entity instanceof net.minecraft.world.entity.vehicle.Boat ||
            entity instanceof net.minecraft.world.entity.decoration.ItemFrame ||
            entity instanceof net.minecraft.world.entity.decoration.Painting ||
            entity instanceof net.minecraft.world.entity.projectile.Projectile ||
            entity instanceof net.minecraft.world.entity.item.PrimedTnt ||
            entity instanceof net.minecraft.world.entity.projectile.FishingHook ||
            entity instanceof AreaEffectCloud) {
            return false;
        }
        
        // Gestion des items dropés
        if (entity instanceof ItemEntity itemEntity) {
            return !itemEntity.getItem().is(ALLOWED_ITEMS);
        }
        
        // Gestion des mobs
        try {
            if (entity.getType().is(ALLOWED_MOBS)) {
                return false;
            }
        } catch (Exception e) {
            return true;
        }
        
        return true;
    }
}
