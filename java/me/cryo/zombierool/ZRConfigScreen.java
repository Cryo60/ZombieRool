package me.cryo.zombierool.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.configuration.ZRClientConfig.HalloweenMode;
import me.cryo.zombierool.HalloweenManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSyncClientPrefsPacket;

public class ZRConfigScreen extends Screen {
    private final Screen parentScreen;
    private Button halloweenModeButton;
    private Button preferZrButton;
    private Button animatePreviewButton;
    private Button hideXpButton;
    
    private HalloweenMode currentMode;
    private boolean currentPreferZr;
    private boolean currentAnimatePreview;
    private boolean currentHideXp;

    public ZRConfigScreen(Screen parentScreen) {
        super(Component.translatable("zombierool.config.client.title"));
        this.parentScreen = parentScreen;
        this.currentMode = ZRClientConfig.getHalloweenMode();
        this.currentPreferZr = ZRClientConfig.prefersZrWeapons();
        this.currentAnimatePreview = ZRClientConfig.animateWeaponPreview();
        this.currentHideXp = ZRClientConfig.hideXpNotifications();
    }

    @Override
    protected void init() {
        super.init();

        this.halloweenModeButton = Button.builder(
            getModeButtonText(),
            button -> {
                cycleHalloweenMode();
                button.setMessage(getModeButtonText());
            })
            .bounds(this.width / 2 - 155, this.height / 6 + 24, 310, 20)
            .build();
        if (this.minecraft.player != null && !this.minecraft.player.hasPermissions(2)) {
            this.halloweenModeButton.active = false;
        }
        this.addRenderableWidget(this.halloweenModeButton);

        this.preferZrButton = Button.builder(
            getPreferZrButtonText(),
            button -> {
                currentPreferZr = !currentPreferZr;
                button.setMessage(getPreferZrButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, this.height / 6 + 48, 310, 20)
            .build();
        this.addRenderableWidget(this.preferZrButton);

        this.animatePreviewButton = Button.builder(
            getAnimatePreviewButtonText(),
            button -> {
                currentAnimatePreview = !currentAnimatePreview;
                button.setMessage(getAnimatePreviewButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, this.height / 6 + 72, 310, 20)
            .build();
        this.addRenderableWidget(this.animatePreviewButton);

        this.hideXpButton = Button.builder(
            getHideXpButtonText(),
            button -> {
                currentHideXp = !currentHideXp;
                button.setMessage(getHideXpButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, this.height / 6 + 96, 310, 20)
            .build();
        this.addRenderableWidget(this.hideXpButton);

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            button -> this.minecraft.setScreen(parentScreen))
            .bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20)
            .build());
    }

    private void cycleHalloweenMode() {
        switch (currentMode) {
            case AUTO -> currentMode = HalloweenMode.FORCE_ON;
            case FORCE_ON -> currentMode = HalloweenMode.FORCE_OFF;
            case FORCE_OFF -> currentMode = HalloweenMode.AUTO;
        }
        syncPrefs();
    }

    private void syncPrefs() {
        ZRClientConfig.setHalloweenMode(currentMode);
        ZRClientConfig.setPreferZrWeapons(currentPreferZr);
        ZRClientConfig.setAnimateWeaponPreview(currentAnimatePreview);
        ZRClientConfig.setHideXpNotifications(currentHideXp);
        me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxRenderer.clearCache();
        if (this.minecraft.getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(
                new C2SSyncClientPrefsPacket(currentMode.name(), currentPreferZr)
            );
        }
    }

    private Component getModeButtonText() {
        String modeText = switch (currentMode) {
            case FORCE_ON -> "FORCE ON";
            case FORCE_OFF -> "FORCE OFF";
            default -> "AUTO";
        };
        String emoji = switch (currentMode) {
            case FORCE_ON -> "🎃";
            case FORCE_OFF -> "❌";
            default -> "🔄";
        };
        return Component.literal("Halloween Mode: " + emoji + " " + modeText);
    }

    private Component getPreferZrButtonText() {
        if (currentPreferZr) {
            return Component.literal("Weapon Models: Classic ZR");
        } else {
            return Component.literal("Weapon Models: TacZ (If installed)");
        }
    }

    private Component getAnimatePreviewButtonText() {
        String text = Component.translatable("gui.zombierool.config.animate_preview").getString() + (currentAnimatePreview ? "ON" : "OFF");
        return Component.literal(text);
    }

    private Component getHideXpButtonText() {
        String text = Component.translatable("zombierool.config.hide_xp").getString() + ": " + (currentHideXp ? "ON" : "OFF");
        return Component.literal(text);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        boolean isNaturalPeriod = HalloweenManager.isNaturalHalloweenPeriod();
        String periodStatus = "Natural Period: " + 
            (isNaturalPeriod ? "🎃 ACTIVE (Oct 20 - Nov 5)" : "❌ INACTIVE");
        guiGraphics.drawCenteredString(this.font, periodStatus, 
            this.width / 2, this.height / 6 + 124, 
            isNaturalPeriod ? 0xFF8C00 : 0xFF0000);

        boolean isEffective = HalloweenManager.isHalloweenPeriod();
        String effectiveStatus = "Effective Status: " + 
            (isEffective ? "🎃 ACTIVE" : "❌ DISABLED");
        guiGraphics.drawCenteredString(this.font, effectiveStatus, 
            this.width / 2, this.height / 6 + 144, 
            isEffective ? 0x00FF00 : 0xFF0000);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}