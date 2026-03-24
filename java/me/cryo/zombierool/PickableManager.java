package me.cryo.zombierool.core.manager;

import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncPickablesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PickableManager {
    private static final Map<String, Set<String>> collected = new ConcurrentHashMap<>();
    private static final Map<String, Integer> totals = new ConcurrentHashMap<>();

    public static void initGame(ServerLevel level) {
        collected.clear();
        totals.clear();
        
        int meteoriteCount = WorldConfig.get(level).getMeteoritePositions().size();
        if (meteoriteCount > 0) {
            totals.put("meteorite", meteoriteCount);
        }
        
        syncAll(level);
    }

    public static void collect(ServerLevel level, String category, String id) {
        collected.computeIfAbsent(category, k -> new HashSet<>()).add(id);
        syncAll(level);
    }

    public static int getCollectedCount(String category) {
        return collected.getOrDefault(category, Collections.emptySet()).size();
    }

    public static int getTotalCount(String category) {
        return totals.getOrDefault(category, 0);
    }

    public static void reset(ServerLevel level) {
        collected.clear();
        totals.clear();
        syncAll(level);
    }

    public static void syncAll(ServerLevel level) {
        Map<String, Integer> collectedCounts = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : collected.entrySet()) {
            collectedCounts.put(entry.getKey(), entry.getValue().size());
        }
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncPickablesPacket(collectedCounts, totals));
    }
}