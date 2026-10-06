package me.cryo.zombierool.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;

public final class WorldZombieroolPaths {
    private WorldZombieroolPaths() {}

    public static File getWorldRoot(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toFile();
    }

    public static File getZombieroolDir(ServerLevel level) {
        return new File(getWorldRoot(level), "zombierool");
    }

    public static File getConfigJson(ServerLevel level) {
        return new File(getZombieroolDir(level), "config.json");
    }

    public static File ensureZombieroolDir(ServerLevel level) {
        File dir = getZombieroolDir(level);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
