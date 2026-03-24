package me.cryo.zombierool;

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
import java.io.FileOutputStream;
import java.io.InputStream;
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
    public static void loadSecretMap(String mapId, boolean isSurvival, SecretConsoleScreen console) {
        String rawInput = mapId.trim();
        if (!rawInput.toLowerCase(Locale.ROOT).startsWith("zr_")) {
            console.addLog(Component.translatable("gui.zombierool.console.err_prefix").withStyle(ChatFormatting.RED));
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        File savesDir = new File(mc.gameDirectory, "saves");
        String folderName = isSurvival ? "temp_zr_secret" : rawInput;
        File targetDir = new File(savesDir, folderName);

        if (!isSurvival && targetDir.exists() && new File(targetDir, "level.dat").exists()) {
            console.addLog(Component.translatable("gui.zombierool.console.devmap_exists", folderName).withStyle(ChatFormatting.YELLOW));
            console.addLog(Component.translatable("gui.zombierool.console.devmap_load").withStyle(ChatFormatting.YELLOW));
            launchMap(mc, console, folderName, false);
            return;
        }

        String withoutZr = rawInput.substring(3);
        String[] attempts = {
            rawInput + ".zip",
            rawInput.toLowerCase(Locale.ROOT) + ".zip",
            withoutZr + ".zip",
            withoutZr.toLowerCase(Locale.ROOT) + ".zip"
        };

        InputStream foundStream = null;
        String matchedName = "";

        for (String attempt : attempts) {
            foundStream = ZombieroolMod.class.getResourceAsStream("/assets/zombierool/maps/" + attempt);
            if (foundStream != null) {
                matchedName = attempt;
                break;
            }
        }

        if (foundStream == null) {
            console.addLog(Component.translatable("gui.zombierool.console.err_notfound", rawInput).withStyle(ChatFormatting.RED));
            return;
        }

        final InputStream is = foundStream;
        final String finalMatchedName = matchedName;

        console.addLog(Component.translatable("gui.zombierool.console.extracting", finalMatchedName).withStyle(ChatFormatting.YELLOW));

        new Thread(() -> {
            try {
                if (targetDir.exists()) {
                    deleteDirectory(targetDir);
                }
                targetDir.mkdirs();

                try (ZipInputStream zis = new ZipInputStream(is)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        File file = new File(targetDir, entry.getName());
                        String canonicalDestPath = targetDir.getCanonicalPath();
                        String canonicalFilePath = file.getCanonicalPath();

                        if (!canonicalFilePath.startsWith(canonicalDestPath + File.separator)) {
                            throw new Exception("Zip Slip detected: " + entry.getName());
                        }

                        if (entry.isDirectory()) {
                            file.mkdirs();
                        } else {
                            file.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                    }
                }

                File levelDat = new File(targetDir, "level.dat");
                if (levelDat.exists()) {
                    CompoundTag root = NbtIo.readCompressed(levelDat);
                    CompoundTag data = root.getCompound("Data");
                    data.putString("LevelName", folderName);
                    NbtIo.writeCompressed(root, levelDat);
                }

                mc.execute(() -> {
                    console.addLog(Component.translatable("gui.zombierool.console.extracted").withStyle(ChatFormatting.GREEN));
                    launchMap(mc, console, folderName, isSurvival);
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    console.addLog(Component.literal("§cException: " + e.getMessage()));
                    e.printStackTrace();
                });
            }
        }).start();
    }

    @OnlyIn(Dist.CLIENT)
    private static void launchMap(Minecraft mc, SecretConsoleScreen console, String folderName, boolean isSurvival) {
        pendingMode = isSurvival ? "SURVIVAL" : "CREATIVE";
        pendingWorldName = folderName;
        mc.createWorldOpenFlows().loadLevel(console, folderName);
    }
}