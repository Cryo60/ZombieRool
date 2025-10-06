package net.mcreator.zombierool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.mcreator.zombierool.bonuses.BonusManager;
import net.mcreator.zombierool.potion.PerksEffectVultureMobEffect;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer; // NEW Import
import net.mcreator.zombierool.network.NetworkHandler; // NEW Import
import net.mcreator.zombierool.network.PointGainPacket; // NEW Import

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
        // IMPORTANT: Only process on the server side for actual score modification
        if (player.level().isClientSide()) return;

        int originalAmount = amount; 
        
        if (player.isCreative()) {
            Scoreboard scoreboard = player.level().getScoreboard();
            Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);
            if (objective != null) {
                Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
                score.setScore(Integer.MAX_VALUE);
            }
            return;
        }

        if (BonusManager.isDoublePointsActive(player) && amount > 0) {
            amount *= 2;
        }

        if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get()) && originalAmount > 0) {
            if (RANDOM.nextDouble() < 0.3) {
                amount += 10;
            }
        }

        Scoreboard scoreboard = player.level().getScoreboard();
        Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);

        if (objective != null) {
            Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);

            int newScore = score.getScore() + amount;
            if (newScore < 0) {
                amount = -score.getScore();
            }

            score.add(amount);

            // Send packet to the client if the player is a ServerPlayer (i.e., on the server)
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new PointGainPacket(amount) // Send the final 'amount' added to the score
                );
                // Ajoutez cette ligne de débogage CÔTÉ SERVEUR
                System.out.println("[SERVER] Sent PointGainPacket to " + serverPlayer.getName().getString() + " with amount: " + amount + " at server game time: " + serverPlayer.level().getGameTime());
            }
        }
    }

    public static PointGainInfo getLastPointGain(Player player) {
        return LAST_POINT_GAINS.get(player.getUUID());
    }

    public static int getScore(Player player) {
        if (player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        Scoreboard scoreboard = player.level().getScoreboard();
        Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);
        return objective != null ?
            scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore() : 0;
    }

    public static void setScore(Player player, int amount) {
        if (player.level().isClientSide()) return;

        if (player.isCreative()) {
            Scoreboard scoreboard = player.level().getScoreboard();
            Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);
            if (objective != null) {
                Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
                score.setScore(Integer.MAX_VALUE);
            }
            return;
        }

        amount = Math.max(amount, 0);

        Scoreboard scoreboard = player.level().getScoreboard();
        Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);

        if (objective != null) {
            Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
            score.setScore(amount);

            // If setting score (e.g., initial 500 points), you might want to send a packet too,
            // but for setting score, it's often not about a "gain" display.
            // If you want a "+500" display on login, you'd send a packet here too.
        }
    }

    public static boolean hasFireSale(Player player) {
        return player.getInventory().items.stream()
                .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);
    }
}