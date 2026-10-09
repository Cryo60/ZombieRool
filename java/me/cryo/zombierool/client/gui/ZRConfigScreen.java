package me.cryo.zombierool.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.configuration.ZRClientConfig.HalloweenMode;
import me.cryo.zombierool.client.HalloweenManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSyncClientPrefsPacket;

public class ZRConfigScreen extends Screen {
    private final Screen parentScreen;
    private Button halloweenModeButton;
    private Button preferZrButton;
    private Button animatePreviewButton;
    private Button hideXpButton;
    private Button goreButton;
    
    private HalloweenMode currentMode;
    private boolean currentPreferZr;
    private boolean currentAnimatePreview;
    private boolean currentHideXp;
    private boolean currentReducedGore;

    public ZRConfigScreen(Screen parentScreen) {
        super(Component.translatable("zombierool.config.client.title"));
        this.parentScreen = parentScreen;
        this.currentMode = ZRClientConfig.getHalloweenMode();
        this.currentPreferZr = ZRClientConfig.prefersZrWeapons();
        this.currentAnimatePreview = ZRClientConfig.animateWeaponPreview();
        this.currentHideXp = ZRClientConfig.hideXpNotifications();
        this.currentReducedGore = ZRClientConfig.isGoreReduced();
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
            .bounds(this.width / 2 - 155, Math.max(8, this.height / 2 - 118) + 24, 310, 20)
            .build();

        this.addRenderableWidget(this.halloweenModeButton);

        this.preferZrButton = Button.builder(
            getPreferZrButtonText(),
            button -> {
                currentPreferZr = !currentPreferZr;
                button.setMessage(getPreferZrButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, Math.max(8, this.height / 2 - 118) + 48, 310, 20)
            .build();
        this.addRenderableWidget(this.preferZrButton);

        this.animatePreviewButton = Button.builder(
            getAnimatePreviewButtonText(),
            button -> {
                currentAnimatePreview = !currentAnimatePreview;
                button.setMessage(getAnimatePreviewButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, Math.max(8, this.height / 2 - 118) + 72, 310, 20)
            .build();
        this.addRenderableWidget(this.animatePreviewButton);

        this.hideXpButton = Button.builder(
            getHideXpButtonText(),
            button -> {
                currentHideXp = !currentHideXp;
                button.setMessage(getHideXpButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, Math.max(8, this.height / 2 - 118) + 96, 310, 20)
            .build();
        this.addRenderableWidget(this.hideXpButton);

        this.goreButton = Button.builder(
            getGoreButtonText(),
            button -> {
                currentReducedGore = !currentReducedGore;
                button.setMessage(getGoreButtonText());
                syncPrefs();
            })
            .bounds(this.width / 2 - 155, Math.max(8, this.height / 2 - 118) + 120, 310, 20)
            .build();
        this.addRenderableWidget(this.goreButton);

        this.addRenderableWidget(Button.builder(counterText(),button->{ZRClientConfig.setShowEnemiesRemaining(!ZRClientConfig.showEnemiesRemaining());button.setMessage(counterText());}).bounds(this.width/2-155,Math.max(8,this.height/2-118)+144,310,20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            button -> this.minecraft.setScreen(parentScreen))
            .bounds(this.width / 2 - 100, this.height - 26, 200, 20)
            .build());
    }

    private Component counterText(){return Component.translatable("zombierool.config.enemies_remaining",Component.translatable(ZRClientConfig.showEnemiesRemaining()?"options.on":"options.off"));}
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
        HalloweenManager.updateFromConfig();
        ZRClientConfig.setPreferZrWeapons(currentPreferZr);
        ZRClientConfig.setAnimateWeaponPreview(currentAnimatePreview);
        ZRClientConfig.setHideXpNotifications(currentHideXp);
        ZRClientConfig.setGoreReduced(currentReducedGore);
        if (this.minecraft.getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(
                new C2SSyncClientPrefsPacket(currentMode.name(), currentPreferZr)
            );
        }
    }

    private Component getModeButtonText() {
        return Component.translatable("zombierool.config.halloween_mode",Component.translatable("zombierool.config.halloween."+currentMode.name().toLowerCase(java.util.Locale.ROOT)));
    }
    private Component getPreferZrButtonText() {
        return Component.translatable("zombierool.config.weapon_models",Component.translatable(currentPreferZr?"zombierool.config.weapon_models.classic":"zombierool.config.weapon_models.tacz"));
    }
    private Component getAnimatePreviewButtonText() {
        return Component.translatable("gui.zombierool.config.animate_preview").append(Component.translatable(currentAnimatePreview?"options.on":"options.off"));
    }
    private Component getHideXpButtonText() {
        return Component.translatable("zombierool.config.hide_xp").append(": ").append(Component.translatable(currentHideXp?"options.on":"options.off"));
    }
    private Component getGoreButtonText() {
        return Component.translatable("zombierool.config.gore",
                Component.translatable(currentReducedGore ? "zombierool.config.gore.reduced" : "zombierool.config.gore.full"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        boolean isNaturalPeriod = HalloweenManager.isNaturalHalloweenPeriod();
        Component periodStatus = Component.translatable("zombierool.config.halloween.natural",Component.translatable(isNaturalPeriod?"zombierool.config.halloween.active":"zombierool.config.halloween.inactive"));
        guiGraphics.drawCenteredString(this.font, periodStatus, this.width / 2, Math.max(8, this.height / 2 - 118) + 172, isNaturalPeriod ? 0x55FF55 : 0xFF5555);

        boolean isEffective = HalloweenManager.isHalloweenPeriod();
        Component effectiveStatus = Component.translatable("zombierool.config.halloween.effective",Component.translatable(isEffective?"zombierool.config.halloween.active":"zombierool.config.halloween.inactive"));
        guiGraphics.drawCenteredString(this.font, effectiveStatus, this.width / 2, Math.max(8, this.height / 2 - 118) + 188, isEffective ? 0x55FF55 : 0xFF5555);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}