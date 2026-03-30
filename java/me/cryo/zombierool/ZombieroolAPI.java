package me.cryo.zombierool.scripting;

import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket;
import me.cryo.zombierool.network.packet.S2CSetFogPresetPacket;
import me.cryo.zombierool.network.packet.S2CTriggerScopeScreamerPacket;
import me.cryo.zombierool.block.system.ObstacleDoorSystem;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.player.PlayerDownManager;
import me.cryo.zombierool.player.PlayerCrawlManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ZombieroolAPI {

    private final ServerLevel level;
    private static final Map<UUID, ServerBossEvent> playerObjectives = new ConcurrentHashMap<>();

    public ZombieroolAPI(ServerLevel level) {
        this.level = level;
    }

    public void registerInteractable(String id, double x, double y, double z, double radius, String langKey) {
        me.cryo.zombierool.core.manager.InteractableManager.register(id, new Vec3(x, y, z), radius, langKey);
    }

    public void removeInteractable(String id) {
        me.cryo.zombierool.core.manager.InteractableManager.remove(id);
    }

    public void registerTimer(String id, int delayTicks, LuaValue callback) {
        LuaScriptManager.registerTimer(id, delayTicks, callback);
    }

    public void cancelTimer(String id) {
        LuaScriptManager.cancelTimer(id);
    }

    public void setData(String key, String value) {
        LuaScriptManager.setData(key, value);
    }

    public String getData(String key) {
        return LuaScriptManager.getData(key);
    }

    public LuaTable getNearbyPlayers(double x, double y, double z, double radius) {
        LuaTable table = new LuaTable();
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box, p -> 
            p.distanceToSqr(x, y, z) <= radius * radius && 
            (p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
        );

        int i = 1;
        for (ServerPlayer p : players) {
            table.set(i++, LuaValue.valueOf(p.getUUID().toString()));
        }
        return table;
    }

    public int getEntityCount(String entityTypeStr) {
        ResourceLocation rl = new ResourceLocation(entityTypeStr.contains(":") ? entityTypeStr : "zombierool:" + entityTypeStr);
        return (int) StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).equals(rl))
                .count();
    }

    public float getPlayerHealth(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getHealth() : 0f;
    }

    public float getPlayerMaxHealth(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getMaxHealth() : 0f;
    }

    public boolean isPlayerDown(String uuidStr) {
        try {
            return PlayerDownManager.isPlayerDown(UUID.fromString(uuidStr));
        } catch (Exception e) { return false; }
    }

    public boolean isPlayerCrawling(String uuidStr) {
        try {
            return PlayerCrawlManager.isCrawling(UUID.fromString(uuidStr));
        } catch (Exception e) { return false; }
    }

    public float getPlayerYaw(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getYRot() : 0f;
    }

    public float getPlayerPitch(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getXRot() : 0f;
    }

    public String getBlockState(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        BlockState state = level.getBlockState(pos);
        return state.toString();
    }

    public void setPlayerGameMode(String uuidStr, String modeStr) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            try {
                GameType mode = GameType.valueOf(modeStr.toUpperCase());
                player.setGameMode(mode);
            } catch (Exception ignored) {}
        }
    }

    public void setWave(int wave) {
        WaveManager.forceSetWave(level, wave);
    }

    public void pauseWave() {
        WaveManager.setGamePaused(true);
    }

    public void resumeWave() {
        WaveManager.setGamePaused(false);
    }

    public void setMaxZombiesAlive(int count) {
        WorldConfig.get(level).setMaxActiveMobsPerPlayer(count);
        WorldConfig.get(level).setDirty();
    }

    public void showTitle(String uuidStr, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            if (title != null && !title.isEmpty()) {
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            }
        }
    }

    public void showActionBar(String uuidStr, String message) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null && message != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    public void playSound(double x, double y, double z, String soundId, float volume, float pitch) {
        ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "minecraft:" + soundId);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (sound != null) {
            level.playSound(null, x, y, z, sound, SoundSource.MASTER, volume, pitch);
        }
    }

    public void setGlowing(String uuidStr, boolean glowing) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            player.setGlowingTag(glowing);
        }
    }

    public void setObjective(String uuidStr, String text) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player == null) return;
        UUID pId = player.getUUID();

        if (text == null || text.isEmpty()) {
            if (playerObjectives.containsKey(pId)) {
                playerObjectives.get(pId).removeAllPlayers();
                playerObjectives.remove(pId);
            }
        } else {
            ServerBossEvent be = playerObjectives.computeIfAbsent(pId, k -> {
                ServerBossEvent boss = new ServerBossEvent(Component.literal(text), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
                boss.addPlayer(player);
                return boss;
            });
            be.setName(Component.literal(text));
        }
    }

    public void giveRandomWeapon(String uuidStr, String tier) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            boolean pap = false;
            String searchTier = tier != null ? tier.toUpperCase(Locale.ROOT) : "";
            
            if (searchTier.contains("PAP")) {
                pap = true;
                searchTier = searchTier.replace("PAP", "").trim();
            }
            
            List<String> candidates = new ArrayList<>();
            for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                boolean match = false;
                if (searchTier.isEmpty() || searchTier.equals("ALL") || searchTier.equals("ANY")) {
                    match = true;
                } else if (searchTier.equals("WONDER")) {
                    match = def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type);
                } else if (def.type != null && def.type.equalsIgnoreCase(searchTier)) {
                    match = true;
                } else if (def.tags != null && def.tags.contains(searchTier.toLowerCase(Locale.ROOT))) {
                    match = true;
                }
                
                if (match) {
                    candidates.add(def.id);
                }
            }
            
            if (!candidates.isEmpty()) {
                String chosen = candidates.get(level.random.nextInt(candidates.size()));
                ItemStack weapon = WeaponFacade.createWeaponStack(chosen, pap, player);
                if (!weapon.isEmpty()) {
                    WeaponFacade.grantWeaponToPlayer(player, weapon);
                }
            }
        }
    }

    public void addPoints(String playerUUIDStr, int amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            PointManager.modifyScore(player, amount);
        }
    }

    public void removePoints(String playerUUIDStr, int amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            PointManager.modifyScore(player, -amount);
        }
    }

    public int getScore(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? PointManager.getScore(player) : 0;
    }

    public void spawnBoss(double x, double y, double z, String mobType) {
        spawnMob(x, y, z, mobType);
    }

    public void spawnMob(double x, double y, double z, String mobType) {
        ResourceLocation rl = new ResourceLocation(mobType.contains(":") ? mobType : "zombierool:" + mobType);
        net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type != null) {
            Entity entity = type.create(level);
            if (entity != null) {
                entity.setPos(x, y, z);
                if (entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zrEntity) {
                    zrEntity.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin(rl.getPath()));
                }
                level.addFreshEntity(entity);
            }
        }
    }

    public void spawnCustomBoss(double x, double y, double z, String mobType, float hp, float speed, float scale) {
        ResourceLocation rl = new ResourceLocation(mobType.contains(":") ? mobType : "zombierool:" + mobType);
        net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type != null) {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                living.setPos(x, y, z);
                living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
                living.setHealth(hp);
                
                if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                    living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                }
                
                if (living instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zrEntity) {
                    zrEntity.setScale(scale);
                    String skin = me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin(rl.getPath());
                    if (skin == null || skin.isEmpty()) {
                        java.util.Map<String, byte[]> skins = me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().get(rl.getPath());
                        if (skins != null && !skins.isEmpty()) {
                            skin = skins.keySet().iterator().next();
                        }
                    }
                    if (skin != null && !skin.isEmpty()) {
                        zrEntity.setCustomSkin(skin);
                    }
                }
                
                level.addFreshEntity(living);
            }
        }
    }

    public void giveWeapon(String playerUUIDStr, String weaponId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ItemStack weapon = WeaponFacade.createWeaponStack(weaponId, false, player);
            if (!weapon.isEmpty()) {
                WeaponFacade.grantWeaponToPlayer(player, weapon);
            }
        }
    }

    public void packAPunchWeapon(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (WeaponFacade.isWeapon(held) && WeaponFacade.canBePackAPunched(held)) {
                WeaponFacade.applyPackAPunch(held);
                WeaponFacade.setAmmo(held, WeaponFacade.getMaxAmmo(held));
                WeaponFacade.setReserve(held, WeaponFacade.getMaxReserve(held));
                player.inventoryMenu.broadcastChanges();
            }
        }
    }

    public void givePerk(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.PerksManager.Perk perk = null;

            if (perkId.equalsIgnoreCase("random")) {
                List<me.cryo.zombierool.PerksManager.Perk> unowned = me.cryo.zombierool.PerksManager.ALL_PERKS.values().stream()
                        .filter(p -> p.getAssociatedEffect() != null && !player.hasEffect(p.getAssociatedEffect()))
                        .collect(Collectors.toList());
                
                if (!unowned.isEmpty()) {
                    perk = unowned.get(level.random.nextInt(unowned.size()));
                }
            } else {
                perk = me.cryo.zombierool.PerksManager.ALL_PERKS.get(perkId);
            }

            if (perk != null) {
                perk.applyEffect(player);
                me.cryo.zombierool.PerksManager.incrementPerkPurchases(perk.getId(), player);
                player.sendSystemMessage(Component.literal("§aAtout " + perk.getName() + " obtenu !"));
            }
        }
    }

    public void unlockChannel(int channel) {
        WaveManager.unlockChannel(channel);
    }

    public void lockChannel(int channel) {
        try {
            WaveManager.lockChannel(channel);
        } catch (Exception ignored) {}
    }

    public void clearUnlockedChannels() {
        WaveManager.UNLOCKED_CHANNELS.clear();
    }
    
    public void clearUnlockedZones() {
        WaveManager.UNLOCKED_ZONES.clear();
    }

    public void setSpawnIntensity(String intensity) {
        try {
            WorldConfig config = WorldConfig.get(level);
            config.setSpawnIntensity(intensity);
            config.setDirty();
            WaveManager.recalculateSpawnInterval(level);
        } catch (Exception ignored) {}
    }

    public void setSpecialWavesEnabled(boolean enabled) {
        try {
            WorldConfig config = WorldConfig.get(level);
            config.setSpecialWavesEnabled(enabled);
            config.setDirty();
        } catch (Exception ignored) {}
    }

    public void openObstacle(String channel) {
        try {
            int c = Integer.parseInt(channel);
            WaveManager.unlockChannel(c);
            
            List<BlockPos> toBreak = new ArrayList<>();
            for (ObstacleDoorBlockEntity be : ObstacleDoorSystem.OBSTACLES) {
                if (be.getLevel() == level && be.getCanalAsInt() == c) {
                    toBreak.add(be.getBlockPos());
                }
            }
            for (BlockPos p : toBreak) {
                level.setBlock(p, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
            }
        } catch (NumberFormatException ignored) {}
    }

    public void playGlobalSound(String soundId) {
        ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayGlobalSoundPacket(rl, 1.0f, 1.0f));
    }

    public void playSoundForPlayer(String playerUUIDStr, String soundId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            if (sound != null) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }
    
    public void playDynamicSoundForPlayer(String playerUUIDStr, String soundId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CPlayGlobalSoundPacket(rl, 1.0f, 1.0f));
        }
    }

    public void setMusicPreset(String preset) {
        WaveManager.currentSessionMusic = preset;
        level.getServer().getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + preset));
        });
    }

    public void setFog(String preset) {
        WorldConfig config = WorldConfig.get(level);
        config.setFogPreset(preset);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
            preset, config.getCustomFogR(), config.getCustomFogG(), config.getCustomFogB(),
            config.getCustomFogNear(), config.getCustomFogFar()
        ));
    }
    
    public void setCustomFog(double r, double g, double b, double near, double far) {
        WorldConfig config = WorldConfig.get(level);
        config.setFogPreset("custom");
        config.setCustomFogR((float)r);
        config.setCustomFogG((float)g);
        config.setCustomFogB((float)b);
        config.setCustomFogNear((float)near);
        config.setCustomFogFar((float)far);
        config.setDirty();
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
            "custom", (float)r, (float)g, (float)b, (float)near, (float)far
        ));
    }

    public void setConfigFlag(String key, boolean value) {
        WorldConfig config = WorldConfig.get(level);
        switch (key) {
            case "superSprintersEnabled" -> config.setSuperSprintersEnabled(value);
            case "zombiesCanSprint" -> config.setZombiesCanSprint(value);
            case "spookyAmbience" -> config.setSpookyAmbience(value);
            case "forceHalloween" -> config.setForceHalloween(value);
            case "hellhoundFireVariant" -> config.setHellhoundFireVariant(value);
            case "crawlerGasExplosion" -> config.setCrawlerGasExplosion(value);
        }
        config.setDirty();
    }

    public void setConfigString(String key, String value) {
        WorldConfig config = WorldConfig.get(level);
        switch (key) {
            case "deathPenalty" -> config.setDeathPenalty(value);
            case "dayNightMode" -> config.setDayNightMode(value);
        }
        config.setDirty();
    }

    public void forceWunderfizz(int x, int y, int z) {
        WorldConfig config = WorldConfig.get(level);
        BlockPos pos = new BlockPos(x, y, z);
        config.setActiveWunderfizzPosition(pos, level);
    }

    public void forceMysteryBox(int x, int y, int z, boolean isLocked) {
        BlockPos pos = new BlockPos(x, y, z);
        me.cryo.zombierool.MysteryBoxManager.get(level).forceLocation(level, pos, isLocked);
    }
    
    public void excludeMysteryBox(int x, int y, int z, boolean exclude) {
        WorldConfig.get(level).setMysteryBoxExcluded(new BlockPos(x, y, z), exclude);
    }
    
    public void excludeWunderfizz(int x, int y, int z, boolean exclude) {
        WorldConfig.get(level).setWunderfizzExcluded(new BlockPos(x, y, z), exclude);
    }

    public void setAmmoCratePrice(int price) {
        me.cryo.zombierool.AmmoCrateManager.get(level).setBaseCost(price);
    }

    public void resetPlayerPerkPurchases(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.setPerkPurchases(perkId, 0);
            });
        }
    }

    public void teleportPlayer(String playerUUIDStr, double x, double y, double z) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }

    public void spawnParticle(String particleId, double x, double y, double z) {
        spawnParticleAdv(particleId, x, y, z, 1, 0, 0, 0, 0);
    }

    public void spawnParticleAdv(String particleId, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        ResourceLocation rl = new ResourceLocation(particleId.contains(":") ? particleId : "minecraft:" + particleId);
        net.minecraft.core.particles.ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        if (type instanceof net.minecraft.core.particles.SimpleParticleType simple) {
            level.sendParticles(simple, x, y, z, count, dx, dy, dz, speed);
        } else if (type instanceof net.minecraft.core.particles.ParticleOptions opts) {
            level.sendParticles(opts, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public void broadcastMessage(String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    public void printConsole(String message) {
        System.out.println("[ZombieRool Lua] " + message);
    }

    public void setBlock(int x, int y, int z, String blockId) {
        ResourceLocation rl = new ResourceLocation(blockId.contains(":") ? blockId : "minecraft:" + blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            BlockPos pos = new BlockPos(x, y, z);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }
    
    public String getBlock(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        BlockState state = level.getBlockState(pos);
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return rl != null ? rl.toString() : "minecraft:air";
    }

    public void destroyBlock(int x, int y, int z, boolean dropItems) {
        BlockPos pos = new BlockPos(x, y, z);
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
        level.destroyBlock(pos, dropItems);
    }

    public void fillBlocks(int x1, int y1, int z1, int x2, int y2, int z2, String blockId) {
        ResourceLocation rl = new ResourceLocation(blockId.contains(":") ? blockId : "minecraft:" + blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            BlockState state = block.defaultBlockState();
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            
            for (int i = minX; i <= maxX; i++) {
                for (int j = minY; j <= maxY; j++) {
                    for (int k = minZ; k <= maxZ; k++) {
                        BlockPos pos = new BlockPos(i, j, k);
                        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
                        level.setBlock(pos, state, 3);
                    }
                }
            }
        }
    }

    public void registerLookTrigger(String id, double x, double y, double z, double radius, double seconds, boolean requireScope) {
        me.cryo.zombierool.core.manager.LookTriggerManager.register(id, x, y, z, radius, seconds, requireScope);
    }

    public void removeLookTrigger(String id) {
        me.cryo.zombierool.core.manager.LookTriggerManager.remove(id);
    }

    public String getPlayerName(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getName().getString() : "Unknown";
    }

    public void giveItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item, count);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    public void removeItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                int remaining = count;
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        int toRemove = Math.min(stack.getCount(), remaining);
                        stack.shrink(toRemove);
                        remaining -= toRemove;
                    }
                }
            }
        }
    }

    public boolean hasItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                int total = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        total += stack.getCount();
                    }
                }
                return total >= count;
            }
        }
        return false;
    }
    
    public LuaTable getInventory(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        LuaTable table = new LuaTable();
        if (player != null) {
            int index = 1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (rl != null) {
                        table.set(index++, LuaValue.valueOf(rl.toString()));
                    }
                }
            }
        }
        return table;
    }

    public void teleportAllPlayers(double x, double y, double z) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                player.teleportTo(x, y, z);
            }
        }
    }

    public void endGame(String message) {
        WaveManager.endGame(level, Component.literal(message));
    }

    public void spawnBonus(String bonusId, double x, double y, double z) {
        BonusManager.Bonus bonus = BonusManager.getBonus(bonusId);
        if (bonus != null) {
            BonusManager.spawnBonus(bonus, level, new net.minecraft.world.phys.Vec3(x, y, z));
        }
    }

    public void spawnBonusAtPlayer(String playerUUIDStr, String bonusId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            BonusManager.Bonus bonus = BonusManager.getBonus(bonusId);
            if (bonus != null) {
                BonusManager.spawnBonus(bonus, level, player.position());
            }
        }
    }

    public int getRandomInt(int min, int max) {
        if (min > max) return min;
        return level.random.nextInt(max - min + 1) + min;
    }

    public void applyEffect(String playerUUIDStr, String effectId, int durationTicks, int amplifier) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(effectId.contains(":") ? effectId : "minecraft:" + effectId);
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
            }
        }
    }

    public LuaTable getActivePlayers() {
        List<String> activePlayers = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .map(p -> p.getUUID().toString())
                .collect(Collectors.toList());

        LuaTable table = new LuaTable();
        int i = 1;
        for (String uuid : activePlayers) {
            table.set(i++, LuaValue.valueOf(uuid));
        }
        return table;
    }

    private ServerPlayer getPlayer(String uuidStr) {
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(uuidStr));
        } catch (Exception e) {
            return null;
        }
    }
    
    public void triggerElderGuardianOverlay(String playerUUIDStr) {
        triggerScopeScreamer(playerUUIDStr);
    }
    
    public void triggerScopeScreamer(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CTriggerScopeScreamerPacket());
        }
    }

    public void killNearbyEntities(double x, double y, double z, double radius, String entityType) {
        level.getEntitiesOfClass(Entity.class, 
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius, 
                x + radius, y + radius, z + radius))
                .stream()
                .filter(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(entityType))
                .forEach(Entity::discard);
    }

    public double getPlayerX(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getX() : 0.0;
    }

    public double getPlayerY(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getY() : 0.0;
    }

    public double getPlayerZ(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getZ() : 0.0;
    }

    public boolean isPlayerInRadius(String playerUUIDStr, double x, double y, double z, double radius) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return player.distanceToSqr(x, y, z) <= (radius * radius);
        }
        return false;
    }

    public int getZombiesInRadius(double x, double y, double z, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, 
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius),
                e -> e instanceof me.cryo.zombierool.entity.ZombieEntity || 
                     e instanceof me.cryo.zombierool.entity.HellhoundEntity || 
                     e instanceof me.cryo.zombierool.entity.CrawlerEntity)
                .size();
    }

    public void sendMessageToPlayer(String playerUUIDStr, String message) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    public void damagePlayer(String playerUUIDStr, float amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.hurt(level.damageSources().magic(), amount);
        }
    }

    public void healPlayer(String playerUUIDStr, float amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.heal(amount);
        }
    }

    public boolean hasPerk(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return me.cryo.zombierool.PerksManager.hasPerk(player, perkId);
        }
        return false;
    }

    public void refillAmmo(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof me.cryo.zombierool.api.IReloadable reloadableWeapon) {
                    reloadableWeapon.setAmmo(stack, reloadableWeapon.getMaxAmmo(stack));
                    reloadableWeapon.setReserve(stack, reloadableWeapon.getMaxReserve(stack));
                }
            }
            me.cryo.zombierool.core.system.WeaponFacade.refillAllTaczAmmo(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    public int getCurrentWave() {
        return WaveManager.getCurrentWave();
    }

    public boolean isPowerActivated() {
        return me.cryo.zombierool.GlobalSwitchState.isActivated(level);
    }

    public void killAllZombies() {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof me.cryo.zombierool.entity.ZombieEntity || 
                entity instanceof me.cryo.zombierool.entity.CrawlerEntity || 
                entity instanceof me.cryo.zombierool.entity.HellhoundEntity) {
                ((LivingEntity) entity).setHealth(0);
                ((LivingEntity) entity).die(level.damageSources().generic());
            }
        }
    }

    public void strikeLightning(double x, double y, double z) {
        net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(x, y, z);
            lightning.setVisualOnly(true); 
            level.addFreshEntity(lightning);
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 5.0f, 1.0f);
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0f, 1.0f);
        }
    }

    public void createExplosion(double x, double y, double z, float radius, float damage) {
        me.cryo.zombierool.ExplosionControl.doCustomExplosion(
            level, null, new net.minecraft.world.phys.Vec3(x, y, z),
            damage, radius, 1.0f, 0.0f, 0.0f, 1.0f, "EXPLOSION", "zombierool:explosion_old", false
        );
    }

    public void setWeather(String type) {
        if (type.equalsIgnoreCase("clear")) {
            level.setWeatherParameters(6000, 0, false, false);
        } else if (type.equalsIgnoreCase("rain")) {
            level.setWeatherParameters(0, 6000, true, false);
        } else if (type.equalsIgnoreCase("thunder")) {
            level.setWeatherParameters(0, 6000, true, true);
        }
    }

    public void spawnCrawler(double x, double y, double z) {
        me.cryo.zombierool.entity.CrawlerEntity crawler = me.cryo.zombierool.init.ZombieroolModEntities.CRAWLER.get().create(level);
        if (crawler != null) {
            crawler.setPos(x, y, z);
            crawler.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("crawler"));
            level.addFreshEntity(crawler);
        }
    }

    public void makeZombieCrawler(String uuidStr) {
        try {
            Entity e = level.getEntity(UUID.fromString(uuidStr));
            if (e instanceof me.cryo.zombierool.entity.ZombieEntity zombie) {
                zombie.makeCrawler();
            }
        } catch(Exception ignored) {}
    }

    public void spawnThrowable(String ownerUuidStr, String throwableType, double x, double y, double z, double vx, double vy, double vz, int fuseTicks) {
        try {
            ServerPlayer player = getPlayer(ownerUuidStr);
            me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableEntity proj = null;
            if (throwableType.equalsIgnoreCase("molotov")) {
                proj = new me.cryo.zombierool.item.throwable.Molotov.MolotovEntity(level, player);
            } else if (throwableType.equalsIgnoreCase("stielhandgranate")) {
                proj = new me.cryo.zombierool.item.throwable.Stielhandgranate.StielhandgranateEntity(level, player, 100 - fuseTicks);
            } else if (throwableType.equalsIgnoreCase("monkey_bomb")) {
                proj = new me.cryo.zombierool.item.throwable.MonkeyBomb.MonkeyBombEntity(level, player, 100 - fuseTicks);
            } else {
                proj = new me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity(level, player, 100 - fuseTicks);
            }
            if (proj != null) {
                proj.setPos(x, y, z);
                proj.setDeltaMovement(vx, vy, vz);
                level.addFreshEntity(proj);
            }
        } catch(Exception ignored) {}
    }

    public void giveBowieKnife(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.getPersistentData().putBoolean("zr_has_bowie_knife", true);
        }
    }

    public boolean hasBowieKnife(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return player.getPersistentData().getBoolean("zr_has_bowie_knife");
        }
        return false;
    }

    public void performMeleeAttack(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.procedures.MeleeAttackHandler.performMeleeAttack(player);
        }
    }

    public void triggerAmmoCrate(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.AmmoCrateManager.get(level).tryPurchaseAmmo(player, level, me.cryo.zombierool.WaveManager.getCurrentWave());
        }
    }
    
    public void placePerkMachine(int x, int y, int z, String facingStr, String perkId) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        
        level.setBlock(pos.below(), me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART, me.cryo.zombierool.block.system.PerksSystem.DummyPart.LOWER)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.FACING, facing), 3);
        level.setBlock(pos.above(), me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART, me.cryo.zombierool.block.system.PerksSystem.DummyPart.UPPER)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.FACING, facing), 3);
        level.setBlock(pos, me.cryo.zombierool.block.system.PerksSystem.BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock.PERK_TYPE, me.cryo.zombierool.block.system.PerksSystem.PerkType.fromString(perkId)), 3);
                
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity perkBE) {
            perkBE.setSavedPerkId(perkId);
            if (me.cryo.zombierool.PerksManager.ALL_PERKS.containsKey(perkId)) {
                perkBE.setSavedPrice(2000); 
            }
        }
    }
    
    public void placeMysteryBox(int x, int y, int z, String facingStr, boolean active) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        
        BlockPos otherPos = me.cryo.zombierool.MysteryBoxManager.getOtherPartPos(pos, facing);
        
        level.setBlock(pos, me.cryo.zombierool.block.system.MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART, false)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.ACTIVE, active), 3);
        level.setBlock(otherPos, me.cryo.zombierool.block.system.MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART, true)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.ACTIVE, active), 3);
    }
    
    public void placeWunderfizz(int x, int y, int z, String facingStr) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        
        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.DER_WUNDERFIZZ.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.DerWunderfizzBlock.FACING, facing), 3);
    }
    
    public void placePowerSwitch(int x, int y, int z, String facingStr, boolean powered) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        
        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.PowerSwitchBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED, powered), 3);
    }
    
    public void placeMeteorite(int x, int y, int z, boolean active) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, me.cryo.zombierool.core.registry.ZRBlocks.METEORITE.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MeteoriteEasterEgg.MeteoriteBlock.ACTIVE, active), 3);
    }

    public void placeAmmoCrate(int x, int y, int z, String facingStr) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;
        
        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.AMMO_CRATE.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.AmmoCrateBlock.FACING, facing), 3);
    }

    public int getPackAPunchState(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            return be.getState();
        }
        return -1;
    }

    public boolean insertPackAPunchWeapon(String playerUUIDStr, int x, int y, int z, boolean free) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            if (be.getState() == 0 && level.getBlockState(pos).getValue(me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlock.POWERED)) {
                ItemStack held = player.getMainHandItem();
                if (!held.isEmpty() && me.cryo.zombierool.core.system.WeaponFacade.isWeapon(held) && me.cryo.zombierool.core.system.WeaponFacade.canBePackAPunched(held)) {
                    if (!free) {
                        int price = be.getPrice();
                        if (me.cryo.zombierool.PointManager.getScore(player) < price) return false;
                        me.cryo.zombierool.PointManager.modifyScore(player, -price);
                    }
                    be.startUpgrading(held.copy(), player.getUUID());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    level.playSound(null, pos, net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool", "buy")), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                    level.playSound(null, pos, net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool", "pap_upgrade")), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean collectPackAPunchWeapon(String playerUUIDStr, int x, int y, int z) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            if (be.getState() == 2) {
                if (be.getOwner() == null || be.getOwner().equals(player.getUUID())) {
                    ItemStack upgraded = be.getCurrentWeapon();
                    boolean added = player.getInventory().add(upgraded);
                    if (!added) {
                        player.drop(upgraded, false);
                    }
                    be.reset();
                    player.inventoryMenu.broadcastChanges();
                    me.cryo.zombierool.util.PlayerVoiceManager.playWeaponUpgraded(player, level);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                    return true;
                }
            }
        }
        return false;
    }
}