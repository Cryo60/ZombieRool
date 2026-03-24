package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class UnifiedConfigScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public UnifiedConfigScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 260;
        this.imageHeight = 210;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;

        g.fillGradient(startX, startY, startX + this.imageWidth, startY + this.imageHeight, 0xEE000000, 0xEE222222);
        g.renderOutline(startX, startY, this.imageWidth, this.imageHeight, 0xFFAA00);

        if (!this.menu.slots.isEmpty()) {
            for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
                drawSlotBg(g, startX + slot.x, startY + slot.y);
            }
        }
    }

    protected void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 16, y + 16, 0x55000000);
        g.renderOutline(x - 1, y - 1, 18, 18, 0xFF444444);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, "§l" + this.title.getString(), this.imageWidth / 2, 10, 0xFFAA00);
    }
}