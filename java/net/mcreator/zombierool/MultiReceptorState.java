package net.mcreator.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

public class MultiReceptorState {

    public static void setActivated(Level level, String key, boolean activated) {
        if (level instanceof ServerLevel sl) {
            MultiReceptorSavedData.get(sl).setActivated(key, activated);
        }
    }

    public static boolean isActivated(Level level, String key) {
        if (level instanceof ServerLevel sl) {
            return MultiReceptorSavedData.get(sl).isActivated(key);
        }
        return false;
    }

    public static void registerReceptor(Level level, String key, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            MultiReceptorSavedData.get(sl).registerReceptor(key, pos);
        }
    }

    public static void unregisterReceptor(Level level, String key, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            MultiReceptorSavedData.get(sl).unregisterReceptor(key, pos);
        }
    }

    public static Set<BlockPos> getReceptorPositions(Level level, String key) {
        if (level instanceof ServerLevel sl) {
            return MultiReceptorSavedData.get(sl).getReceptorPositions(key);
        }
        return Collections.emptySet();
    }
}
