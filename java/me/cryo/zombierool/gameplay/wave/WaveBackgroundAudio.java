package me.cryo.zombierool.gameplay.wave;

import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.util.ZrNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WaveBackgroundAudio {
    private static final List<ResourceLocation> SPRINT_SOUNDS;

    static {
        List<ResourceLocation> tmp = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tmp.add(new ResourceLocation("zombierool", "sprint_bg" + i));
        }
        SPRINT_SOUNDS = Collections.unmodifiableList(tmp);
    }

    private int timer;
    private int state;

    public void tick(ServerLevel level, int currentWave, boolean specialWave, int activeMobCount) {
        WorldConfig config = WorldConfig.get(level);
        if (!config.isSprintBgSoundsEnabled() || currentWave < config.getZombieSprintWave() || specialWave) {
            return;
        }

        if (activeMobCount > 3) {
            if (--timer <= 0) {
                if (state == 0) {
                    int idx = ThreadLocalRandom.current().nextInt(0, SPRINT_SOUNDS.size());
                    ZrNetwork.playGlobalSound(SPRINT_SOUNDS.get(idx));
                    if (ThreadLocalRandom.current().nextDouble() < 0.20) {
                        state = 1;
                        timer = ThreadLocalRandom.current().nextInt(4, 17);
                    } else {
                        state = 0;
                        timer = 80 + ThreadLocalRandom.current().nextInt(41);
                    }
                } else {
                    int idx = ThreadLocalRandom.current().nextInt(0, 4);
                    ZrNetwork.playGlobalSound(SPRINT_SOUNDS.get(idx));
                    state = 0;
                    timer = 80 + ThreadLocalRandom.current().nextInt(41);
                }
            }
        } else {
            timer = 10;
        }
    }
}
