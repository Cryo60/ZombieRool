package me.cryo.zombierool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.potion.PerksEffectVultureMobEffect;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.event.ServerEventHandler; // Added Import
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.PointGainPacket;

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
	    if (player.isCreative()) {
	        Scoreboard scoreboard = player.level().getScoreboard();
	        Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); // Using ServerEventHandler constant
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
	    Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); // Using ServerEventHandler constant
	    if (objective != null) {
	        Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
	        int newScore = score.getScore() + amount;
	        if (newScore < 0) {
	            amount = -score.getScore();
	        }
	        score.add(amount);
	        if (player instanceof ServerPlayer serverPlayer) {
	            NetworkHandler.INSTANCE.send(
	                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
	                new PointGainPacket(amount) 
	            );
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
	    Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); // Using ServerEventHandler constant
	    return objective != null ?
	        scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore() : 0;
	}
	
	public static void setScore(Player player, int amount) {
	    if (player.level().isClientSide()) return;
	    if (player.isCreative()) {
	        Scoreboard scoreboard = player.level().getScoreboard();
	        Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); // Using ServerEventHandler constant
	        if (objective != null) {
	            Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
	            score.setScore(Integer.MAX_VALUE);
	        }
	        return;
	    }
	    amount = Math.max(amount, 0);
	    Scoreboard scoreboard = player.level().getScoreboard();
	    Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID); // Using ServerEventHandler constant
	    if (objective != null) {
	        Score score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
	        score.setScore(amount);
	    }
	}
	
	public static boolean hasFireSale(Player player) {
	    return player.getInventory().items.stream()
	            .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);
	}
}