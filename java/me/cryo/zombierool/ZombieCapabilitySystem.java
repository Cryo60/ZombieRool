package me.cryo.zombierool.core.capability;

import me.cryo.zombierool.client.career.LocalCareerManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.core.network.S2CSyncPlayerDataPacket;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZombieCapabilitySystem {

    public interface IData extends INBTSerializable<CompoundTag> {
        int getPoints();
        void setPoints(int amount);
        void addPoints(int amount);

        int getTotalPoints();
        void addTotalPoints(int amount);
        void resetTotalPoints(); 

        int getKills();
        void addKill();

        int getHeadshots();
        void addHeadshot();

        int getAssists();
        void addAssist();

        int getDowns();
        void addDown();

        int getPerkPurchases(String perkId);
        void incrementPerkPurchases(String perkId);
        void setPerkPurchases(String perkId, int count); 

        void resetStats();
        void resetPerkPurchases();

        String getLethalType();
        void setLethalType(String type);

        int getLethalCount();
        void setLethalCount(int count);

        void copyFrom(IData source);
        void sync(ServerPlayer player);
    }

    public static class Impl implements IData {
        private int points = 500;
        private int totalPoints = 0;
        private int kills = 0;
        private int headshots = 0;
        private int assists = 0;
        private int downs = 0;

        private final Map<String, Integer> perkPurchases = new HashMap<>();

        private String lethalType = "zombierool:grenade";
        private int lethalCount = 5;

        @Override public int getPoints() { return points; }
        @Override public void setPoints(int amount) { this.points = Math.max(0, amount); }
        @Override public void addPoints(int amount) { this.points = Math.max(0, this.points + amount); }

        @Override public int getTotalPoints() { return totalPoints; }
        @Override public void addTotalPoints(int amount) { this.totalPoints += amount; }
        @Override public void resetTotalPoints() { this.totalPoints = 0; } 

        @Override public int getKills() { return kills; }
        @Override public void addKill() { this.kills++; }

        @Override public int getHeadshots() { return headshots; }
        @Override public void addHeadshot() { this.headshots++; }

        @Override public int getAssists() { return assists; }
        @Override public void addAssist() { this.assists++; }

        @Override public int getDowns() { return downs; }
        @Override public void addDown() { this.downs++; }

        @Override public int getPerkPurchases(String perkId) { return perkPurchases.getOrDefault(perkId, 0); }
        @Override public void incrementPerkPurchases(String perkId) { perkPurchases.put(perkId, getPerkPurchases(perkId) + 1); }
        @Override public void setPerkPurchases(String perkId, int count) { perkPurchases.put(perkId, count); }

        @Override public String getLethalType() { return lethalType; }
        @Override public void setLethalType(String type) { this.lethalType = type; }

        @Override public int getLethalCount() { return lethalCount; }
        @Override public void setLethalCount(int count) { this.lethalCount = Math.max(0, Math.min(5, count)); }

        @Override public void resetStats() { 
            this.kills = 0; 
            this.assists = 0; 
            this.downs = 0; 
            this.headshots = 0;
        }
        @Override public void resetPerkPurchases() { perkPurchases.clear(); }

        @Override public void copyFrom(IData source) {
            this.points = source.getPoints();
            this.totalPoints = source.getTotalPoints();
            this.kills = source.getKills();
            this.headshots = source.getHeadshots();
            this.assists = source.getAssists();
            this.downs = source.getDowns();
            this.lethalType = source.getLethalType();
            this.lethalCount = source.getLethalCount();

            this.perkPurchases.clear();
            if (source instanceof Impl implSource) {
                this.perkPurchases.putAll(implSource.perkPurchases);
            }
        }

        @Override public void sync(ServerPlayer player) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncPlayerDataPacket(serializeNBT()));
        }

        @Override public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Points", points);
            tag.putInt("TotalPoints", totalPoints);
            tag.putInt("Kills", kills);
            tag.putInt("Headshots", headshots);
            tag.putInt("Assists", assists);
            tag.putInt("Downs", downs);

            tag.putString("LethalType", lethalType);
            tag.putInt("LethalCount", lethalCount);

            CompoundTag perksTag = new CompoundTag();
            for (Map.Entry<String, Integer> entry : perkPurchases.entrySet()) {
                perksTag.putInt(entry.getKey(), entry.getValue());
            }
            tag.put("PerkPurchases", perksTag);

            return tag;
        }

        @Override public void deserializeNBT(CompoundTag nbt) {
            if (nbt.contains("Points")) this.points = nbt.getInt("Points");
            if (nbt.contains("TotalPoints")) this.totalPoints = nbt.getInt("TotalPoints");
            if (nbt.contains("Kills")) this.kills = nbt.getInt("Kills");
            if (nbt.contains("Headshots")) this.headshots = nbt.getInt("Headshots");
            if (nbt.contains("Assists")) this.assists = nbt.getInt("Assists");
            if (nbt.contains("Downs")) this.downs = nbt.getInt("Downs");

            if (nbt.contains("LethalType")) this.lethalType = nbt.getString("LethalType");
            if (nbt.contains("LethalCount")) this.lethalCount = nbt.getInt("LethalCount");

            if (nbt.contains("PerkPurchases")) {
                this.perkPurchases.clear();
                CompoundTag perksTag = nbt.getCompound("PerkPurchases");
                for (String key : perksTag.getAllKeys()) {
                    this.perkPurchases.put(key, perksTag.getInt(key));
                }
            }
        }
    }

    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        public static final Capability<IData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>(){});

        private IData backend = null;
        private final LazyOptional<IData> optional = LazyOptional.of(this::getOrCreateBackend);

        private IData getOrCreateBackend() {
            if (this.backend == null) this.backend = new Impl();
            return this.backend;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == PLAYER_DATA) return optional.cast();
            return LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { 
            return this.getOrCreateBackend().serializeNBT(); 
        }

        @Override public void deserializeNBT(CompoundTag nbt) { 
            this.getOrCreateBackend().deserializeNBT(nbt); 
        }
    }

    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class CapabilityRegister {
        @SubscribeEvent
        public static void registerCaps(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
            event.register(IData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CapabilityEventHandler {

        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(new ResourceLocation("zombierool", "player_data"), new Provider());
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            Player original = event.getOriginal();
            Player clone = event.getEntity();
            original.getCapability(Provider.PLAYER_DATA).ifPresent(oldCap -> {
                clone.getCapability(Provider.PLAYER_DATA).ifPresent(newCap -> {
                    newCap.copyFrom(oldCap);
                });
            });
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                player.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> cap.sync(player));
                PlayerStatsManager.syncAll(player.serverLevel());
                me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnPlayerRespawn", player.getUUID().toString());
            }
        }

        @SubscribeEvent
        public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                player.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> cap.sync(player));
                PlayerStatsManager.syncAll(player.serverLevel());
            }
        }
    }

    public static class PlayerStatsManager {
        public static class PlayerStats {
            public int score = 0;
            public int totalPoints = 0;
            public int kills = 0;
            public int headshots = 0;
            public int assists = 0;
            public int deaths = 0;
        }

        private static final Map<Integer, Set<UUID>> recentDamagers = new ConcurrentHashMap<>();

        public static void recordDamage(net.minecraft.world.entity.LivingEntity target, ServerPlayer attacker) {
            recentDamagers.computeIfAbsent(target.getId(), k -> new HashSet<>()).add(attacker.getUUID());
        }

        public static void recordKill(net.minecraft.world.entity.LivingEntity target, ServerPlayer killer) {
            killer.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.addKill();
                cap.sync(killer);
            });

            Set<UUID> damagers = recentDamagers.remove(target.getId());
            if (damagers != null) {
                for (UUID damagerId : damagers) {
                    if (!damagerId.equals(killer.getUUID())) {
                        ServerPlayer damager = killer.server.getPlayerList().getPlayer(damagerId);
                        if (damager != null) {
                            damager.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> {
                                cap.addAssist();
                                cap.sync(damager);
                            });
                        }
                    }
                }
            }
            syncAll(killer.serverLevel());
        }

        public static void recordHeadshot(ServerPlayer killer) {
            killer.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.addHeadshot();
                cap.sync(killer);
            });
            syncAll(killer.serverLevel());
        }

        public static void recordDeath(ServerPlayer player) {
            player.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.addDown();
                cap.sync(player);
            });
            syncAll(player.serverLevel());
        }

        public static void cleanupEntity(int entityId) {
            recentDamagers.remove(entityId);
        }

        public static void reset(net.minecraft.server.level.ServerLevel level) {
            recentDamagers.clear();
            syncAll(level);
        }

        public static void syncAll(net.minecraft.server.level.ServerLevel level) {
            Map<UUID, PlayerStats> allStats = new HashMap<>();
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.getCapability(Provider.PLAYER_DATA).ifPresent(cap -> {
                    PlayerStats s = new PlayerStats();
                    s.score = cap.getPoints();
                    s.totalPoints = cap.getTotalPoints();
                    s.kills = cap.getKills();
                    s.headshots = cap.getHeadshots();
                    s.assists = cap.getAssists();
                    s.deaths = cap.getDowns();
                    allStats.put(p.getUUID(), s);
                });
            }
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new me.cryo.zombierool.network.packet.S2CSyncPlayerStatsPacket(allStats));
        }
    }

    public static class PickableManager {
        private static final Map<String, Set<String>> collected = new ConcurrentHashMap<>();
        private static final Map<String, Integer> totals = new ConcurrentHashMap<>();

        public static void initGame(net.minecraft.server.level.ServerLevel level) {
            collected.clear();
            totals.clear();

            int meteoriteCount = me.cryo.zombierool.WorldConfig.get(level).getMeteoritePositions().size();
            if (meteoriteCount > 0) {
                totals.put("meteorite", meteoriteCount);
            }
            syncAll(level);
        }

        public static void collect(net.minecraft.server.level.ServerLevel level, String category, String id) {
            collected.computeIfAbsent(category, k -> new HashSet<>()).add(id);
            syncAll(level);
        }

        public static int getCollectedCount(String category) {
            return collected.getOrDefault(category, java.util.Collections.emptySet()).size();
        }

        public static int getTotalCount(String category) {
            return totals.getOrDefault(category, 0);
        }

        public static void reset(net.minecraft.server.level.ServerLevel level) {
            collected.clear();
            totals.clear();
            syncAll(level);
        }

        public static void syncAll(net.minecraft.server.level.ServerLevel level) {
            Map<String, Integer> collectedCounts = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : collected.entrySet()) {
                collectedCounts.put(entry.getKey(), entry.getValue().size());
            }
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new me.cryo.zombierool.network.packet.S2CSyncPickablesPacket(collectedCounts, totals));
        }
    }

    @Mod.EventBusSubscriber(modid = "zombierool", value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientTabListRenderer {
        public static Map<UUID, PlayerStatsManager.PlayerStats> clientStats = new HashMap<>();
        private static Map<String, Integer> clientPickablesCollected = new HashMap<>();
        private static Map<String, Integer> clientPickablesTotal = new HashMap<>();

        public static void updateStats(Map<UUID, PlayerStatsManager.PlayerStats> stats) {
            clientStats = stats;
        }

        public static void updatePickables(Map<String, Integer> collected, Map<String, Integer> totals) {
            clientPickablesCollected = collected;
            clientPickablesTotal = totals;
        }

        @SubscribeEvent
        public static void onRenderTabList(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
            if (event.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_LIST.type()) {
                event.setCanceled(true); 
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.options.keyPlayerList.isDown() && mc.level != null) {
                    drawCustomTabList(event.getGuiGraphics(), mc);
                }
            }
        }

        private static void drawCustomTabList(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc) {
            net.minecraft.client.gui.Font font = mc.font;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            
            java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> players = mc.getConnection().getListedOnlinePlayers();
            java.util.List<net.minecraft.client.multiplayer.PlayerInfo> sortedPlayers = new java.util.ArrayList<>(players);
            
            sortedPlayers.sort((p1, p2) -> {
                PlayerStatsManager.PlayerStats s1 = clientStats.getOrDefault(p1.getProfile().getId(), new PlayerStatsManager.PlayerStats());
                PlayerStatsManager.PlayerStats s2 = clientStats.getOrDefault(p2.getProfile().getId(), new PlayerStatsManager.PlayerStats());
                if (s1.kills != s2.kills) return Integer.compare(s2.kills, s1.kills);
                if (s1.score != s2.score) return Integer.compare(s2.score, s1.score);
                return p1.getProfile().getName().compareToIgnoreCase(p2.getProfile().getName());
            });

            int colNameW = 75;
            int colTotalW = 40;
            int colScoreW = 40;
            int colHeadshotsW = 55;
            int colKillsW = 30;
            int colDownsW = 35;
            int colAssistsW = 40;
            int colPingW = 30;

            int totalWidth = colNameW + colTotalW + colScoreW + colHeadshotsW + colKillsW + colDownsW + colAssistsW + colPingW + 20;
            int startX = (screenWidth - totalWidth) / 2;
            int startY = 15;
            int rowHeight = 12;

            int tableHeight = (sortedPlayers.size() + 1) * rowHeight + 5;

            graphics.fill(startX, startY, startX + totalWidth, startY + tableHeight, 0xAA000000);
            graphics.renderOutline(startX, startY, totalWidth, tableHeight, 0xFF555555);

            int currentY = startY + 4;
            int xName = startX + 5;
            int xTotal = xName + colNameW;
            int xScore = xTotal + colTotalW;
            int xHeadshots = xScore + colScoreW;
            int xKills = xHeadshots + colHeadshotsW;
            int xDowns = xKills + colKillsW;
            int xAssists = xDowns + colDownsW;
            int xPing = xAssists + colAssistsW;

            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.zombierool.tab.player").getString(), xName, currentY, 0xFFAA00);
            graphics.drawString(font, "Total", xTotal, currentY, 0xFFAA00);
            graphics.drawString(font, "Score", xScore, currentY, 0xFFAA00);
            graphics.drawString(font, "Headshot", xHeadshots, currentY, 0xFFAA00);
            graphics.drawString(font, "Kills", xKills, currentY, 0xFFAA00);
            graphics.drawString(font, "Downs", xDowns, currentY, 0xFFAA00);
            graphics.drawString(font, "Assist", xAssists, currentY, 0xFFAA00);
            graphics.drawString(font, "Ping", xPing, currentY, 0xFFAA00);

            currentY += rowHeight;

            for (net.minecraft.client.multiplayer.PlayerInfo pInfo : sortedPlayers) {
                String name = pInfo.getProfile().getName();
                PlayerStatsManager.PlayerStats pStats = clientStats.getOrDefault(pInfo.getProfile().getId(), new PlayerStatsManager.PlayerStats());

                int color = (pInfo.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR) ? 0xAAAAAA : 0xFFFFFF;

                graphics.drawString(font, name, xName, currentY, color);
                graphics.drawString(font, String.valueOf(pStats.totalPoints), xTotal, currentY, 0xFFFFFF);
                graphics.drawString(font, String.valueOf(pStats.score), xScore, currentY, 0x55FF55);
                graphics.drawString(font, String.valueOf(pStats.headshots), xHeadshots, currentY, 0xAAAAAA);
                graphics.drawString(font, String.valueOf(pStats.kills), xKills, currentY, 0xFFFFFF);
                graphics.drawString(font, String.valueOf(pStats.deaths), xDowns, currentY, 0xFF5555);
                graphics.drawString(font, String.valueOf(pStats.assists), xAssists, currentY, 0xAAAAAA);
                graphics.drawString(font, pInfo.getLatency() + "ms", xPing, currentY, getPingColor(pInfo.getLatency()));

                currentY += rowHeight;
            }

            int lastBoxBottomY = startY + tableHeight + 10;
            
            int activePickablesCount = (int) clientPickablesTotal.values().stream().filter(v -> v > 0).count();
            if (activePickablesCount > 0) {
                int pickBoxHeight = 20 + (activePickablesCount * 20);
                int pickBoxWidth = 180;
                int pickBoxX = (screenWidth - pickBoxWidth) / 2;

                graphics.fill(pickBoxX, lastBoxBottomY, pickBoxX + pickBoxWidth, lastBoxBottomY + pickBoxHeight, 0xAA000000);
                graphics.renderOutline(pickBoxX, lastBoxBottomY, pickBoxWidth, pickBoxHeight, 0xFF555555);

                graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.zombierool.tab.map_inventory").getString(), screenWidth / 2, lastBoxBottomY + 5, 0xFFAA00);

                int pY = lastBoxBottomY + 18;
                for (Map.Entry<String, Integer> entry : clientPickablesTotal.entrySet()) {
                    if (entry.getValue() <= 0) continue;

                    String category = entry.getKey();
                    int total = entry.getValue();
                    int collected = clientPickablesCollected.getOrDefault(category, 0);

                    net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
                    String displayName = category;

                    if (category.equals("meteorite")) {
                        displayName = net.minecraft.network.chat.Component.translatable("gui.zombierool.tab.meteorites").getString();
                        icon = new net.minecraft.world.item.ItemStack(me.cryo.zombierool.core.registry.ZRBlocks.METEORITE.get());
                    }

                    if (!icon.isEmpty()) {
                        graphics.renderItem(icon, pickBoxX + 10, pY);
                    }

                    String countText = collected + " / " + total;
                    int textColor = (collected >= total) ? 0x55FF55 : 0xFFFFFF;

                    graphics.drawString(font, displayName + ": " + countText, pickBoxX + 35, pY + 4, textColor);
                    pY += 20;
                }
                
                lastBoxBottomY += pickBoxHeight + 10;
            }

            int careerBoxWidth = 260;
            int careerBoxX = (screenWidth - careerBoxWidth) / 2;
            int careerBoxHeight = 120; 

            graphics.fill(careerBoxX, lastBoxBottomY, careerBoxX + careerBoxWidth, lastBoxBottomY + careerBoxHeight, 0xAA000000);
            graphics.renderOutline(careerBoxX, lastBoxBottomY, careerBoxWidth, careerBoxHeight, 0xFF555555);

            graphics.drawCenteredString(font, "--- Career Progression ---", screenWidth / 2, lastBoxBottomY + 5, 0xFFAA00);

            LocalCareerManager.CareerData data = LocalCareerManager.getData();
            int pY = lastBoxBottomY + 20;

            int playerLevel = data.currentLevel;
            int currentXP = data.currentXp;
            int nextXP = LocalCareerManager.getXpRequiredForLevel(playerLevel);

            graphics.drawString(font, "Player Level: " + playerLevel, careerBoxX + 10, pY, 0xFFFFFF);

            if (playerLevel < 50) {
                int barWidth = 100;
                graphics.fill(careerBoxX + 100, pY, careerBoxX + 100 + barWidth, pY + 6, 0xFF333333);
                float progress = (float) currentXP / nextXP;
                graphics.fill(careerBoxX + 100, pY, careerBoxX + 100 + (int)(barWidth * progress), pY + 6, 0xFF00FF00);
                graphics.drawString(font, currentXP + "/" + nextXP + " XP", careerBoxX + 100 + barWidth + 5, pY, 0xAAAAAA);
            } else {
                graphics.drawString(font, "MAX LEVEL", careerBoxX + 100, pY, 0xFFFF00);
            }
            pY += 15;

            ItemStack heldWeapon = mc.player.getMainHandItem();
            if (WeaponFacade.isWeapon(heldWeapon)) {
                String weaponId = WeaponFacade.getWeaponId(heldWeapon).replace("zombierool:", "");
                int wLevel = LocalCareerManager.getWeaponLevel(weaponId);
                int wCurrentXP = LocalCareerManager.getWeaponXpInCurrentLevel(weaponId);
                int wNextXP = LocalCareerManager.getWeaponXpForNextLevel(weaponId);

                String wpnName = heldWeapon.getHoverName().getString();
                if (wpnName.length() > 10) wpnName = wpnName.substring(0, 10) + "..";

                graphics.drawString(font, wpnName + " Lvl: " + wLevel, careerBoxX + 10, pY, 0x00FFFF);

                if (wLevel < 100) {
                    int barWidth = 100;
                    graphics.fill(careerBoxX + 100, pY, careerBoxX + 100 + barWidth, pY + 6, 0xFF333333);
                    float progress = (float) wCurrentXP / wNextXP;
                    graphics.fill(careerBoxX + 100, pY, careerBoxX + 100 + (int)(barWidth * progress), pY + 6, 0xFF00FFFF);
                    graphics.drawString(font, wCurrentXP + "/" + wNextXP + " XP", careerBoxX + 100 + barWidth + 5, pY, 0xAAAAAA);
                } else {
                    graphics.drawString(font, "MAX LEVEL", careerBoxX + 100, pY, 0xFFFF00);
                }
            } else {
                graphics.drawString(font, "Hold a weapon to see progression", careerBoxX + 10, pY, 0xAAAAAA);
            }
            pY += 20;

            graphics.drawString(font, "Active Challenges:", careerBoxX + 10, pY, 0xFFAA00);
            pY += 10;
            for (Map.Entry<String, LocalCareerManager.ChallengeDef> entry : data.activeChallenges.entrySet()) {
                String id = entry.getKey();
                LocalCareerManager.ChallengeDef def = entry.getValue();
                int prog = data.challengeProgress.getOrDefault(id, 0);
                boolean done = data.challengeCompleted.getOrDefault(id, false);
                
                String typeStr = def.type.name().toLowerCase();
                net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.translatable("gui.zombierool.career.challenge." + typeStr, def.target);
                
                graphics.drawString(font, "- " + text.getString(), careerBoxX + 10, pY, 0xFFFFFF);
                if (done) {
                    graphics.drawString(font, "DONE", careerBoxX + 210, pY, 0x00FF00);
                } else {
                    graphics.drawString(font, prog + "/" + def.target, careerBoxX + 210, pY, 0xAAAAAA);
                }
                pY += 12;
            }
        }

        private static int getPingColor(int ping) {
            if (ping < 50) return 0x55FF55;
            if (ping < 150) return 0xFFFF55;
            return 0xFF5555;
        }
    }
}