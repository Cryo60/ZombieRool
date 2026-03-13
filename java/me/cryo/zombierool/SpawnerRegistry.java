package me.cryo.zombierool.spawner;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerRegistry {
    private static final Map<Level, List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity>> WORLD_SPAWNERS_MAP = new ConcurrentHashMap<>();

    private static List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> getSpawnersForLevel(Level level) {
        return WORLD_SPAWNERS_MAP.computeIfAbsent(level, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static void registerSpawner(Level level, UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner) {
        List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> list = getSpawnersForLevel(level);
        if (!list.contains(spawner)) {
            list.add(spawner);
        }
    }

    public static void unregisterSpawner(Level level, UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner) {
        List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> list = getSpawnersForLevel(level);
        list.remove(spawner);
    }

    public static List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> getSpawners(Level level) {
        return new ArrayList<>(getSpawnersForLevel(level));
    }

    public static void clearRegistry(Level level) {
        WORLD_SPAWNERS_MAP.remove(level);
    }

    public static boolean hasActiveSpawnerOfType(Level level, UniversalSpawnerSystem.SpawnerMobType type) {
        return getSpawners(level).stream().anyMatch(be -> be.getMobType() == type && be.isActive(level));
    }
}