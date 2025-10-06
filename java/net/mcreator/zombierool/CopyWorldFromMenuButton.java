package net.mcreator.zombierool;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import org.apache.commons.io.FileUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.mcreator.zombierool.init.ZombieroolModSounds;

@Mod.EventBusSubscriber
public class CopyWorldFromMenuButton {

    private static String selectedWorldName = null;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmm");
    private static final Logger LOGGER = LogManager.getLogger();

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    private static final Component SELECT_WORLD_TEXT = getTranslatedComponent("Monde à copier", "World to copy");
    private static final Component COPY_BUTTON_TEXT = getTranslatedComponent("Copier le monde sélectionné", "Copy Selected World");
    private static final Component ERROR_SAVE_DIR_NOT_FOUND = getTranslatedComponent("Dossier des sauvegardes introuvable ou inaccessible.", "Saves folder not found or inaccessible.");
    private static final Component ERROR_NO_WORLDS_FOUND = getTranslatedComponent("Aucun monde valide trouvé à copier.", "No valid worlds found to copy.");
    private static final Component ERROR_SELECT_WORLD = getTranslatedComponent("Veuillez sélectionner un monde.", "Please select a world.");
    private static final Component ERROR_SOURCE_NOT_FOUND = getTranslatedComponent("Le monde source est introuvable ou n'est pas un dossier.", "Source world not found or is not a directory.");
    private static final Component INFO_COPYING_WORLD = getTranslatedComponent("Copie du monde en cours...", "Copying world...");
    private static final Component SUCCESS_COPY = getTranslatedComponent("Monde copié avec succès : ", "World copied successfully: ");
    private static final Component ERROR_COPY_FAILED = getTranslatedComponent("Échec de la copie du monde : ", "World copy failed: ");
    private static final Component ERROR_LEVEL_DAT = getTranslatedComponent("Erreur lors de la lecture/écriture de level.dat.", "Error reading/writing level.dat.");


    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return; // Only concern ourselves with the TitleScreen
        }

        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        if (!savesDir.exists() || !savesDir.isDirectory()) {
            LOGGER.error("Dossier des sauvegardes introuvable ou inaccessible : " + savesDir.getAbsolutePath());
            return;
        }

        if (!savesDir.canWrite()) {
            LOGGER.error("Le dossier 'saves' n'est pas inscriptible : " + savesDir.getAbsolutePath());
            return;
        }

        File[] worldFolders = savesDir.listFiles(File::isDirectory);
        if (worldFolders == null || worldFolders.length == 0) {
            LOGGER.warn("Aucun dossier de monde trouvé dans le répertoire des sauvegardes.");
            return;
        }

        List<String> worldNames = Arrays.stream(worldFolders)
                .filter(folder -> new File(folder, "level.dat").exists())
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(File::getName)
                .collect(Collectors.toList());

        if (worldNames.isEmpty()) {
            LOGGER.warn("Aucun monde valide (avec level.dat) trouvé pour la copie.");
            return;
        }

        selectedWorldName = worldNames.get(0);

        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24; // Standard spacing between Minecraft menu buttons

        // Calculate the total height of your button block
        int totalCustomButtonHeight = (buttonHeight * 2) + spacing; // For 2 buttons + 1 spacing

        // Find the Y position of the "Quit Game" button, which is usually the lowest main menu button.
        // Main menu buttons typically start at screen.height / 4 + 48
        // The "Quit Game" button is roughly 5 button heights + 5 spacings below that.
        int lowestMainMenuButtonY = screen.height / 4 + 48 + (spacing * 5); // Approx. "Quit Game" button's Y

        // Calculate a starting Y for your buttons, ensuring they are below the main menu buttons
        int startY = lowestMainMenuButtonY + spacing; // Start 1 spacing below the "Quit Game" button

        // Ensure your buttons don't go off the bottom of the screen, leaving a small margin
        int minBottomMargin = 20; // Pixels from the bottom of the screen
        if (startY + totalCustomButtonHeight + minBottomMargin > screen.height) {
            startY = screen.height - totalCustomButtonHeight - minBottomMargin;
            // Also, ensure it doesn't go ABOVE the main menu buttons if the screen is super short
            if (startY < lowestMainMenuButtonY + spacing) {
                startY = lowestMainMenuButtonY + spacing;
            }
        }

        int x = screen.width / 2 - (buttonWidth / 2); // Center your buttons horizontally

        CycleButton<String> selector = CycleButton.builder(Component::literal)
                .withValues(worldNames)
                .withInitialValue(selectedWorldName)
                .create(x, startY, buttonWidth, buttonHeight, SELECT_WORLD_TEXT,
                        (btn, value) -> {
                            selectedWorldName = value;
                            Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_SELECT.get(), 1.0F)
                            );
                        });

        Button copyButton = Button.builder(COPY_BUTTON_TEXT, btn -> {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
            );
            copySelectedWorld(savesDir);
        }).bounds(x, startY + spacing, buttonWidth, buttonHeight).build();

        event.addListener(selector);
        event.addListener(copyButton);
    }

    private static void copySelectedWorld(File savesDir) {
        if (selectedWorldName == null) {
            showNotification(ERROR_SELECT_WORLD, 0xFFFF0000);
            return;
        }

        File source = new File(savesDir, selectedWorldName);
        if (!source.exists() || !source.isDirectory()) {
            showNotification(ERROR_SOURCE_NOT_FOUND, 0xFFFF0000);
            LOGGER.error("Monde source introuvable : " + source.getAbsolutePath());
            return;
        }

        File target = null;
        try {
            String baseCopyName = selectedWorldName + "_Copy_" + DATE_FORMAT.format(new Date());
            target = getNextAvailableFolder(savesDir, baseCopyName);

            showNotification(INFO_COPYING_WORLD, 0xFFADD8E6);
            LOGGER.info("Début de la copie du monde '{}' vers '{}'", selectedWorldName, target.getName());

            FileUtils.copyDirectory(source, target);

            File levelDat = new File(target, "level.dat");
            if (levelDat.exists()) {
                try {
                    CompoundTag root = NbtIo.readCompressed(levelDat);
                    CompoundTag data = root.getCompound("Data");
                    data.putString("LevelName", target.getName());
                    NbtIo.writeCompressed(root, levelDat);
                    LOGGER.info("level.dat mis à jour pour le nouveau monde : " + target.getName());
                } catch (IOException e) {
                    showNotification(ERROR_LEVEL_DAT, 0xFFFF0000);
                    LOGGER.error("Erreur lors de la lecture/écriture de level.dat pour " + target.getName(), e);
                    FileUtils.deleteDirectory(target);
                    return;
                }
            } else {
                LOGGER.warn("Pas de level.dat trouvé dans le monde copié : " + target.getName());
            }

            showNotification(SUCCESS_COPY.copy().append(getTranslatedComponent(target.getName(), target.getName())), 0xFF00FF00);
            LOGGER.info("Monde '{}' copié avec succès vers '{}'", selectedWorldName, target.getAbsolutePath());

        } catch (IOException e) {
            showNotification(ERROR_COPY_FAILED.copy().append(Component.literal(e.getMessage())), 0xFFFF0000);
            LOGGER.error("Erreur critique lors de la copie du monde de '{}'", selectedWorldName, e);
            if (target != null && target.exists()) {
                try {
                    FileUtils.deleteDirectory(target);
                    LOGGER.info("Dossier de copie partiel supprimé : " + target.getAbsolutePath());
                } catch (IOException cleanupEx) {
                    LOGGER.error("Erreur lors du nettoyage du dossier partiel : " + target.getAbsolutePath(), cleanupEx);
                }
            }
        }
    }

    private static File getNextAvailableFolder(File baseDir, String baseName) {
        File folder = new File(baseDir, baseName);
        int i = 1;
        while (folder.exists()) {
            folder = new File(baseDir, baseName + "_" + i);
            i++;
        }
        return folder;
    }

    private static void showNotification(Component message, int color) {
        Minecraft.getInstance().setScreen(new CopyNotificationScreen(message, color));
    }
}
