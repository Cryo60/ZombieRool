// [main\java\me\cryo\zombierool\client\gui\MatchRecapScreen.java]
package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.client.career.LocalCareerManager;

public class MatchRecapScreen extends Screen {
    private final int waves, kills, headshots, assists, downs, score;

    public MatchRecapScreen(int waves, int kills, int headshots, int assists, int downs, int score) {
        super(Component.translatable("gui.zombierool.recap.title"));
        this.waves = waves;
        this.kills = kills;
        this.headshots = headshots;
        this.assists = assists;
        this.downs = downs;
        this.score = score;
    }

    @Override
    protected void init() {
        int btnWidth = 120;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.recap.continue"), btn -> {
            this.minecraft.setScreen(null);
        }).bounds(this.width / 2 - btnWidth / 2, this.height - 40, btnWidth, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        g.fillGradient(0, 0, this.width, this.height, 0xEE000000, 0xEE222222);

        g.drawCenteredString(this.font, this.title.getString(), this.width / 2, 20, 0xFFAA00);

        int col1X = this.width / 2 - 140;
        int col2X = this.width / 2 + 20;
        int startY = 60;
        int lineSpacing = 15;

        g.drawString(this.font, Component.translatable("gui.zombierool.recap.match_stats"), col1X, startY, 0x00FFFF);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.waves", waves), col1X, startY + lineSpacing * 1, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.score", score), col1X, startY + lineSpacing * 2, 0x55FF55);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.kills", kills), col1X, startY + lineSpacing * 3, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.headshots", headshots), col1X, startY + lineSpacing * 4, 0xAAAAAA);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.assists", assists), col1X, startY + lineSpacing * 5, 0xFFFFFF);
        g.drawString(this.font, Component.translatable("gui.zombierool.recap.downs", downs), col1X, startY + lineSpacing * 6, 0xFF5555);

        g.drawString(this.font, Component.translatable("gui.zombierool.recap.career"), col2X, startY, 0x00FFFF);
        
        LocalCareerManager.CareerData data = LocalCareerManager.getData();
        String levelText = Component.translatable("gui.zombierool.recap.level", data.currentLevel).getString();
        if (data.prestigeLevel > 0) levelText += " (Prestige " + data.prestigeLevel + ")";
        if (data.currentLevel >= 50) levelText += " [MAX]";
        
        g.drawString(this.font, levelText, col2X, startY + lineSpacing * 1, 0xFFFFFF);

        int xpY = startY + lineSpacing * 2 + 2;
        int barWidth = 100;
        g.fill(col2X, xpY, col2X + barWidth, xpY + 6, 0xFF333333);
        
        if (data.currentLevel < 50) {
            int nextXp = LocalCareerManager.getXpRequiredForLevel(data.currentLevel);
            float progress = (float) data.currentXp / nextXp;
            g.fill(col2X, xpY, col2X + (int)(barWidth * progress), xpY + 6, 0xFF00FF00);
            g.drawString(this.font, data.currentXp + " / " + nextXp + " XP", col2X + barWidth + 5, xpY - 1, 0xAAAAAA);
        } else {
            g.fill(col2X, xpY, col2X + barWidth, xpY + 6, 0xFFFFD700);
        }

        g.drawString(this.font, "ZRF Balance: " + data.zrfBalance, col2X, startY + lineSpacing * 4, 0x55FF55);

        super.render(g, mouseX, mouseY, partialTick);
    }
}