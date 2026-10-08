package me.cryo.zombierool.client;

import me.cryo.zombierool.maptexture.MapTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class TextureKitScreen extends AbstractContainerScreen<MapTextures.TextureKitMenu> {
    private final List<Integer> shown = new ArrayList<>();
    private int selected = -1;
    private int scroll;

    public TextureKitScreen(MapTextures.TextureKitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 460;
        this.imageHeight = 332;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        shown.clear();
        for (int i = 0; i < menu.names.size() && i < MapTextures.SLOTS; i++) {
            String name = menu.names.get(i);
            if (name != null && !name.isEmpty()) shown.add(i);
        }
        if (!shown.isEmpty() && (selected < 0 || !shown.contains(selected))) selected = shown.get(0);
        addRenderableWidget(Button.builder(Component.translatable("message.zombierool.maptex.apply"), button -> MapTextureClient.rebuildIfChanged())
                .bounds(leftPos + imageWidth - 158, topPos + 6, 150, 16).build());
        if (MapTextureClient.worldRoot() != null) addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.texture_editor"), button -> minecraft.setScreen(new TexturePixelEditor(this, selected >= 0 ? menu.names.get(selected) : "new_texture"))).bounds(leftPos + 196, topPos + 158, 246, 20).build());
        if (shown.isEmpty()) return;

        int y = topPos + 58;
        int first = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - 6)));
        scroll = first;
        for (int row = 0; row < 6 && first + row < shown.size(); row++) {
            int slot = shown.get(first + row);
            String name = menu.names.get(slot);
            Component label = Component.literal(slot == selected ? "> " + name : "  " + name);
            addRenderableWidget(Button.builder(label, button -> select(slot))
                    .bounds(leftPos + 10, y + row * 20, 168, 18).build());
        }
        for (int shape = 0; shape < MapTextures.SHAPES.length; shape++) {
            int id = shape;
            int col = shape % 2;
            int row = shape / 2;
            addRenderableWidget(Button.builder(Component.translatable("shape.zombierool." + MapTextures.SHAPES[shape]), button -> give(id))
                    .bounds(leftPos + 196 + col * 126, y + row * 24, 120, 20).build());
        }
        String current = currentSound();
        int soundY = topPos + 214;
        for (int i = 0; i < MapTextures.SOUND_IDS.length; i++) {
            int index = i;
            String id = MapTextures.SOUND_IDS[i];
            Component name = Component.translatable("sound.zombierool." + id);
            Component label = id.equals(current) ? Component.literal("> ").append(name) : name;
            int col = i % 6;
            int row = i / 6;
            addRenderableWidget(Button.builder(label, button -> pickSound(index))
                    .bounds(leftPos + 10 + col * 74, soundY + row * 18, 72, 16).build());
        }
    }

    private String currentSound() {
        if (selected < 0 || selected >= menu.sounds.size() || menu.sounds.get(selected) == null || menu.sounds.get(selected).isEmpty()) {
            return "stone";
        }
        return menu.sounds.get(selected);
    }

    private void select(int slot) {
        if (selected == slot) return;
        selected = slot;
        clearWidgets();
        init();
    }

    private void pickSound(int soundIndex) {
        if (selected < 0 || minecraft == null || minecraft.gameMode == null) return;
        String id = MapTextures.SOUND_IDS[soundIndex];
        while (menu.sounds.size() <= selected) menu.sounds.add("stone");
        menu.sounds.set(selected, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MapTextures.SOUND_BUTTON + selected * MapTextures.SOUND_IDS.length + soundIndex);
        clearWidgets();
        init();
    }

    private void give(int shape) {
        if (selected < 0 || minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected * MapTextures.SHAPES.length + shape);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (shown.size() > 6 && mouseX < leftPos + 186) {
            scroll = Math.max(0, Math.min(shown.size() - 6, scroll - (int) Math.signum(delta)));
            clearWidgets();
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0101010);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 4, 0xFF6B2D3C);
        if (shown.isEmpty()) return;
        graphics.fill(leftPos + 8, topPos + 48, leftPos + 182, topPos + 182, 0xFF1A1A1A);
        graphics.fill(leftPos + 190, topPos + 48, leftPos + imageWidth - 8, topPos + 182, 0xFF1A1A1A);
        graphics.fill(leftPos + 8, topPos + 190, leftPos + imageWidth - 8, topPos + imageHeight - 8, 0xFF1A1A1A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + 8, topPos + 8, 0xFFFFFF, false);
        if (shown.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                graphics.drawString(font, Component.translatable("message.zombierool.maptex.help" + (i + 1)), leftPos + 8, topPos + 36 + i * 12, 0xCCCCCC, false);
            }
            return;
        }
        String texture = selected >= 0 && selected < menu.names.size() ? menu.names.get(selected) : "";
        graphics.drawString(font, Component.translatable("message.zombierool.maptex.status", texture, Component.translatable("sound.zombierool." + currentSound())), leftPos + 8, topPos + 24, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("message.zombierool.maptex.texture"), leftPos + 12, topPos + 38, 0xE0B080, false);
        graphics.drawString(font, Component.translatable("message.zombierool.maptex.shape"), leftPos + 196, topPos + 38, 0xE0B080, false);
        graphics.drawString(font, Component.translatable("message.zombierool.maptex.sound_for"), leftPos + 12, topPos + 196, 0xE0B080, false);
    }

    @Override
    public void removed() {
        super.removed();
        MapTextureClient.rebuildIfChanged();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
