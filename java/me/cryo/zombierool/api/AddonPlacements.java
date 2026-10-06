package me.cryo.zombierool.api;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.util.WorldZombieroolPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lets an addon drop machines into a map from a text file, without opening the map.
 * ZombieRool does not ship Gobblegum. The addon registers the type, then writes lines like:
 * GobblegumMachine 10 64 -20 north on price=500 channel=1
 */
public final class AddonPlacements {
    private static final int MAX_LINES = 2000;
    private static final int MAX_COORD = 30_000_000;
    private static final Map<String, Handler> HANDLERS = new HashMap<>();

    private AddonPlacements() {}

    public interface Handler {
        /**
         * @return true when the placement was applied and should not run again for this line
         */
        boolean place(ServerLevel level, BlockPos pos, Direction facing, boolean enabled, Map<String, String> params);
    }

    public static void register(String type, Handler handler) {
        if (type == null || handler == null) return;
        HANDLERS.put(type.toLowerCase(Locale.ROOT), handler);
    }

    public static void apply(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        List<String> lines = readLines(level);
        if (lines.isEmpty()) return;
        File marker = new File(WorldZombieroolPaths.ensureZombieroolDir(level), "addon_placements.txt");
        Set<String> done = readMarker(marker);
        Set<String> unknown = new HashSet<>();
        String worldName = WorldZombieroolPaths.getWorldRoot(level).getName();
        boolean changed = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Placement placement = parse(line);
            if (placement == null) {
                ZombieroolMod.LOGGER.warn("[ZombieRool] Placement ignoré : {}", line);
                continue;
            }
            String onlyMap = placement.params.get("map");
            if (onlyMap != null && !onlyMap.equalsIgnoreCase(worldName)) continue;
            String hash = sha256(line);
            if (done.contains(hash)) continue;
            Handler handler = HANDLERS.get(placement.type.toLowerCase(Locale.ROOT));
            if (handler == null) {
                if (unknown.add(placement.type)) {
                    ZombieroolMod.LOGGER.info("[ZombieRool] Aucun addon n'enregistre le placement '{}'.", placement.type);
                }
                continue;
            }
            try {
                if (handler.place(level, placement.pos, placement.facing, placement.enabled, placement.params)) {
                    done.add(hash);
                    changed = true;
                }
            } catch (Exception e) {
                ZombieroolMod.LOGGER.error("[ZombieRool] Placement '{}' a échoué", placement.type, e);
            }
        }
        if (changed) writeMarker(marker, done);
    }

    private static List<String> readLines(ServerLevel level) {
        List<String> lines = new ArrayList<>();
        collectFile(new File(FMLPaths.CONFIGDIR.get().toFile(), "zombierool/placements"), lines);
        collectFile(new File(WorldZombieroolPaths.getZombieroolDir(level), "placements"), lines);
        MinecraftServer server = level.getServer();
        ResourceManager resources = server.getResourceManager();
        try {
            Map<net.minecraft.resources.ResourceLocation, Resource> found = resources.listResources(
                    "zombierool_placements", location -> location.getPath().endsWith(".txt"));
            for (Resource resource : found.values()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && lines.size() < MAX_LINES) lines.add(line);
                }
            }
        } catch (Exception e) {
            ZombieroolMod.LOGGER.warn("[ZombieRool] Placements d'addon illisibles : {}", e.getMessage());
        }
        return lines;
    }

    private static void collectFile(File dir, List<String> lines) {
        File[] files = dir.listFiles((folder, name) -> name.endsWith(".txt"));
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".txt")) continue;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (lines.size() >= MAX_LINES) return;
                    lines.add(line);
                }
            } catch (IOException e) {
                ZombieroolMod.LOGGER.warn("[ZombieRool] Fichier de placement illisible : {}", file.getName());
            }
        }
    }

    private static Placement parse(String line) {
        if (line.length() > 500) return null;
        String[] parts = line.split("\\s+");
        if (parts.length < 4 || !parts[0].matches("[A-Za-z][A-Za-z0-9_]*")) return null;
        Integer x = integer(parts[1]);
        Integer y = integer(parts[2]);
        Integer z = integer(parts[3]);
        if (x == null || y == null || z == null) return null;
        if (Math.abs(x) > MAX_COORD || Math.abs(z) > MAX_COORD || y < -64 || y > 320) return null;
        Direction facing = Direction.NORTH;
        boolean enabled = true;
        boolean facingSet = false;
        boolean enabledSet = false;
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 4; i < parts.length; i++) {
            String token = parts[i];
            int eq = token.indexOf('=');
            if (eq > 0) {
                String key = token.substring(0, eq);
                String value = token.substring(eq + 1);
                if (!key.matches("[A-Za-z][A-Za-z0-9_]*") || value.length() > 200) return null;
                params.put(key, value);
                continue;
            }
            Direction asFacing = facing(token);
            if (asFacing != null && !facingSet) {
                facing = asFacing;
                facingSet = true;
                continue;
            }
            if (!enabledSet && (token.equalsIgnoreCase("on") || token.equalsIgnoreCase("off")
                    || token.equalsIgnoreCase("true") || token.equalsIgnoreCase("false"))) {
                enabled = token.equalsIgnoreCase("on") || token.equalsIgnoreCase("true");
                enabledSet = true;
                continue;
            }
            return null;
        }
        params.put("facing", facing.getName());
        params.put("enabled", enabled ? "on" : "off");
        return new Placement(parts[0], new BlockPos(x, y, z), facing, enabled, params);
    }

    private static Direction facing(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    private static Integer integer(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Set<String> readMarker(File marker) {
        Set<String> done = new HashSet<>();
        if (!marker.isFile()) return done;
        try {
            done.addAll(Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
        return done;
    }

    private static void writeMarker(File marker, Set<String> done) {
        try {
            Files.write(marker.toPath(), done, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ZombieroolMod.LOGGER.warn("[ZombieRool] Marqueur de placements non écrit : {}", e.getMessage());
        }
    }

    private static String sha256(String line) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(line.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(line.hashCode());
        }
    }

    private static final class Placement {
        final String type;
        final BlockPos pos;
        final Direction facing;
        final boolean enabled;
        final Map<String, String> params;

        Placement(String type, BlockPos pos, Direction facing, boolean enabled, Map<String, String> params) {
            this.type = type;
            this.pos = pos;
            this.facing = facing;
            this.enabled = enabled;
            this.params = params;
        }
    }
}
