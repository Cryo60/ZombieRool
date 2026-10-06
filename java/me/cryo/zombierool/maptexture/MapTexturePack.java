package me.cryo.zombierool.maptexture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.CRC32;

/** Writes the resource pack that shows map-authored block textures. */
public final class MapTexturePack {
    private static final byte[] PLACEHOLDER = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private MapTexturePack() {}

    /** @return true when the pack contents changed and the client should reload */
    public static boolean install(Path worldRoot, Path packDir) throws IOException {
        String stamp = sourceStamp(worldRoot);
        Path stampFile = packDir.resolve("stamp.txt");
        boolean same = Files.exists(stampFile) && Files.exists(packDir.resolve("pack.mcmeta"))
                && stamp.equals(Files.readString(stampFile, StandardCharsets.UTF_8));
        if (same) return false;
        write(worldRoot, packDir);
        Files.writeString(stampFile, stamp, StandardCharsets.UTF_8);
        return true;
    }

    public static String sourceStamp(Path worldRoot) throws IOException {
        Path dir = worldRoot.resolve("zombierool").resolve("custom_blocks");
        StringBuilder builder = new StringBuilder("pane2crc;");
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(path -> {
                    return path.getFileName().toString().endsWith(".png");
                }).sorted().forEach(path -> {
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        CRC32 crc = new CRC32();
                        crc.update(bytes);
                        builder.append(path.getFileName()).append(':').append(bytes.length).append(':')
                                .append(crc.getValue()).append(';');
                    } catch (IOException ignored) {}
                });
            }
        }
        return builder.toString();
    }

    private static void write(Path worldRoot, Path packDir) throws IOException {
        Path assets = packDir.resolve("assets").resolve("zombierool");
        Files.createDirectories(assets.resolve("textures").resolve("block").resolve("maptex"));
        Files.createDirectories(assets.resolve("models").resolve("block"));
        Files.createDirectories(assets.resolve("models").resolve("item"));
        Files.createDirectories(assets.resolve("blockstates"));
        Files.writeString(packDir.resolve("pack.mcmeta"), """
                {"pack":{"pack_format":15,"description":"ZombieRool map block textures"}}
                """, StandardCharsets.UTF_8);
        String[] slots = MapTextures.readSlots(worldRoot);
        Path custom = worldRoot.resolve("zombierool").resolve("custom_blocks");
        for (int i = 0; i < MapTextures.SLOTS; i++) {
            String name = slots[i] == null ? "" : slots[i];
            copyFace(custom, name, "", assets.resolve("textures/block/maptex/" + i + "_side.png"));
            copyFace(custom, name, "_top", assets.resolve("textures/block/maptex/" + i + "_top.png"));
            copyFace(custom, name, "_bottom", assets.resolve("textures/block/maptex/" + i + "_bottom.png"));
            String side = "zombierool:block/maptex/" + i + "_side";
            String top = "zombierool:block/maptex/" + i + "_top";
            String bottom = "zombierool:block/maptex/" + i + "_bottom";
            String block = "maptex_" + i;
            writeModel(assets, "block/" + block, cube(side, top, bottom));
            writeModel(assets, "block/" + block + "_stairs", stairs("minecraft:block/stairs", side, top, bottom));
            writeModel(assets, "block/" + block + "_stairs_inner", stairs("minecraft:block/inner_stairs", side, top, bottom));
            writeModel(assets, "block/" + block + "_stairs_outer", stairs("minecraft:block/outer_stairs", side, top, bottom));
            writeModel(assets, "block/" + block + "_slab", stairs("minecraft:block/slab", side, top, bottom));
            writeModel(assets, "block/" + block + "_slab_top", stairs("minecraft:block/slab_top", side, top, bottom));
            writeModel(assets, "block/" + block + "_fence_post", single("minecraft:block/fence_post", "texture", side));
            writeModel(assets, "block/" + block + "_fence_side", single("minecraft:block/fence_side", "texture", side));
            writeModel(assets, "block/" + block + "_fence_inventory", single("minecraft:block/fence_inventory", "texture", side));
            writeModel(assets, "block/" + block + "_wall_post", single("minecraft:block/template_wall_post", "wall", side));
            writeModel(assets, "block/" + block + "_wall_side", single("minecraft:block/template_wall_side", "wall", side));
            writeModel(assets, "block/" + block + "_wall_side_tall", single("minecraft:block/template_wall_side_tall", "wall", side));
            writeModel(assets, "block/" + block + "_wall_inventory", single("minecraft:block/wall_inventory", "wall", side));
            writeModel(assets, "block/" + block + "_pane_post", pane("minecraft:block/template_glass_pane_post", side, top));
            writeModel(assets, "block/" + block + "_pane_side", pane("minecraft:block/template_glass_pane_side", side, top));
            writeModel(assets, "block/" + block + "_pane_side_alt", pane("minecraft:block/template_glass_pane_side_alt", side, top));
            writeModel(assets, "block/" + block + "_pane_noside", pane("minecraft:block/template_glass_pane_noside", side, top));
            writeModel(assets, "block/" + block + "_pane_noside_alt", pane("minecraft:block/template_glass_pane_noside_alt", side, top));
            text(assets.resolve("blockstates").resolve(block + ".json"), "{\"variants\":{\"\":{\"model\":\"zombierool:block/" + block + "\"}}}");
            text(assets.resolve("blockstates").resolve(block + "_stairs.json"), stairState(block));
            text(assets.resolve("blockstates").resolve(block + "_slab.json"),
                    "{\"variants\":{\"type=bottom\":{\"model\":\"zombierool:block/" + block + "_slab\"},\"type=double\":{\"model\":\"zombierool:block/" + block + "\"},\"type=top\":{\"model\":\"zombierool:block/" + block + "_slab_top\"}}}");
            text(assets.resolve("blockstates").resolve(block + "_fence.json"), fenceState(block));
            text(assets.resolve("blockstates").resolve(block + "_wall.json"), wallState(block));
            text(assets.resolve("blockstates").resolve(block + "_pane.json"), paneState(block));
            text(assets.resolve("models/item").resolve(block + ".json"), "{\"parent\":\"zombierool:block/" + block + "\"}");
            text(assets.resolve("models/item").resolve(block + "_stairs.json"), "{\"parent\":\"zombierool:block/" + block + "_stairs\"}");
            text(assets.resolve("models/item").resolve(block + "_slab.json"), "{\"parent\":\"zombierool:block/" + block + "_slab\"}");
            text(assets.resolve("models/item").resolve(block + "_fence.json"), "{\"parent\":\"zombierool:block/" + block + "_fence_inventory\"}");
            text(assets.resolve("models/item").resolve(block + "_wall.json"), "{\"parent\":\"zombierool:block/" + block + "_wall_inventory\"}");
            text(assets.resolve("models/item").resolve(block + "_pane.json"), "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + side + "\"}}");
        }
    }

    private static void copyFace(Path custom, String name, String suffix, Path dest) throws IOException {
        Path source = null;
        if (!name.isEmpty()) {
            Path faced = custom.resolve(name + suffix + ".png");
            Path all = custom.resolve(name + ".png");
            if (!suffix.isEmpty() && Files.exists(faced)) source = faced;
            else if (Files.exists(all)) source = all;
        }
        if (source != null) Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        else Files.write(dest, PLACEHOLDER);
    }

    private static void writeModel(Path assets, String relative, String json) throws IOException {
        text(assets.resolve("models").resolve(relative + ".json"), json);
    }

    private static void text(Path file, String json) throws IOException {
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static String cube(String side, String top, String bottom) {
        return "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"" + side + "\",\"down\":\"" + bottom
                + "\",\"up\":\"" + top + "\",\"north\":\"" + side + "\",\"south\":\"" + side + "\",\"east\":\"" + side
                + "\",\"west\":\"" + side + "\"}}";
    }

    private static String stairs(String parent, String side, String top, String bottom) {
        return "{\"parent\":\"" + parent + "\",\"textures\":{\"particle\":\"" + side + "\",\"side\":\"" + side
                + "\",\"top\":\"" + top + "\",\"bottom\":\"" + bottom + "\"}}";
    }

    private static String single(String parent, String key, String texture) {
        return "{\"parent\":\"" + parent + "\",\"textures\":{\"" + key + "\":\"" + texture + "\"}}";
    }

    private static String pane(String parent, String pane, String edge) {
        return "{\"parent\":\"" + parent + "\",\"textures\":{\"pane\":\"" + pane + "\",\"edge\":\"" + edge + "\"}}";
    }

    private static String stairState(String block) {
        String straight = "zombierool:block/" + block + "_stairs";
        String inner = "zombierool:block/" + block + "_stairs_inner";
        String outer = "zombierool:block/" + block + "_stairs_outer";
        String[][] rows = {
                {"east", "bottom", "straight", straight, "0", "0"},
                {"west", "bottom", "straight", straight, "0", "180"},
                {"south", "bottom", "straight", straight, "0", "90"},
                {"north", "bottom", "straight", straight, "0", "270"},
                {"east", "bottom", "outer_right", outer, "0", "0"},
                {"west", "bottom", "outer_right", outer, "0", "180"},
                {"south", "bottom", "outer_right", outer, "0", "90"},
                {"north", "bottom", "outer_right", outer, "0", "270"},
                {"east", "bottom", "outer_left", outer, "0", "270"},
                {"west", "bottom", "outer_left", outer, "0", "90"},
                {"south", "bottom", "outer_left", outer, "0", "0"},
                {"north", "bottom", "outer_left", outer, "0", "180"},
                {"east", "bottom", "inner_right", inner, "0", "0"},
                {"west", "bottom", "inner_right", inner, "0", "180"},
                {"south", "bottom", "inner_right", inner, "0", "90"},
                {"north", "bottom", "inner_right", inner, "0", "270"},
                {"east", "bottom", "inner_left", inner, "0", "270"},
                {"west", "bottom", "inner_left", inner, "0", "90"},
                {"south", "bottom", "inner_left", inner, "0", "0"},
                {"north", "bottom", "inner_left", inner, "0", "180"},
                {"east", "top", "straight", straight, "180", "0"},
                {"west", "top", "straight", straight, "180", "180"},
                {"south", "top", "straight", straight, "180", "90"},
                {"north", "top", "straight", straight, "180", "270"},
                {"east", "top", "outer_right", outer, "180", "90"},
                {"west", "top", "outer_right", outer, "180", "270"},
                {"south", "top", "outer_right", outer, "180", "180"},
                {"north", "top", "outer_right", outer, "180", "0"},
                {"east", "top", "outer_left", outer, "180", "0"},
                {"west", "top", "outer_left", outer, "180", "180"},
                {"south", "top", "outer_left", outer, "180", "90"},
                {"north", "top", "outer_left", outer, "180", "270"},
                {"east", "top", "inner_right", inner, "180", "90"},
                {"west", "top", "inner_right", inner, "180", "270"},
                {"south", "top", "inner_right", inner, "180", "180"},
                {"north", "top", "inner_right", inner, "180", "0"},
                {"east", "top", "inner_left", inner, "180", "0"},
                {"west", "top", "inner_left", inner, "180", "180"},
                {"south", "top", "inner_left", inner, "180", "90"},
                {"north", "top", "inner_left", inner, "180", "270"}
        };
        StringBuilder builder = new StringBuilder("{\"variants\":{");
        for (int i = 0; i < rows.length; i++) {
            String[] row = rows[i];
            if (i > 0) builder.append(',');
            builder.append("\"facing=").append(row[0]).append(",half=").append(row[1]).append(",shape=").append(row[2]).append("\":{\"model\":\"").append(row[3]).append("\"");
            int x = Integer.parseInt(row[4]);
            int y = Integer.parseInt(row[5]);
            if (x != 0) builder.append(",\"x\":").append(x);
            if (y != 0) builder.append(",\"y\":").append(y);
            if (x != 0 || y != 0) builder.append(",\"uvlock\":true");
            builder.append('}');
        }
        return builder.append("}}").toString();
    }

    private static String fenceState(String block) {
        String post = "zombierool:block/" + block + "_fence_post";
        String side = "zombierool:block/" + block + "_fence_side";
        return "{\"multipart\":["
                + "{\"apply\":{\"model\":\"" + post + "\"}},"
                + "{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":\"" + side + "\",\"uvlock\":true}},"
                + "{\"when\":{\"east\":\"true\"},\"apply\":{\"model\":\"" + side + "\",\"y\":90,\"uvlock\":true}},"
                + "{\"when\":{\"south\":\"true\"},\"apply\":{\"model\":\"" + side + "\",\"y\":180,\"uvlock\":true}},"
                + "{\"when\":{\"west\":\"true\"},\"apply\":{\"model\":\"" + side + "\",\"y\":270,\"uvlock\":true}}"
                + "]}";
    }

    private static String wallState(String block) {
        String post = "zombierool:block/" + block + "_wall_post";
        String side = "zombierool:block/" + block + "_wall_side";
        String tall = "zombierool:block/" + block + "_wall_side_tall";
        return "{\"multipart\":["
                + "{\"when\":{\"up\":\"true\"},\"apply\":{\"model\":\"" + post + "\"}},"
                + "{\"when\":{\"north\":\"low\"},\"apply\":{\"model\":\"" + side + "\",\"uvlock\":true}},"
                + "{\"when\":{\"east\":\"low\"},\"apply\":{\"model\":\"" + side + "\",\"y\":90,\"uvlock\":true}},"
                + "{\"when\":{\"south\":\"low\"},\"apply\":{\"model\":\"" + side + "\",\"y\":180,\"uvlock\":true}},"
                + "{\"when\":{\"west\":\"low\"},\"apply\":{\"model\":\"" + side + "\",\"y\":270,\"uvlock\":true}},"
                + "{\"when\":{\"north\":\"tall\"},\"apply\":{\"model\":\"" + tall + "\",\"uvlock\":true}},"
                + "{\"when\":{\"east\":\"tall\"},\"apply\":{\"model\":\"" + tall + "\",\"y\":90,\"uvlock\":true}},"
                + "{\"when\":{\"south\":\"tall\"},\"apply\":{\"model\":\"" + tall + "\",\"y\":180,\"uvlock\":true}},"
                + "{\"when\":{\"west\":\"tall\"},\"apply\":{\"model\":\"" + tall + "\",\"y\":270,\"uvlock\":true}}"
                + "]}";
    }

    private static String paneState(String block) {
        String post = "zombierool:block/" + block + "_pane_post";
        String side = "zombierool:block/" + block + "_pane_side";
        String alt = "zombierool:block/" + block + "_pane_side_alt";
        String noside = "zombierool:block/" + block + "_pane_noside";
        String nosideAlt = "zombierool:block/" + block + "_pane_noside_alt";
        return "{\"multipart\":["
                + "{\"apply\":{\"model\":\"" + post + "\"}},"
                + "{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":\"" + side + "\"}},"
                + "{\"when\":{\"east\":\"true\"},\"apply\":{\"model\":\"" + side + "\",\"y\":90}},"
                + "{\"when\":{\"south\":\"true\"},\"apply\":{\"model\":\"" + alt + "\"}},"
                + "{\"when\":{\"west\":\"true\"},\"apply\":{\"model\":\"" + alt + "\",\"y\":90}},"
                + "{\"when\":{\"north\":\"false\"},\"apply\":{\"model\":\"" + noside + "\"}},"
                + "{\"when\":{\"east\":\"false\"},\"apply\":{\"model\":\"" + nosideAlt + "\"}},"
                + "{\"when\":{\"south\":\"false\"},\"apply\":{\"model\":\"" + nosideAlt + "\",\"y\":90}},"
                + "{\"when\":{\"west\":\"false\"},\"apply\":{\"model\":\"" + noside + "\",\"y\":270}}"
                + "]}";
    }
}
