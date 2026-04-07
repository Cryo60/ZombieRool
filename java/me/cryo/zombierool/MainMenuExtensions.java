package me.cryo.zombierool.client.gui;
import me.cryo.zombierool.MapDownloaderScreen;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
@Mod.EventBusSubscriber
public class MainMenuExtensions {
    private static final Logger LOGGER = LogManager.getLogger();
    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        if (!ZRClientConfig.hasAnsweredNetworkPrompt()) {
            Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new NetworkPromptScreen(screen)));
            return;
        }
        int buttonHeight = 20;
        int spacing = 4;
        int lowestMainMenuButtonY = screen.height / 4 + 48 + (24 * 5) + 16;
        int availableHeight = screen.height - lowestMainMenuButtonY - 10;
        boolean useGrid = availableHeight < ((buttonHeight + spacing) * 5);
        int buttonWidth = useGrid ? 145 : 200;
        int startX = screen.width / 2 - (buttonWidth / 2);
        int startY = lowestMainMenuButtonY;
        if (useGrid) {
            startX = screen.width / 2 - buttonWidth - (spacing / 2);
        }
        int currentY = startY;
        int col = 0;
        if (ModList.get().isLoaded("tacz")) {
            event.addListener(Button.builder(Component.translatable("gui.zombierool.mainmenu.tacz"), btn -> {
                playSound();
                Minecraft.getInstance().setScreen(new TacZPackDownloaderScreen(screen));
            }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
            if (useGrid) {
                col++;
            } else {
                currentY += buttonHeight + spacing;
            }
        }
        event.addListener(Button.builder(Component.translatable("gui.zombierool.mainmenu.maps"), btn -> {
            playSound();
            Minecraft.getInstance().setScreen(new MapDownloaderScreen(screen));
        }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
        if (useGrid) {
            col = 0;
            currentY += buttonHeight + spacing;
        } else {
            currentY += buttonHeight + spacing;
        }
        event.addListener(Button.builder(Component.translatable("gui.zombierool.mainmenu.play_copy"), btn -> {
            playSound();
            Minecraft.getInstance().setScreen(new CopyWorldSelectionScreen(screen));
        }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
        if (useGrid) {
            col++;
        } else {
            currentY += buttonHeight + spacing;
        }
        event.addListener(Button.builder(Component.translatable("gui.zombierool.mainmenu.career"), btn -> {
            playSound();
            Minecraft.getInstance().setScreen(new CareerScreen(screen));
        }).bounds(startX + (col * (buttonWidth + spacing)), currentY, buttonWidth, buttonHeight).build());
    }
    private static void playSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }
}