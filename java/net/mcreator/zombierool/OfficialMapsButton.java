package net.mcreator.zombierool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class OfficialMapsButton {

    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static Component getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }

        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        // Position ABOVE the CopyWorldFromMenuButton buttons
        // CopyWorldFromMenuButton starts at: lowestMainMenuButtonY + spacing
        // We need to be ABOVE that, so we subtract our button height + spacing
        int lowestMainMenuButtonY = screen.height / 4 + 48 + (spacing * 5);
        int copyButtonStartY = lowestMainMenuButtonY + spacing;
        int ourButtonY = copyButtonStartY - buttonHeight - (spacing / 2); // Half spacing above

        // Ensure it doesn't overlap with main menu
        if (ourButtonY < lowestMainMenuButtonY) {
            ourButtonY = lowestMainMenuButtonY;
        }

        int x = screen.width / 2 - (buttonWidth / 2);

        Component buttonText = getTranslatedComponent("Maps Officielles", "Official Maps");

        Button officialMapsButton = Button.builder(buttonText, btn -> {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
            );
            Minecraft.getInstance().setScreen(new MapDownloaderScreen(screen));
        }).bounds(x, ourButtonY, buttonWidth, buttonHeight).build();

        event.addListener(officialMapsButton);
    }
}