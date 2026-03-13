package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.CopyNotificationScreen;
import me.cryo.zombierool.MapDownloaderScreen;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class MainMenuExtensions {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmm");
    private static String selectedWorldName = null;

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }

        int buttonHeight = 20;
        int spacing = 4;
        
        int lowestMainMenuButtonY = screen.height / 4 + 48 + (24 * 5) + 16;
        int availableHeight = screen.height - lowestMainMenuButtonY - 10;
        
        boolean useGrid = availableHeight < ((buttonHeight + spacing) * 4);
        int buttonWidth = useGrid ? 145 : 200;
        
        int startX = screen.width / 2 - (buttonWidth / 2);
        int startY = lowestMainMenuButtonY;

        if (useGrid) {
            startX = screen.width / 2 - buttonWidth - (spacing / 2);
        }

        int currentY = startY;
        int col = 0;

        if (ModList.get().isLoaded("tacz")) {
            event.addListener(Button.builder(Component.literal("TacZ & Gunpacks"), btn -> {
                playSound();
                Minecraft.getInstance().setScreen(new TacZPackDownloaderScreen(screen));
            }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
            
            if (useGrid) {
                col++;
            } else {
                currentY += buttonHeight + spacing;
            }
        }

        // Modifié ici : "Official Maps" renommé en "Maps"
        event.addListener(Button.builder(Component.literal("Maps"), btn -> {
            playSound();
            Minecraft.getInstance().setScreen(new MapDownloaderScreen(screen));
        }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());

        if (useGrid) {
            col = 0;
            currentY += buttonHeight + spacing;
        } else {
            currentY += buttonHeight + spacing;
        }

        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] worldFolders = savesDir.listFiles(File::isDirectory);
            if (worldFolders != null && worldFolders.length > 0) {
                List<String> worldNames = Arrays.stream(worldFolders)
                        .filter(folder -> new File(folder, "level.dat").exists())
                        .sorted(Comparator.comparingLong(File::lastModified).reversed())
                        .map(File::getName)
                        .collect(Collectors.toList());

                if (!worldNames.isEmpty()) {
                    if (selectedWorldName == null || !worldNames.contains(selectedWorldName)) {
                        selectedWorldName = worldNames.get(0);
                    }

                    event.addListener(CycleButton.builder(Component::literal)
                            .withValues(worldNames)
                            .withInitialValue(selectedWorldName)
                            .create(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight, Component.literal("World to copy"),
                                    (btn, value) -> {
                                        selectedWorldName = value;
                                        playSound();
                                    }));
                                    
                    if (useGrid) {
                        col++;
                    } else {
                        currentY += buttonHeight + spacing;
                    }

                    event.addListener(Button.builder(Component.literal("Copy Selected World"), btn -> {
                        playSound();
                        copySelectedWorld(savesDir);
                    }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
                }
            }
        }
    }

    private static void playSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    private static void copySelectedWorld(File savesDir) {
        if (selectedWorldName == null) return;
        File source = new File(savesDir, selectedWorldName);
        if (!source.exists()) return;

        try {
            String baseCopyName = selectedWorldName + "_Copy_" + DATE_FORMAT.format(new Date());
            File target = getNextAvailableFolder(savesDir, baseCopyName);

            showNotification(Component.literal("Copying world..."), 0xFFADD8E6);
            FileUtils.copyDirectory(source, target);

            File levelDat = new File(target, "level.dat");
            if (levelDat.exists()) {
                CompoundTag root = NbtIo.readCompressed(levelDat);
                CompoundTag data = root.getCompound("Data");
                data.putString("LevelName", target.getName());
                NbtIo.writeCompressed(root, levelDat);
            }

            showNotification(Component.literal("Success: " + target.getName()), 0xFF00FF00);
        } catch (IOException e) {
            LOGGER.error("Failed to copy world", e);
            showNotification(Component.literal("Error copying world"), 0xFFFF0000);
        }
    }

    private static File getNextAvailableFolder(File baseDir, String baseName) {
        File folder = new File(baseDir, baseName);
        int i = 1;
        while (folder.exists()) {
            folder = new File(baseDir, baseName + "_" + i++);
        }
        return folder;
    }

    private static void showNotification(Component message, int color) {
        Minecraft.getInstance().setScreen(new CopyNotificationScreen(message, color));
    }
}