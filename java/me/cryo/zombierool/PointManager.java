package me.cryo.zombierool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.event.ServerEventHandler;

import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CPointGainPacket;
import net.minecraftforge.network.PacketDistributor;

public class PointManager {
    private static final Random RANDOM = new Random();
    public static final Map<java.util.UUID, PointGainInfo> LAST_POINT_GAINS = new ConcurrentHashMap<>();

    public static class PointGainInfo {
        public final int amount;
        public final long timestamp;

        public PointGainInfo(int amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    public static void modifyScore(Player player, int amount) {
        if (player.level().isClientSide()) return;
        int originalAmount = amount; 

        if (player.isCreative()) return;

        if (BonusManager.isDoublePointsActive(player) && amount > 0) {
            amount *= 2;
        }

        if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get()) && originalAmount > 0) {
            if (RANDOM.nextDouble() < 0.3) {
                amount += 10;
            }
        }

        final int finalAmount = amount;

        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.addPoints(finalAmount);
            if (finalAmount > 0) {
                cap.addTotalPoints(finalAmount);
            }
            int finalScore = cap.getPoints();

            Scoreboard scoreboard = player.level().getScoreboard();
            Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); 
            if (objective == null) {
                objective = scoreboard.addObjective(
                    ServerEventHandler.OBJECTIVE_ID,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Points"),
                    ObjectiveCriteria.RenderType.INTEGER
                );
                scoreboard.setDisplayObjective(1, objective);
            }

            Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
            score.setScore(finalScore);

            if (player instanceof ServerPlayer serverPlayer) {
                cap.sync(serverPlayer);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new S2CPointGainPacket(finalAmount));
                PlayerStatsManager.syncAll(serverPlayer.serverLevel());
            }
        });
    }

    public static PointGainInfo getLastPointGain(Player player) {
        return LAST_POINT_GAINS.get(player.getUUID());
    }

    public static int getScore(Player player) {
        if (player.isCreative()) return Integer.MAX_VALUE;
        return player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA)
            .map(ZombieCapabilitySystem.IData::getPoints)
            .orElse(0);
    }

    public static void setScore(Player player, int amount) {
        if (player.level().isClientSide()) return;
        if (player.isCreative()) return;

        int finalAmount = Math.max(amount, 0);
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.setPoints(finalAmount);
            int finalScore = cap.getPoints();

            Scoreboard scoreboard = player.level().getScoreboard();
            Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); 
            if (objective == null) {
                objective = scoreboard.addObjective(
                    ServerEventHandler.OBJECTIVE_ID,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("Points"),
                    ObjectiveCriteria.RenderType.INTEGER
                );
                scoreboard.setDisplayObjective(1, objective);
            }

            Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
            score.setScore(finalScore);

            if (player instanceof ServerPlayer sp) {
                cap.sync(sp);
                PlayerStatsManager.syncAll(sp.serverLevel());
            }
        });
    }

    public static boolean hasFireSale(Player player) {
        return player.getInventory().items.stream()
                .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);
    }
}