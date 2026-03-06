package me.cryo.zombierool.event;

import me.cryo.zombierool.ColdWaterEffectManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.SetFogPresetPacket;
import me.cryo.zombierool.network.packet.SyncWeatherPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

	public static final String OBJECTIVE_ID = "zr_score"; 

	private static final TagKey<EntityType<?>> ALLOWED_MOBS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("zombierool", "allowed_mobs"));
	private static final TagKey<Item> ALLOWED_ITEMS = TagKey.create(Registries.ITEM, new ResourceLocation("zombierool", "allowed_items"));

	private static final Queue<ServerPlayer> playersToUpdateFog = new ConcurrentLinkedQueue<>();

	@SubscribeEvent
	public static void onServerStart(ServerAboutToStartEvent event) {
	    MinecraftServer server = event.getServer();
	    Scoreboard scoreboard = server.getScoreboard();
	    if (scoreboard.getObjective(OBJECTIVE_ID) == null) {
	        scoreboard.addObjective(
	            OBJECTIVE_ID,
	            ObjectiveCriteria.DUMMY,
	            Component.literal("Points"),
	            ObjectiveCriteria.RenderType.INTEGER
	        );
	    }
	}

	@SubscribeEvent
	public static void onWorldLoad(LevelEvent.Load event) {
	    if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel serverLevel) {
	        MinecraftServer server = serverLevel.getServer();
	        if (server == null) return;

	        GameRules gameRules = serverLevel.getGameRules();
	        if (gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
	            gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
	        }
	        
	        WorldConfig.get(serverLevel).refreshAllChunkTickets(serverLevel);
	    }
	}

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
	    if (event.getEntity() instanceof ServerPlayer player) {
	        ServerLevel level = player.serverLevel();
	        Scoreboard scoreboard = level.getScoreboard();

	        var objective = scoreboard.getObjective(OBJECTIVE_ID);
	        if (objective != null) {
	            var score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
	            if (score.getScore() == 0) {
	                score.setScore(500); 
	            }
	            scoreboard.setDisplayObjective(1, objective);
	        }

	        WorldConfig worldConfig = WorldConfig.get(level);
	        playersToUpdateFog.add(player);

	        boolean particlesEnabled = worldConfig.areParticlesEnabled();
	        ResourceLocation particleTypeId = worldConfig.getParticleTypeId();
	        String particleDensity = worldConfig.getParticleDensity();
	        String particleMode = worldConfig.getParticleMode();

	        if (particlesEnabled && particleTypeId != null) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
                    new SyncWeatherPacket(true, particleTypeId.toString(), particleDensity, particleMode));
	        } else {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
                    new SyncWeatherPacket(false, "", "", ""));
	        }
	    }
	}

	@SubscribeEvent
	public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
	    if (event.getEntity() instanceof ServerPlayer player) {
	        playersToUpdateFog.add(player);
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
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
	    if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
	        ServerPlayer player = (ServerPlayer) event.player;
	        ServerLevel level = (ServerLevel) player.level();
	        WorldConfig worldConfig = WorldConfig.get(level);

	        if (worldConfig.isColdWaterEffectEnabled()) {
	            boolean inWater = player.isUnderWater() || player.isInWater();
	            ColdWaterEffectManager.updateIntensity(level, player, inWater);

	            final float SLOWNESS_THRESHOLD = 0.20f;
	            if (ColdWaterEffectManager.getIntensity(player) >= SLOWNESS_THRESHOLD) {
	                if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() < 2) {
	                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
	                }
	                if (player.tickCount % 5 == 0) {
	                    level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + player.getBbHeight() / 2.0D, player.getZ(), 5, 0.2D, 0.5D, 0.2D, 0.0D);
	                }
	            }
	        } else {
	            if (ColdWaterEffectManager.getIntensity(player) > 0.0f) {
	                ColdWaterEffectManager.resetIntensity(player);
	            }
	        }
	    }
	}

	@SubscribeEvent
	public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
	    if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
	        event.getEntity().remove(Entity.RemovalReason.DISCARDED);
	        event.setCanceled(true);
	    }
	}

	@SubscribeEvent
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
	    if (!event.getLevel().isClientSide() && shouldDespawn(event.getEntity())) {
	        event.setCanceled(true);
	        event.getEntity().remove(Entity.RemovalReason.DISCARDED);
	    }
	}

	private static void updateFogForPlayer(ServerPlayer player) {
	    WorldConfig worldConfig = WorldConfig.get(player.serverLevel());
	    String clientFogPreset;

	    if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
	        clientFogPreset = "none";
	    } else {
	        clientFogPreset = worldConfig.getFogPreset();
	    }

	    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SetFogPresetPacket(
            clientFogPreset, 
            worldConfig.getCustomFogR(), worldConfig.getCustomFogG(), worldConfig.getCustomFogB(), 
            worldConfig.getCustomFogNear(), worldConfig.getCustomFogFar()
        ));
	}

	private static boolean shouldDespawn(Entity entity) {
	    if (entity == null || !entity.isAlive() || entity.isRemoved()) {
	        return false;
	    }
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
	    if (entity instanceof ItemEntity itemEntity) {
	        return !itemEntity.getItem().is(ALLOWED_ITEMS);
	    }
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