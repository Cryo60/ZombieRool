package net.mcreator.zombierool;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class RepairTracker {
    private static final Map<String, Integer> repairCount = new HashMap<>();
    private static final Map<String, Long> repairTimestamps = new HashMap<>();
    private static final int MAX_REPAIRS = 6;
    private static final long RESET_TIME_MS = 2 * 60 * 1000 + 20 * 1000; // 2 minutes 20 secondes

    public static boolean tryAddRepair(Player player) {
        String uuid = player.getStringUUID();
        long now = System.currentTimeMillis();

        // Réinitialisation si le temps écoulé dépasse RESET_TIME_MS
        if (!repairTimestamps.containsKey(uuid) || now - repairTimestamps.get(uuid) > RESET_TIME_MS) {
            repairCount.put(uuid, 0);
            repairTimestamps.put(uuid, now);
        }

        int current = repairCount.getOrDefault(uuid, 0);
        // Si le maximum est atteint, on ne joue pas le son et on renvoie false
        if (current >= MAX_REPAIRS) {
            return false;
        }

        // Sinon, on ajoute une réparation et on joue le son
        repairCount.put(uuid, current + 1);

        // Jouer le son "zombierool:buy" si le max n'est pas encore atteint
        SoundEvent sound = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "buy"));
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.5f, 1.0f);

        return true;
    }
}
