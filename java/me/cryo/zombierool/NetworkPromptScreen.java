package me.cryo.zombierool.client.gui;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
public class NetworkPromptScreen extends Screen {
    private final Screen parentScreen;
    public NetworkPromptScreen(Screen parentScreen) {
        super(Component.translatable("gui.zombierool.network.title"));
        this.parentScreen = parentScreen;
    }
    @Override
    protected void init() {
        super.init();
        int btnWidth = 150;
        int btnHeight = 20;
        int startX = this.width / 2 - btnWidth - 10;
        int startY = this.height / 2 + 30;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.network.allow").withStyle(ChatFormatting.GREEN), btn -> {
            playSound();
            ZRClientConfig.setAllowNetworkRequests(true);
            ZRClientConfig.setHasAnsweredNetworkPrompt(true);
            this.minecraft.setScreen(parentScreen);
        }).bounds(startX, startY, btnWidth, btnHeight).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.network.decline").withStyle(ChatFormatting.RED), btn -> {
            playSound();
            ZRClientConfig.setAllowNetworkRequests(false);
            ZRClientConfig.setHasAnsweredNetworkPrompt(true);
            this.minecraft.setScreen(parentScreen);
        }).bounds(startX + btnWidth + 20, startY, btnWidth, btnHeight).build());
    }
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.fillGradient(0, 0, this.width, this.height, 0xDD000000, 0xEE000000);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFAA00);
        g.drawCenteredString(this.font, Component.translatable("gui.zombierool.network.warning1"), this.width / 2, this.height / 2 - 35, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("gui.zombierool.network.warning2"), this.width / 2, this.height / 2 - 20, 0xAAAAAA);
        g.drawCenteredString(this.font, Component.translatable("gui.zombierool.network.warning3"), this.width / 2, this.height / 2 - 5, 0xAAAAAA);
        g.drawCenteredString(this.font, Component.translatable("gui.zombierool.network.warning4"), this.width / 2, this.height / 2 + 10, 0xAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    private void playSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }
}