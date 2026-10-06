package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.ZombieroolKeys;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class MatchPlayerReset {
    private MatchPlayerReset() {}

    public static void applyStartLoadout(ServerPlayer player, WorldConfig config, int characterId) {
        player.getPersistentData().putInt("zr_character_id", characterId);
        player.getPersistentData().putBoolean("zr_match_dirty", true);
        player.getPersistentData().remove(ZombieroolKeys.HAS_BOWIE_KNIFE);
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.setPoints(500);
            cap.resetStats();
            cap.resetTotalPoints();
            cap.resetPerkPurchases();
            cap.setLethalType(config.getStartingLethal());
            cap.setLethalCount(5);
            cap.sync(player);
        });
    }

    public static void applyEndLoadout(ServerPlayer player, ServerLevel level) {
        if (player.isAlive() && (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                || me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID()))) {
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
        }
        player.removeAllEffects();
        player.getInventory().clearContent();
        player.getPersistentData().remove("zr_match_dirty");
        player.getPersistentData().remove(ZombieroolKeys.HAS_BOWIE_KNIFE);
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.resetStats();
            cap.resetPerkPurchases();
            cap.resetTotalPoints();
            cap.setLethalType(WorldConfig.get(level).getStartingLethal());
            cap.setLethalCount(5);
            cap.sync(player);
        });
        if (player.isAlive()) {
            PointManager.setScore(player, 500);
        }
    }

    public static int nextCharacterId(int current) {
        int next = current + 1;
        return next > 4 ? 1 : next;
    }
}
