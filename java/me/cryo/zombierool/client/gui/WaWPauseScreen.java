package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SRestartLevelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class WaWPauseScreen extends Screen {
    private static int remembered;

    private final List<Entry> entries = new ArrayList<>();
    private int selected;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int[] rowY = new int[0];
    private int[] rowW = new int[0];
    private int[] rowH = new int[0];

    public WaWPauseScreen() {
        super(Component.translatable("gui.zombierool.pause.title"));
    }

    @Override
    protected void init() {
        this.entries.clear();
        this.entries.add(new Entry("gui.zombierool.pause.resume", this::resume));
        this.entries.add(new Entry("gui.zombierool.pause.options", () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
        this.entries.add(new Entry("gui.zombierool.pause.restart", this::restart));
        this.entries.add(new Entry("gui.zombierool.pause.quit", this::quit));
        this.selected = Math.min(remembered, this.entries.size() - 1);
        this.rowY = new int[this.entries.size()];
        this.rowW = new int[this.entries.size()];
        this.rowH = new int[this.entries.size()];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x99101012);
        int bar = Math.max(28, this.height / 11);
        g.fill(0, 0, this.width, bar, 0xFF000000);
        g.fill(0, this.height - bar, this.width, this.height, 0xFF000000);
        g.fill(0, bar, this.width, bar + 1, 0xFFC6C6C6);
        g.fill(0, this.height - bar - 1, this.width, this.height - bar, 0xFFC6C6C6);

        String version = ModList.get().getModContainerById("zombierool")
            .map(c -> "ZombieRool " + c.getModInfo().getVersion())
            .orElse("ZombieRool");
        g.drawString(this.font, version, this.width - 14 - this.font.width(version), Math.max(6, bar / 2 - 4), 0xFFB0B0B0, false);

        float titleScale = this.width < 700 ? 2.0f : 2.6f;
        int right = this.width - Math.max(36, this.width / 22);
        int titleY = bar + Math.max(22, this.height / 18);
        String title = I18n.get("gui.zombierool.pause.title");
        this.drawTrackedRight(g, title, right, titleY, titleScale, 2, 0xFFF4F4F4);

        this.layoutRows(titleY + Math.round(this.font.lineHeight * titleScale) + Math.max(26, this.height / 16));
        if (mouseX != this.lastMouseX || mouseY != this.lastMouseY) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            int hovered = this.hit(mouseX, mouseY);
            if (hovered >= 0 && hovered != this.selected) {
                this.selected = hovered;
                this.play(ZombieroolModSounds.MENU_SLIDER.get(), 0.75F);
            }
        }
        this.drawEntries(g, right);
    }

    private void layoutRows(int startY) {
        float scale = this.itemScale();
        int step = Math.round(this.font.lineHeight * scale) + this.itemGap();
        int y = startY;
        for (int i = 0; i < this.entries.size(); i++) {
            this.rowY[i] = y;
            this.rowH[i] = step;
            this.rowW[i] = this.textWidth(I18n.get(this.entries.get(i).key()), scale, 1);
            y += step;
        }
    }

    private void drawEntries(GuiGraphics g, int right) {
        float scale = this.itemScale();
        int textH = Math.round(this.font.lineHeight * scale);
        for (int i = 0; i < this.entries.size(); i++) {
            if (i == this.selected) {
                int barY = this.rowY[i] - 3;
                int barH = textH + 6;
                int barLeft = right - this.rowW[i] - 22;
                g.fill(barLeft, barY, this.width, barY + barH, 0x55C8C8C8);
            }
            int color = i == this.selected ? 0xFFFFFFFF : 0xFF8E8E8E;
            this.drawTrackedRight(g, I18n.get(this.entries.get(i).key()), right, this.rowY[i], scale, 1, color);
        }
    }

    private int hit(double mouseX, double mouseY) {
        int right = this.width - Math.max(28, this.width / 28);
        for (int i = 0; i < this.entries.size(); i++) {
            int left = Math.min(this.width / 2, right - this.rowW[i] - 24);
            if (mouseX >= left && mouseX <= this.width && mouseY >= this.rowY[i] && mouseY < this.rowY[i] + this.rowH[i]) {
                return i;
            }
        }
        return -1;
    }

    private float itemScale() {
        return this.height < 500 ? 1.25f : 1.65f;
    }

    private int itemGap() {
        return this.height < 500 ? 8 : 12;
    }

    private int drawTrackedRight(GuiGraphics g, String text, int right, int y, float scale, int tracking, int color) {
        int width = this.textWidth(text, scale, tracking);
        this.drawTracked(g, text, right - width, y, scale, tracking, color);
        return width;
    }

    private void drawTracked(GuiGraphics g, String text, int x, int y, float scale, int tracking, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        int cursor = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            g.drawString(this.font, ch, cursor, 0, color, false);
            cursor += this.font.width(ch) + tracking;
            i += Character.charCount(cp);
        }
        pose.popPose();
    }

    private int textWidth(String text, float scale, int tracking) {
        int cursor = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            cursor += this.font.width(ch) + tracking;
            i += Character.charCount(cp);
        }
        return Math.round(cursor * scale);
    }

    private void move(int delta) {
        int next = Math.floorMod(this.selected + delta, this.entries.size());
        if (next != this.selected) {
            this.selected = next;
            remembered = next;
            this.play(ZombieroolModSounds.MENU_SLIDER.get(), 0.75F);
        }
    }

    private void activate() {
        if (this.selected < 0 || this.selected >= this.entries.size()) {
            return;
        }
        remembered = this.selected;
        this.play(ZombieroolModSounds.MENU_CLICK.get(), 0.85F);
        this.entries.get(this.selected).action().run();
    }

    private void resume() {
        this.minecraft.setScreen(null);
    }

    private void restart() {
        this.minecraft.setScreen(null);
        NetworkHandler.INSTANCE.sendToServer(new C2SRestartLevelPacket());
    }

    private void quit() {
        boolean local = this.minecraft.isLocalServer();
        if (this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }
        if (local) {
            this.minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            this.minecraft.clearLevel();
        }
        this.minecraft.setScreen(new TitleScreen());
    }

    private void play(SoundEvent event, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, volume));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            this.move(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            this.move(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            this.activate();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int hovered = this.hit(mouseX, mouseY);
            if (hovered >= 0) {
                this.selected = hovered;
                this.activate();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            this.move(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Entry(String key, Runnable action) {}
}
