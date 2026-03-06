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
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static MutableComponent getTranslatedComponent(String french, String english) {
        return Component.literal(isEnglishClient() ? english : french);
    }

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }

        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;
        
        // --- Calcul positions ---
        int lowestMainMenuButtonY = screen.height / 4 + 48 + (spacing * 5);
        int startY = lowestMainMenuButtonY + spacing;
        int x = screen.width / 2 - (buttonWidth / 2);

        // Ajustement si l'écran est trop petit
        int totalCustomHeight = (buttonHeight * 3) + spacing;
        if (startY + totalCustomHeight > screen.height - 20) {
            startY = screen.height - totalCustomHeight - 20;
            if (startY < lowestMainMenuButtonY) startY = lowestMainMenuButtonY;
        }

        // --- Bouton "Official Maps" ---
        int officialMapY = startY - buttonHeight - (spacing / 2); 
        if (officialMapY < lowestMainMenuButtonY) officialMapY = lowestMainMenuButtonY;

        event.addListener(Button.builder(getTranslatedComponent("Maps Officielles", "Official Maps"), btn -> {
            playSound();
            Minecraft.getInstance().setScreen(new MapDownloaderScreen(screen));
        }).bounds(x, officialMapY, buttonWidth, buttonHeight).build());


        // --- Logique Copie de Monde ---
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

                    // Sélecteur de monde
                    event.addListener(CycleButton.builder(Component::literal)
                            .withValues(worldNames)
                            .withInitialValue(selectedWorldName)
                            .create(x, startY, buttonWidth, buttonHeight, getTranslatedComponent("Monde à copier", "World to copy"),
                                    (btn, value) -> {
                                        selectedWorldName = value;
                                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_SELECT.get(), 1.0F));
                                    }));

                    // Bouton Copier
                    event.addListener(Button.builder(getTranslatedComponent("Copier le monde sélectionné", "Copy Selected World"), btn -> {
                        playSound();
                        copySelectedWorld(savesDir);
                    }).bounds(x, startY + spacing, buttonWidth, buttonHeight).build());
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
            
            showNotification(getTranslatedComponent("Copie en cours...", "Copying world..."), 0xFFADD8E6);
            FileUtils.copyDirectory(source, target);

            // Mise à jour level.dat
            File levelDat = new File(target, "level.dat");
            if (levelDat.exists()) {
                CompoundTag root = NbtIo.readCompressed(levelDat);
                CompoundTag data = root.getCompound("Data");
                data.putString("LevelName", target.getName());
                NbtIo.writeCompressed(root, levelDat);
            }

            showNotification(getTranslatedComponent("Succès : " + target.getName(), "Success: " + target.getName()), 0xFF00FF00);
        } catch (IOException e) {
            LOGGER.error("Failed to copy world", e);
            showNotification(getTranslatedComponent("Erreur lors de la copie", "Error copying world"), 0xFFFF0000);
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