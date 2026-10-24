package me.cryo.zombierool.core.capability;
import me.cryo.zombierool.client.career.LocalCareerManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncPlayerDataPacket;
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

        String getEquipmentType();
        void setEquipmentType(String type);
        int getEquipmentCount();
        void setEquipmentCount(int count);
        String getTacticalType();
        void setTacticalType(String type);
        int getTacticalCount();
        void setTacticalCount(int count);
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
        private String equipmentType = "none", tacticalType = "none";
        private int equipmentCount, tacticalCount;

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

        @Override public String getEquipmentType() { return equipmentType; }
        @Override public void setEquipmentType(String type) { equipmentType = type; }
        @Override public int getEquipmentCount() { return equipmentCount; }
        @Override public void setEquipmentCount(int count) { equipmentCount = Math.max(0, Math.min(2, count)); }
        @Override public String getTacticalType() { return tacticalType; }
        @Override public void setTacticalType(String type) { tacticalType = type; }
        @Override public int getTacticalCount() { return tacticalCount; }
        @Override public void setTacticalCount(int count) { tacticalCount = Math.max(0, Math.min(2, count)); }

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
            tacticalType = source.getTacticalType(); tacticalCount = source.getTacticalCount();
            equipmentType = source.getEquipmentType(); equipmentCount = source.getEquipmentCount();

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
            tag.putString("TacticalType", tacticalType); tag.putInt("TacticalCount", tacticalCount);
            tag.putString("EquipmentType", equipmentType); tag.putInt("EquipmentCount", equipmentCount);

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
            if (nbt.contains("TacticalType")) tacticalType=nbt.getString("TacticalType");
            setTacticalCount(nbt.getInt("TacticalCount"));
            if (nbt.contains("EquipmentType")) equipmentType=nbt.getString("EquipmentType");
            setEquipmentCount(nbt.getInt("EquipmentCount"));

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

            int meteoriteCount = me.cryo.zombierool.config.WorldConfig.get(level).getMeteoritePositions().size();
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
            var font=mc.font;
            int realWidth=mc.getWindow().getGuiScaledWidth(),realHeight=mc.getWindow().getGuiScaledHeight();
            float scale=Math.min(1f,realWidth/660f);graphics.pose().pushPose();graphics.pose().scale(scale,scale,1);
            int width=(int)(realWidth/scale),height=(int)(realHeight/scale),panel=Math.min(720,width-40),left=(width-panel)/2;
            var players=new java.util.ArrayList<>(mc.getConnection().getListedOnlinePlayers());
            players.sort(java.util.Comparator.<net.minecraft.client.multiplayer.PlayerInfo>comparingInt(p->-clientStats.getOrDefault(p.getProfile().getId(),new PlayerStatsManager.PlayerStats()).kills).thenComparing(p->p.getProfile().getName()));
            int challengeCount=LocalCareerManager.getData().activeChallenges.size();
            int challengeReserve=challengeCount==0?0:38+Math.min(4,(challengeCount+1)/2)*27;
            int maxRows=Math.min(2,Math.max(1,(height-150-challengeReserve)/67));
            int y=18+me.cryo.zombierool.client.BuildableClient.drawTab(graphics,width,18,38+maxRows*67,clientPickablesCollected,clientPickablesTotal);
            int nameWidth=Math.max(170,panel*42/100),statWidth=(panel-nameWidth)/6,rowHeight=22;
            int visible=Math.min(players.size(),Math.max(1,(height-y-75-challengeReserve)/rowHeight));int rows=Math.max(1,visible),tableHeight=27+rows*rowHeight;
            graphics.fill(left,y,left+panel,y+tableHeight,0xB0000000);graphics.fill(left,y,left+panel,y+24,0xC0000000);
            String map=mc.getSingleplayerServer()!=null?mc.getSingleplayerServer().getWorldData().getLevelName():mc.getCurrentServer()!=null?mc.getCurrentServer().name:"ZombieRool";
            graphics.drawCenteredString(font,font.plainSubstrByWidth(map,nameWidth-12),left+nameWidth/2,y+8,0xE2E2E2);
            String[] labels={"Score","Kills","Downs","Assist","Headshots","Ping"};
            for(int c=0;c<6;c++){
                int cx=left+nameWidth+c*statWidth;
                graphics.drawCenteredString(font,labels[c],cx+statWidth/2,y+8,0xA9A9A9);
                if(c%2==0)graphics.fill(cx,y+26,cx+statWidth-2,y+tableHeight-2,0x55306381);
            }
            for(int i=0;i<visible;i++){
                var info=players.get(i);var stats=clientStats.getOrDefault(info.getProfile().getId(),new PlayerStatsManager.PlayerStats());int ry=y+27+i*rowHeight;
                boolean self=info.getProfile().getId().equals(mc.player.getUUID());
                if(self){graphics.fill(left+2,ry,left+panel-2,ry+rowHeight,0x40377094);graphics.renderOutline(left+2,ry,panel-4,rowHeight,0xFFF08A24);}
                graphics.drawString(font,font.plainSubstrByWidth(info.getProfile().getName(),nameWidth-16),left+9,ry+7,self?0xF5CE4C:0xDCDCDC);
                String[] values={""+stats.score,""+stats.kills,""+stats.deaths,""+stats.assists,""+stats.headshots,""+info.getLatency()};
                for(int c=0;c<6;c++)graphics.drawCenteredString(font,values[c],left+nameWidth+c*statWidth+statWidth/2,ry+7,info.getGameMode()==net.minecraft.world.level.GameType.SPECTATOR?0x888888:0xE2E2E2);
            }
            int footer=y+tableHeight+9;
            if(footer+18<height){
                var career=LocalCareerManager.getData();graphics.fill(left,footer,left+panel,footer+19,0x99000000);
                graphics.drawString(font,net.minecraft.network.chat.Component.translatable("gui.zombierool.buildable.career_compact",career.currentLevel,career.currentXp),left+7,footer+5,0xBBBBBB);
                var held=mc.player.getMainHandItem();if(WeaponFacade.isWeapon(held)){
                    String id=WeaponFacade.getCareerWeaponId(held),text=held.getHoverName().getString()+" · "+LocalCareerManager.getWeaponLevel(id);
                    graphics.drawString(font,font.plainSubstrByWidth(text,panel/2-10),left+panel/2,footer+5,0x8BBAD3);
                }
            }
            me.cryo.zombierool.client.TabChallenges.draw(graphics,mc,left,footer+27,panel,height);
            graphics.pose().popPose();
        }

        private static int getPingColor(int ping) {
            if (ping < 50) return 0x55FF55;
            if (ping < 150) return 0xFFFF55;
            return 0xFF5555;
        }
    }
}
