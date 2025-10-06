// Dans net.mcreator.zombierool.spawner.SpawnerRegistry.java

package net.mcreator.zombierool.spawner;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.minecraft.world.level.Level; // NOUVEL IMPORT

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gère l’enregistrement des SpawnerBlockEntity par canal (int), par monde.
 */
public class SpawnerRegistry {

    // MODIFICATION ICI : Map de Level.dimension() vers une instance interne de SpawnerRegistry
    // Chaque Level aura sa propre map de spawnersByCanal
    private static final Map<Level, Map<Integer, List<BlockEntity>>> WORLD_SPAWNERS_MAP = new ConcurrentHashMap<>();

    // Méthode pour obtenir la map de spawners pour un monde donné
    private static Map<Integer, List<BlockEntity>> getSpawnersForLevel(Level level) {
        // computeIfAbsent permet de créer la map pour ce level si elle n'existe pas encore
        return WORLD_SPAWNERS_MAP.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
    }

    /** Enregistre un spawner dans le canal donné pour un monde spécifique */
    public static void registerSpawner(Level level, int canal, BlockEntity spawner) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        spawnersByCanal
            .computeIfAbsent(canal, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(spawner);
        System.out.println("[SpawnerRegistry] Registered spawner for level " + level.dimension().location() + ", canal " + canal + ", pos " + spawner.getBlockPos());
    }

    /** Désenregistre un spawner du canal donné pour un monde spécifique */
    public static void unregisterSpawner(Level level, int canal, BlockEntity spawner) { // MODIF : AJOUT DE Level level
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
        System.out.println("[SpawnerRegistry] Unregistered spawner for level " + level.dimension().location() + ", canal " + canal + ", pos " + spawner.getBlockPos());
    }

    /** Retourne la liste (copie) de TOUTES les entités de ce canal pour un monde (actives ou non) */
    public static List<BlockEntity> getAllSpawnersByCanal(Level level, int canal) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        List<BlockEntity> list = spawnersByCanal.get(canal);
        if (list == null) return Collections.emptyList();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    // Modification de clearRegistry pour qu'il vide pour un monde spécifique
    public static void clearRegistry(Level level) { // MODIF : AJOUT DE Level level
        WORLD_SPAWNERS_MAP.remove(level);
        System.out.println("[SpawnerRegistry] Cleared registry for level " + level.dimension().location());
    }

    /** Alias pour compatibilité : même chose que getAllSpawnersByCanal */
    public static List<BlockEntity> getSpawnersByCanal(Level level, int canal) { // MODIF : AJOUT DE Level level
        return getAllSpawnersByCanal(level, canal);
    }

    /** Retourne tous les spawners **actifs**, tous canaux confondus, pour un monde spécifique */
    public static List<BlockEntity> getAllSpawners(Level level) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        return spawnersByCanal.values().stream()
               .flatMap(List::stream)
               .filter(SpawnerRegistry::isSpawnerActive) // isSpawnerActive sera modifié pour prendre le Level
               .collect(Collectors.toList());
    }

    /** Vérifie s’il existe au moins un spawner de type donné dans CE canal pour un monde */
    public static boolean hasSpawnerOfTypeInCanal(Level level, int canal, Class<? extends BlockEntity> type) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        List<BlockEntity> list = spawnersByCanal.get(canal);
        if (list == null) return false;
        synchronized (list) {
            return list.stream().anyMatch(type::isInstance);
        }
    }

    /** Vérifie s’il existe au moins un spawner actif de type donné dans tous les canaux pour un monde */
    public static boolean hasActiveSpawnerOfType(Level level, Class<? extends BlockEntity> clazz) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        return spawnersByCanal.values().stream()
               .flatMap(List::stream)
               .anyMatch(be -> clazz.isInstance(be) && isSpawnerActive(be)); // isSpawnerActive sera modifié pour prendre le Level
    }

    /** Retourne l’ensemble des canaux actuellement enregistrés pour un monde */
    public static Set<Integer> getRegisteredCanals(Level level) { // MODIF : AJOUT DE Level level
        Map<Integer, List<BlockEntity>> spawnersByCanal = getSpawnersForLevel(level);
        return new HashSet<>(spawnersByCanal.keySet());
    }

    /** Accès direct à la map interne (à utiliser avec précaution) - Pour un monde spécifique */
    public static Map<Integer, List<BlockEntity>> getInternalMap(Level level) { // MODIF : AJOUT DE Level level
        return getSpawnersForLevel(level);
    }

    /** Détecte si un BlockEntity est un spawner et qu’il est activé */
    private static boolean isSpawnerActive(BlockEntity be) { // CONSERVER CELLE-CI TELLE QUELLE POUR LA RÉFÉRENCE DE STREAM
        // La méthode isSpawnerActive ne prend pas de 'Level' car elle est appelée depuis un stream de BlockEntity,
        // et le BlockEntity connaît déjà son 'Level'
        if (be instanceof SpawnerZombieBlockEntity z)   return z.isActive();
        if (be instanceof SpawnerCrawlerBlockEntity c)  return c.isActive();
        if (be instanceof SpawnerDogBlockEntity d)      return d.isActive();
        return false;
    }
}