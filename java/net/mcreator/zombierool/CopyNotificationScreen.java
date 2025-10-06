package net.mcreator.zombierool;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance; // Ensure this is imported
import net.mcreator.zombierool.init.ZombieroolModSounds; // Import your sound events class

public class CopyNotificationScreen extends Screen {

    private final Component message;
    private final int messageColor;
    private long startTime;
    private static final int DISPLAY_DURATION_MS = 3000;
    private static final int BACKGROUND_COLOR = 0x80000000;

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

    public CopyNotificationScreen(Component message, int messageColor) {
        super(getTranslatedComponent("Notification", "Notification")); // Translate screen title
        this.message = message;
        this.messageColor = messageColor;
        // Play the ui_notify sound when the notification screen is created
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(
                ZombieroolModSounds.UI_NOTIFIY.get(), // Use the registered SoundEvent
                1.0F
            )
        );
    }

    @Override
    protected void init() {
        super.init();
        this.startTime = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        int panelWidth = Math.max(200, font.width(message) + 40);
        int panelHeight = 60;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BACKGROUND_COLOR);
        guiGraphics.drawCenteredString(font, getTranslatedComponent("Notification de Copie", "Copy Notification"), this.width / 2, panelY + 10, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, message, this.width / 2, panelY + 35, messageColor);

        if (Util.getMillis() - startTime > DISPLAY_DURATION_MS) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.onClose();
        return true;
    }
}
