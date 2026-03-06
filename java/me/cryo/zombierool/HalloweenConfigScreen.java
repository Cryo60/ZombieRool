package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.configuration.HalloweenConfig;
import me.cryo.zombierool.configuration.HalloweenConfig.HalloweenMode;
import me.cryo.zombierool.HalloweenManager;

/**
 * Écran de configuration pour le mode Halloween
 */
public class HalloweenConfigScreen extends Screen {
    
    private final Screen parentScreen;
    private Button halloweenModeButton;
    private HalloweenMode currentMode;
    
    public HalloweenConfigScreen(Screen parentScreen) {
        super(Component.translatable("zombierool.config.halloween.title"));
        this.parentScreen = parentScreen;
        this.currentMode = HalloweenConfig.getHalloweenMode();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Bouton pour cycler entre les modes Halloween
        this.halloweenModeButton = Button.builder(
            getModeButtonText(),
            button -> {
                cycleHalloweenMode();
                button.setMessage(getModeButtonText());
            })
            .bounds(this.width / 2 - 155, this.height / 6 + 24, 310, 20)
            .build();
        
        this.addRenderableWidget(this.halloweenModeButton);
        
        // Bouton "Terminé"
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            button -> this.minecraft.setScreen(parentScreen))
            .bounds(this.width / 2 - 100, this.height / 6 + 168, 200, 20)
            .build());
    }
    
    private void cycleHalloweenMode() {
        // Cycle: AUTO -> FORCE_ON -> FORCE_OFF -> AUTO
        switch (currentMode) {
            case AUTO:
                currentMode = HalloweenMode.FORCE_ON;
                break;
            case FORCE_ON:
                currentMode = HalloweenMode.FORCE_OFF;
                break;
            case FORCE_OFF:
                currentMode = HalloweenMode.AUTO;
                break;
        }
        
        // Sauvegarder localement
        HalloweenConfig.setHalloweenMode(currentMode);
        
        // Envoyer au serveur
        if (this.minecraft.getConnection() != null) {
            me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(
                new me.cryo.zombierool.network.HalloweenConfigSyncPacket(currentMode)
            );
        }
    }
    
    private Component getModeButtonText() {
        String modeText;
        String emoji;
        
        switch (currentMode) {
            case FORCE_ON:
                modeText = "FORCE ON";
                emoji = "🎃";
                break;
            case FORCE_OFF:
                modeText = "FORCE OFF";
                emoji = "❌";
                break;
            case AUTO:
            default:
                modeText = "AUTO";
                emoji = "🔄";
                break;
        }
        
        return Component.literal("Halloween Mode: " + emoji + " " + modeText);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        // Titre
        guiGraphics.drawCenteredString(this.font, this.title, 
            this.width / 2, 20, 0xFFFFFF);
        
        // Description du mode actuel
        String desc1 = getModeDescription1();
        String desc2 = getModeDescription2();
        guiGraphics.drawCenteredString(this.font, desc1, 
            this.width / 2, this.height / 6 + 0, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, desc2, 
            this.width / 2, this.height / 6 + 12, 0xAAAAAA);
        
        // Statut actuel de la période
        boolean isNaturalPeriod = HalloweenManager.isNaturalHalloweenPeriod();
        String periodStatus = "Natural Period: " + 
            (isNaturalPeriod ? "🎃 ACTIVE (Oct 20 - Nov 5)" : "❌ INACTIVE");
        guiGraphics.drawCenteredString(this.font, periodStatus, 
            this.width / 2, this.height / 6 + 60, 
            isNaturalPeriod ? 0xFF8C00 : 0xFF0000);
        
        // Statut effectif (ce qui est vraiment appliqué)
        boolean isEffective = HalloweenManager.isHalloweenPeriod();
        String effectiveStatus = "Effective Status: " + 
            (isEffective ? "🎃 ACTIVE" : "❌ DISABLED");
        guiGraphics.drawCenteredString(this.font, effectiveStatus, 
            this.width / 2, this.height / 6 + 80, 
            isEffective ? 0x00FF00 : 0xFF0000);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private String getModeDescription1() {
        switch (currentMode) {
            case FORCE_ON:
                return "Halloween features are ALWAYS active";
            case FORCE_OFF:
                return "Halloween features are ALWAYS disabled";
            case AUTO:
            default:
                return "Halloween features active during natural period";
        }
    }
    
    private String getModeDescription2() {
        switch (currentMode) {
            case FORCE_ON:
                return "(ignoring calendar date)";
            case FORCE_OFF:
                return "(even during Halloween period)";
            case AUTO:
            default:
                return "(October 20 - November 5)";
        }
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}