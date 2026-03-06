package me.cryo.zombierool.spawner;

import net.minecraft.world.level.block.entity.BlockEntity;
import me.cryo.zombierool.block.entity.SpawnerZombieBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerDogBlockEntity;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpawnerRegistry {
	private static final Map<Level, Map<Integer, List<BlockEntity>>> WORLD_SPAWNERS_MAP = new ConcurrentHashMap<>();
	private static Map<Integer, List<BlockEntity>> getSpawnersForLevel(Level level) {
	    return WORLD_SPAWNERS_MAP.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
	}
	
	public static void registerSpawner(Level level, int canal, BlockEntity spawner) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    spawnersByCanal
	        .computeIfAbsent(canal, k -> Collections.synchronizedList(new ArrayList<>()))
	        .add(spawner);
	    // SUPPRESSION DU LOG SYSTEMATIQUE
	}
	
	public static void unregisterSpawner(Level level, int canal, BlockEntity spawner) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    List<BlockEntity> list = spawnersByCanal.get(canal);
	    if (list != null) {
	        synchronized (list) {
	            list.remove(spawner);
	            if (list.isEmpty()) {
	                spawnersByCanal.remove(canal);
	            }
	        }
	    }
	    // SUPPRESSION DU LOG SYSTEMATIQUE
	}
	
	public static List<BlockEntity> getAllSpawnersByCanal(Level level, int canal) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    List<BlockEntity> list = spawnersByCanal.get(canal);
	    if (list == null) return Collections.emptyList();
	    synchronized (list) {
	        return new ArrayList<>(list);
	    }
	}
	
	public static void clearRegistry(Level level) { 
	    WORLD_SPAWNERS_MAP.remove(level);
	    // Log conservé ici car il n'arrive qu'une fois au déchargement du monde
	    System.out.println("[SpawnerRegistry] Cleared registry for level " + level.dimension().location());
	}
	
	public static List<BlockEntity> getSpawnersByCanal(Level level, int canal) { 
	    return getAllSpawnersByCanal(level, canal);
	}
	
	public static List<BlockEntity> getAllSpawners(Level level) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    return spawnersByCanal.values().stream()
	           .flatMap(List::stream)
	           .filter(SpawnerRegistry::isSpawnerActive) 
	           .collect(Collectors.toList());
	}
	
	public static boolean hasSpawnerOfTypeInCanal(Level level, int canal, Class<? extends BlockEntity> type) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    List<BlockEntity> list = spawnersByCanal.get(canal);
	    if (list == null) return false;
	    synchronized (list) {
	        return list.stream().anyMatch(type::isInstance);
	    }
	}
	
	public static boolean hasActiveSpawnerOfType(Level level, Class<? extends BlockEntity> clazz) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    return spawnersByCanal.values().stream()
	           .flatMap(List::stream)
	           .anyMatch(be -> clazz.isInstance(be) && isSpawnerActive(be)); 
	}
	
	public static Set<Integer> getRegisteredCanals(Level level) { 
	    Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
	    return new HashSet<>(spawnersByCanal.keySet());
	}
	
	public static Map<Integer, List<BlockEntity>> getInternalMap(Level level) { 
	    return getSpawnersForLevel(level);
	}
	
	private static boolean isSpawnerActive(BlockEntity be) { 
	    if (be instanceof SpawnerZombieBlockEntity z)   return z.isActive();
	    if (be instanceof SpawnerCrawlerBlockEntity c)  return c.isActive();
	    if (be instanceof SpawnerDogBlockEntity d)      return d.isActive();
	    return false;
	}
}