package me.cryo.zombierool.event;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncDynamicSkinPacket;
import me.cryo.zombierool.network.packet.S2CSyncWeatherPacket;
import me.cryo.zombierool.network.packet.S2CSyncBowieKnifePacket;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.world.entity.AreaEffectCloud;

@Mod.EventBusSubscriber(modid = me.cryo.zombierool.ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {
    public static final String OBJECTIVE_ID = "zr_score";
    private static final Queue<ServerPlayer> playersToUpdateFog = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            if (server == null) return;
            
            if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                DynamicResourceManager.loadWorldResources(serverLevel);
                LuaScriptManager.loadScripts(serverLevel);
            }
            
            GameRules gameRules = serverLevel.getGameRules();
            if (gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            }

            WorldConfig config = WorldConfig.get(serverLevel);
            config.refreshAllChunkTickets(serverLevel);
            try {
                java.io.File worldDir = serverLevel.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
                java.io.File mapJson = new java.io.File(worldDir, "zombierool_map.json");
                if (mapJson.exists()) {
                    try (java.io.FileReader reader = new java.io.FileReader(mapJson)) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("id")) config.setMapId(json.get("id").getAsString());
                        if (json.has("resource_pack")) {
                            com.google.gson.JsonObject rp = json.getAsJsonObject("resource_pack");
                            if (rp.has("url")) config.setResourcePackUrl(rp.get("url").getAsString());
                            if (rp.has("name")) config.setResourcePackName(rp.get("name").getAsString());
                        } else {
                            if (json.has("resource_pack_url")) config.setResourcePackUrl(json.get("resource_pack_url").getAsString());
                            if (json.has("resource_pack_name")) config.setResourcePackName(json.get("resource_pack_name").getAsString());
                        }
                        if (json.has("overrides")) {
                            com.google.gson.JsonObject overrides = json.getAsJsonObject("overrides");
                            if (overrides.has("spooky_ambience")) config.setSpookyAmbience(overrides.get("spooky_ambience").getAsBoolean());
                            if (overrides.has("force_halloween")) config.setForceHalloween(overrides.get("force_halloween").getAsBoolean());
                        } else {
                            if (json.has("spooky_ambience")) config.setSpookyAmbience(json.get("spooky_ambience").getAsBoolean());
                            if (json.has("force_halloween")) config.setForceHalloween(json.get("force_halloween").getAsBoolean());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        Scoreboard scoreboard = level.getScoreboard();
        var objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE_ID,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Points"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        }
        scoreboard.setDisplayObjective(1, objective);

        player.getPersistentData().remove("zr_has_bowie_knife");

        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.resetStats();
            cap.resetPerkPurchases();
            cap.setLethalType("zombierool:grenade");
            cap.setLethalCount(5);

            if (WaveManager.isGameRunning()) {
                cap.setPoints(0);
                player.setGameMode(GameType.SPECTATOR);
                player.getInventory().clearContent();
                player.sendSystemMessage(Component.translatable("message.zombierool.join_spectator").withStyle(ChatFormatting.GRAY));
            } else {
                cap.setPoints(500);
            }
            
            var safeObjective = scoreboard.getObjective(OBJECTIVE_ID);
            if (safeObjective != null) {
                var score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), safeObjective);
                score.setScore(cap.getPoints());
            }

            cap.sync(player);
        });

        PlayerStatsManager.syncAll(level);

        WorldConfig worldConfig = WorldConfig.get(level);
        playersToUpdateFog.add(player);

        String musicPreset = worldConfig.getMusicPreset();
        if (musicPreset == null || musicPreset.isEmpty()) musicPreset = "default";
        player.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + musicPreset));

        boolean particlesEnabled = worldConfig.areParticlesEnabled();
        ResourceLocation particleTypeId = worldConfig.getParticleTypeId();
        String particleDensity = worldConfig.getParticleDensity();
        String particleMode = worldConfig.getParticleMode();
        if (particlesEnabled && particleTypeId != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncWeatherPacket(true, particleTypeId.toString(), particleDensity, particleMode));
        } else {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncWeatherPacket(false, "", "", ""));
        }

        for (Map.Entry<String, Map<String, byte[]>> entry : DynamicResourceManager.getAllServerSkins().entrySet()) {
            String mobType = entry.getKey();
            for (Map.Entry<String, byte[]> skinEntry : entry.getValue().entrySet()) {
                String skinId = skinEntry.getKey();
                byte[] data = skinEntry.getValue();
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncDynamicSkinPacket(mobType, skinId, data));
            }
        }

        DynamicResourceManager.sendAudioToPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove("zr_has_bowie_knife");
            player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.setLethalType("zombierool:grenade");
                cap.setLethalCount(5);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playersToUpdateFog.add(player);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            net.minecraft.core.BlockPos pos = event.getPos();
            LuaScriptManager.callEvent("OnBlockInteract", player.getUUID().toString(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
            String itemId = rl != null ? rl.toString() : "unknown";
            LuaScriptManager.callEvent("OnItemUsed", player.getUUID().toString(), itemId);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        while (!playersToUpdateFog.isEmpty()) {
            ServerPlayer player = playersToUpdateFog.poll();
            if (player != null && player.isAlive()) {
                updateFogForPlayer(player);
            }
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            WorldConfig worldConfig = WorldConfig.get(level);
            
            String dayNightMode = worldConfig.getDayNightMode();
            switch (dayNightMode) {
                case "day" -> level.setDayTime(6000);
                case "night" -> level.setDayTime(18000);
                case "cycle" -> {}
            }

            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
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
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
            ItemStack stack = event.getItem().getItem();
            int limit = me.cryo.zombierool.core.system.WeaponFacade.getWeaponLimit(player);

            if (me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack)) {
                boolean hasSpace = false;
                for (int i = 1; i <= limit; i++) {
                    if (!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(player.getInventory().getItem(i))) {
                        hasSpace = true;
                        break;
                    }
                }
                if (!hasSpace) {
                    event.setCanceled(true);
                }
            } else {
                boolean hasSpace = false;
                for (int i = limit + 1; i < 36; i++) {
                    if (player.getInventory().getItem(i).isEmpty()) {
                        hasSpace = true;
                        break;
                    }
                }
                if (!hasSpace) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.player;
            ServerLevel level = (ServerLevel) player.level();

            boolean currentBowie = player.getPersistentData().getBoolean("zr_has_bowie_knife");
            boolean lastBowie = player.getPersistentData().getBoolean("zr_last_synced_bowie");
            if (currentBowie != lastBowie) {
                player.getPersistentData().putBoolean("zr_last_synced_bowie", currentBowie);
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), 
                    new S2CSyncBowieKnifePacket(player.getId(), currentBowie)
                );
            }

            if (WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
                int limit = me.cryo.zombierool.core.system.WeaponFacade.getWeaponLimit(player);

                ItemStack slot0 = player.getInventory().getItem(0);
                if (!slot0.isEmpty()) {
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    boolean added = false;
                    
                    if (me.cryo.zombierool.core.system.WeaponFacade.isWeapon(slot0)) {
                        for (int w = 1; w <= limit; w++) {
                            if (!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(player.getInventory().getItem(w))) {
                                player.getInventory().setItem(w, slot0);
                                added = true;
                                break;
                            }
                        }
                    }
                    if (!added) {
                        for (int i = limit + 1; i < 36; i++) {
                            if (player.getInventory().getItem(i).isEmpty()) {
                                player.getInventory().setItem(i, slot0);
                                added = true;
                                break;
                            }
                        }
                    }
                    if (!added) {
                        player.drop(slot0, false);
                    }
                }

                ItemStack offhand = player.getInventory().getItem(40);
                if (!offhand.isEmpty()) {
                    player.getInventory().setItem(40, ItemStack.EMPTY);
                    boolean added = false;
                    
                    if (me.cryo.zombierool.core.system.WeaponFacade.isWeapon(offhand)) {
                        for (int w = 1; w <= limit; w++) {
                            if (!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(player.getInventory().getItem(w))) {
                                player.getInventory().setItem(w, offhand);
                                added = true;
                                break;
                            }
                        }
                    }
                    if (!added) {
                        for (int i = limit + 1; i < 36; i++) {
                            if (player.getInventory().getItem(i).isEmpty()) {
                                player.getInventory().setItem(i, offhand);
                                added = true;
                                break;
                            }
                        }
                    }
                    if (!added) {
                        player.drop(offhand, false);
                    }
                }

                for (int i = 1; i < 36; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.isEmpty()) continue;

                    boolean isWeapon = me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack);
                    boolean isWeaponSlot = (i >= 1 && i <= limit);

                    if (isWeapon && !isWeaponSlot) {
                        boolean moved = false;
                        for (int w = 1; w <= limit; w++) {
                            if (!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(player.getInventory().getItem(w))) {
                                ItemStack swap = player.getInventory().getItem(w);
                                player.getInventory().setItem(w, stack.copy());
                                player.getInventory().setItem(i, swap);
                                moved = true;
                                break;
                            }
                        }
                        if (!moved) {
                            player.drop(stack.copy(), false);
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    } 
                    else if (!isWeapon && isWeaponSlot) {
                        boolean moved = false;
                        for (int u = limit + 1; u < 36; u++) {
                            if (player.getInventory().getItem(u).isEmpty()) {
                                player.getInventory().setItem(u, stack.copy());
                                player.getInventory().setItem(i, ItemStack.EMPTY);
                                moved = true;
                                break;
                            }
                        }
                        if (!moved) {
                            player.drop(stack.copy(), false);
                            player.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                }

                int sel = player.getInventory().selected;
                if (sel > limit && player.getInventory().getItem(sel).isEmpty()) {
                    player.getInventory().selected = 1;
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(1));
                }
            }

            WorldConfig worldConfig = WorldConfig.get(level);
            long now = level.getGameTime();

            if (now % 10 == 0) {
                net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(15.0);
                boolean seesZombie = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e ->
                        (e instanceof me.cryo.zombierool.entity.ZombieEntity
                                || e instanceof me.cryo.zombierool.entity.HellhoundEntity
                                || e instanceof me.cryo.zombierool.entity.CrawlerEntity)
                                && player.hasLineOfSight(e)
                ).size() > 0;
                if (seesZombie) {
                    me.cryo.zombierool.util.PlayerVoiceManager.onPlayerSeeZombie(player);
                }
            }

            long lastShot = me.cryo.zombierool.util.PlayerVoiceManager.getLastShotTime(player);
            long lastSeen = me.cryo.zombierool.util.PlayerVoiceManager.getLastZombieSeenTime(player);

            if (now - lastShot > 600 && now - lastSeen > 600) {
                me.cryo.zombierool.util.PlayerVoiceManager.playRandomChatter(player, level);
                me.cryo.zombierool.util.PlayerVoiceManager.onPlayerShoot(player);
                me.cryo.zombierool.util.PlayerVoiceManager.onPlayerSeeZombie(player);
            }

            if (worldConfig.isColdWaterEffectEnabled()) {
                boolean inWater = player.isUnderWater() || player.isInWater();
                me.cryo.zombierool.ColdWaterEffectManager.updateIntensity(level, player, inWater);

                final float SLOWNESS_THRESHOLD = 0.20f;
                if (me.cryo.zombierool.ColdWaterEffectManager.getIntensity(player) >= SLOWNESS_THRESHOLD) {
                    if (!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                            || player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() < 2) {
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
                    }
                    if (player.tickCount % 5 == 0) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                                player.getX(), player.getY() + player.getBbHeight() / 2.0D, player.getZ(),
                                5, 0.2D, 0.5D, 0.2D, 0.0D);
                    }
                }
            } else {
                if (me.cryo.zombierool.ColdWaterEffectManager.getIntensity(player) > 0.0f) {
                    me.cryo.zombierool.ColdWaterEffectManager.resetIntensity(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
            event.getEntity().remove(Entity.RemovalReason.DISCARDED);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().remove(Entity.RemovalReason.DISCARDED);
        }
    }

    private static void updateFogForPlayer(ServerPlayer player) {
        WorldConfig worldConfig = WorldConfig.get(player.serverLevel());
        String clientFogPreset;

        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            clientFogPreset = "none";
        } else {
            clientFogPreset = worldConfig.getFogPreset();
        }

        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new me.cryo.zombierool.network.packet.S2CSetFogPresetPacket(
                clientFogPreset,
                worldConfig.getCustomFogR(), worldConfig.getCustomFogG(), worldConfig.getCustomFogB(),
                worldConfig.getCustomFogNear(), worldConfig.getCustomFogFar()
        ));
    }

    private static boolean shouldDespawn(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }

        if (entity instanceof Player
                || entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart
                || entity instanceof net.minecraft.world.entity.vehicle.Boat
                || entity instanceof net.minecraft.world.entity.decoration.ItemFrame
                || entity instanceof net.minecraft.world.entity.decoration.Painting
                || entity instanceof net.minecraft.world.entity.projectile.Projectile
                || entity instanceof net.minecraft.world.entity.item.PrimedTnt
                || entity instanceof net.minecraft.world.entity.projectile.FishingHook
                || entity instanceof AreaEffectCloud) {
            return false;
        }

        if (entity.level() instanceof ServerLevel level) {
            WorldConfig config = WorldConfig.get(level);

            if (entity instanceof ItemEntity itemEntity) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(itemEntity.getItem().getItem());
                if (rl != null && config.getAllowedItems().contains(rl.toString())) {
                    return false;
                }
                return true;
            }

            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (rl != null && config.getAllowedMobs().contains(rl.toString())) {
                return false;
            }
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer targetPlayer && event.getEntity() instanceof ServerPlayer tracker) {
            boolean hasBowie = targetPlayer.getPersistentData().getBoolean("zr_has_bowie_knife");
            if (hasBowie) {
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> tracker),
                    new S2CSyncBowieKnifePacket(targetPlayer.getId(), true)
                );
            }
        }
    }
}