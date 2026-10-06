package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.ZombieroolMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import me.cryo.zombierool.client.gui.SecretConsoleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Mod.EventBusSubscriber(modid = "zombierool")
public class SecretMapManager {
    public static String pendingMode = "NONE";
    public static String pendingWorldName = "";

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String levelName = player.server.getWorldData().getLevelName();
            if (levelName.equals(pendingWorldName)) {
                if ("SURVIVAL".equals(pendingMode)) {
                    player.setGameMode(GameType.SURVIVAL);
                    ZombieroolMod.queueServerWork(20, () -> {
                        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "zombierool start");
                    });
                } else if ("CREATIVE".equals(pendingMode)) {
                    player.setGameMode(GameType.CREATIVE);
                }
                pendingMode = "NONE";
                pendingWorldName = "";
            }
        }
    }

    public static void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }

    @Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHooks {
        @SubscribeEvent
        public static void onInitScreen(ScreenEvent.Init.Pre event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                File secretMap = new File(Minecraft.getInstance().gameDirectory, "saves/temp_zr_secret");
                if (secretMap.exists()) {
                    deleteDirectory(secretMap);
                    System.out.println("[ZombieRool] Deleted temporary secret map directory.");
                }
                File copyMap = new File(Minecraft.getInstance().gameDirectory, "saves/temp_zr_copy");
                if (copyMap.exists()) {
                    deleteDirectory(copyMap);
                    System.out.println("[ZombieRool] Deleted temporary copied map directory.");
                }
            }
        }

        @SubscribeEvent
        public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
            if (event.getCodePoint() == '²') {
                if (!(event.getScreen() instanceof SecretConsoleScreen)) {
                    Minecraft.getInstance().setScreen(new SecretConsoleScreen(event.getScreen()));
                }
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            if (event.getKeyCode() == GLFW.GLFW_KEY_GRAVE_ACCENT || event.getKeyCode() == 161 || event.getKeyCode() == GLFW.GLFW_KEY_BACKSLASH) {
                if (!(event.getScreen() instanceof SecretConsoleScreen)) {
                    Minecraft.getInstance().setScreen(new SecretConsoleScreen(event.getScreen()));
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && event.getAction() == GLFW.GLFW_PRESS) {
                if (event.getKey() == GLFW.GLFW_KEY_GRAVE_ACCENT || event.getKey() == 161 || event.getKey() == GLFW.GLFW_KEY_BACKSLASH) {
                    mc.setScreen(new SecretConsoleScreen(null));
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void loadSecretMap(String mapId, boolean isSurvival, me.cryo.zombierool.client.gui.SecretConsoleScreen console) {
        String rawInput = mapId.trim();
        if (!rawInput.toLowerCase(java.util.Locale.ROOT).startsWith("zr_")) {
            console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.err_prefix").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        java.io.File savesDir = new java.io.File(mc.gameDirectory, "saves");
        String folderName = isSurvival ? "temp_zr_secret" : rawInput;
        java.io.File targetDir = new java.io.File(savesDir, folderName);
        if (!isSurvival && targetDir.exists() && new java.io.File(targetDir, "level.dat").exists()) {
            console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.devmap_exists", folderName).withStyle(net.minecraft.ChatFormatting.YELLOW));
            console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.devmap_load").withStyle(net.minecraft.ChatFormatting.YELLOW));
            launchMap(mc, console, folderName, false);
            return;
        }
        String withoutZr = rawInput.substring(3);
        String[] attempts = {
            rawInput + ".zip",
            rawInput.toLowerCase(java.util.Locale.ROOT) + ".zip",
            withoutZr + ".zip",
            withoutZr.toLowerCase(java.util.Locale.ROOT) + ".zip"
        };
        java.io.InputStream foundStream = null;
        String matchedName = "";
        for (String attempt : attempts) {
            foundStream = ZombieroolMod.class.getResourceAsStream("/assets/zombierool/maps/" + attempt);
            if (foundStream != null) {
                matchedName = attempt;
                break;
            }
        }
        if (foundStream == null) {
            console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.err_notfound", rawInput).withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        final java.io.InputStream is = foundStream;
        final String finalMatchedName = matchedName;
        console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.extracting", finalMatchedName).withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        new Thread(() -> {
            try {
                extractZip(is, targetDir, folderName);
                mc.execute(() -> {
                    console.addLog(net.minecraft.network.chat.Component.translatable("gui.zombierool.console.extracted").withStyle(net.minecraft.ChatFormatting.GREEN));
                    launchMap(mc, console, folderName, isSurvival);
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    console.addLog(net.minecraft.network.chat.Component.literal("§cException: " + e.getMessage()));
                    e.printStackTrace();
                });
            }
        }).start();
    }

    public static final class CopyResult {
        public final String errorKey;
        public final String arg;
        public final boolean fromAssets;

        private CopyResult(String errorKey, String arg, boolean fromAssets) {
            this.errorKey = errorKey;
            this.arg = arg;
            this.fromAssets = fromAssets;
        }

        public boolean success() {
            return errorKey == null;
        }

        public static CopyResult ok(boolean fromAssets) {
            return new CopyResult(null, null, fromAssets);
        }

        public static CopyResult error(String errorKey, String arg) {
            return new CopyResult(errorKey, arg, false);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static CopyResult copyMap(String sourceName, String destName) {
        if (!isSafeFolderName(sourceName) || !isSafeFolderName(destName)) {
            return CopyResult.error("gui.zombierool.console.copy.badname", null);
        }
        if (sourceName.equalsIgnoreCase(destName)) {
            return CopyResult.error("gui.zombierool.console.copy.same", null);
        }
        if (isReservedFolder(destName)) {
            return CopyResult.error("gui.zombierool.console.copy.reserved", destName);
        }

        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        File sourceDir = new File(savesDir, sourceName);
        File destDir = new File(savesDir, destName);
        try {
            String savesCanon = savesDir.getCanonicalPath();
            if (!isInside(savesCanon, sourceDir) || !isInside(savesCanon, destDir)) {
                return CopyResult.error("gui.zombierool.console.copy.badname", null);
            }
        } catch (IOException e) {
            return CopyResult.error("gui.zombierool.console.copy.fail", e.getMessage());
        }
        if (destDir.exists()) {
            return CopyResult.error("gui.zombierool.console.copy.exists", destName);
        }

        if (sourceDir.isDirectory() && new File(sourceDir, "level.dat").isFile()) {
            try {
                copyDirectory(sourceDir, destDir);
                renameLevel(destDir, destName);
                return CopyResult.ok(false);
            } catch (Exception e) {
                deleteDirectory(destDir);
                return CopyResult.error("gui.zombierool.console.copy.fail", errorText(e));
            }
        }

        InputStream bundled = openBundledMap(sourceName);
        if (bundled == null) {
            return CopyResult.error("gui.zombierool.console.copy.notfound", sourceName);
        }
        try {
            extractZip(bundled, destDir, destName);
            if (!new File(destDir, "level.dat").isFile()) {
                deleteDirectory(destDir);
                return CopyResult.error("gui.zombierool.console.copy.fail", "level.dat");
            }
            return CopyResult.ok(true);
        } catch (Exception e) {
            deleteDirectory(destDir);
            return CopyResult.error("gui.zombierool.console.copy.fail", errorText(e));
        }
    }

    private static void extractZip(InputStream is, File targetDir, String levelName) throws Exception {
        if (targetDir.exists()) {
            deleteDirectory(targetDir);
        }
        targetDir.mkdirs();
        try (InputStream in = is; ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalFilePath = file.getCanonicalPath();
                if (!canonicalFilePath.equals(canonicalDestPath) && !canonicalFilePath.startsWith(canonicalDestPath + File.separator)) {
                    throw new Exception("Zip Slip detected: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    Files.copy(zis, file.toPath());
                }
            }
        }
        File levelDat = new File(targetDir, "level.dat");
        if (levelDat.exists()) {
            renameLevel(targetDir, levelName);
        }
    }

    private static void copyDirectory(File source, File dest) throws IOException {
        Path sourcePath = source.toPath();
        Path destPath = dest.toPath();
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destPath.resolve(sourcePath.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.equalsIgnoreCase("session.lock") || fileName.equalsIgnoreCase("level.dat_old")) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = destPath.resolve(sourcePath.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void renameLevel(File worldDir, String levelName) throws IOException {
        File levelDat = new File(worldDir, "level.dat");
        if (!levelDat.isFile()) {
            throw new IOException("level.dat missing");
        }
        net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(levelDat);
        net.minecraft.nbt.CompoundTag data = root.getCompound("Data");
        data.putString("LevelName", levelName);
        root.put("Data", data);
        net.minecraft.nbt.NbtIo.writeCompressed(root, levelDat);
    }

    private static InputStream openBundledMap(String rawInput) {
        if (!rawInput.toLowerCase(Locale.ROOT).startsWith("zr_")) {
            return null;
        }
        String withoutZr = rawInput.substring(3);
        String[] attempts = {
            rawInput + ".zip",
            rawInput.toLowerCase(Locale.ROOT) + ".zip",
            withoutZr + ".zip",
            withoutZr.toLowerCase(Locale.ROOT) + ".zip"
        };
        for (String attempt : attempts) {
            InputStream stream = ZombieroolMod.class.getResourceAsStream("/assets/zombierool/maps/" + attempt);
            if (stream != null) {
                return stream;
            }
        }
        return null;
    }

    private static boolean isSafeFolderName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32) {
                return false;
            }
            switch (c) {
                case '/':
                case '\\':
                case ':':
                case '*':
                case '?':
                case '"':
                case '<':
                case '>':
                case '|':
                    return false;
                default:
                    break;
            }
        }
        return true;
    }

    private static boolean isReservedFolder(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("temp_zr_secret") || lower.equals("temp_zr_copy");
    }

    private static boolean isInside(String parentCanon, File child) throws IOException {
        String childCanon = child.getCanonicalPath();
        return childCanon.equals(parentCanon) || childCanon.startsWith(parentCanon + File.separator);
    }

    private static String errorText(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @OnlyIn(Dist.CLIENT)
    private static void launchMap(Minecraft mc, SecretConsoleScreen console, String folderName, boolean isSurvival) {
        pendingMode = isSurvival ? "SURVIVAL" : "CREATIVE";
        pendingWorldName = folderName;
        mc.createWorldOpenFlows().loadLevel(console, folderName);
    }
}