package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class WaWMainMenuScreen extends Screen {
    private static final String TITLE = "ZOMBIEROOL";
    private static int remembered;

    private final List<Entry> entries = new ArrayList<>();
    private int selected;
    private int ticks;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int[] rowY = new int[0];
    private int[] rowW = new int[0];
    private int[] rowH = new int[0];

    public WaWMainMenuScreen() {
        super(Component.literal(TITLE));
    }

    @Override
    protected void init() {
        this.entries.clear();
        this.entries.add(new Entry("gui.zombierool.waw.solo", () -> this.minecraft.setScreen(new SelectWorldScreen(this))));
        this.entries.add(new Entry("gui.zombierool.waw.multi", () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));
        this.entries.add(new Entry("gui.zombierool.waw.maps", () -> this.minecraft.setScreen(new MapDownloaderScreen(this))));
        this.entries.add(new Entry("gui.zombierool.waw.career", () -> this.minecraft.setScreen(new CareerScreen(this))));
        if (ModList.get().isLoaded("tacz")) {
            this.entries.add(new Entry("gui.zombierool.waw.tacz", () -> this.minecraft.setScreen(new TacZPackDownloaderScreen(this))));
        }
        this.entries.add(new Entry("gui.zombierool.waw.options", () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
        this.entries.add(new Entry("gui.zombierool.waw.mods", () -> this.minecraft.setScreen(new ModListScreen(this))));
        this.entries.add(new Entry("gui.zombierool.waw.quit", () -> this.minecraft.stop()));
        this.selected = Math.min(remembered, this.entries.size() - 1);
        this.rowY = new int[this.entries.size()];
        this.rowW = new int[this.entries.size()];
        this.rowH = new int[this.entries.size()];
        if (!ZRClientConfig.hasAnsweredNetworkPrompt()) {
            Minecraft.getInstance().tell(() -> {
                if (Minecraft.getInstance().screen == this) {
                    Minecraft.getInstance().setScreen(new NetworkPromptScreen(this));
                }
            });
        } else {
            this.playUi(ZombieroolModSounds.MENU_BUTTON.get(), 0.55F);
        }
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.drawBackdrop(g, partialTick);
        this.drawWordmark(g);
        this.layoutRows();
        if (mouseX != this.lastMouseX || mouseY != this.lastMouseY) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            int hovered = this.hit(mouseX, mouseY);
            if (hovered >= 0 && hovered != this.selected) {
                this.selected = hovered;
                this.playSelect();
            }
        }
        this.drawEntries(g);
        this.drawFooter(g);
    }

    private void drawBackdrop(GuiGraphics g, float partialTick) {
        WaWMenuBackdrop.draw(g, this.width, this.height, this.ticks + partialTick);
    }

    private void drawWordmark(GuiGraphics g) {
        float scale = this.logoScale();
        int x = this.menuX();
        int y = Math.max(24, (int) (this.height * 0.13));
        this.drawTracked(g, TITLE, x + 1, y + 1, scale, 3, 0x40FFFFFF);
        int widthPx = this.drawTracked(g, TITLE, x, y, scale, 3, 0xFFF3F0E8);
        int rule = Math.max(48, (int) (widthPx * 0.42));
        int ruleY = y + Math.round(this.font.lineHeight * scale) + 8;
        g.fill(x, ruleY, x + rule, ruleY + 2, 0xFF9A1C14);
    }

    private void drawEntries(GuiGraphics g) {
        float scale = this.itemScale();
        int x = this.menuX();
        for (int i = 0; i < this.entries.size(); i++) {
            boolean on = i == this.selected;
            String label = I18n.get(this.entries.get(i).key());
            int color = on ? 0xFFF7F4EC : 0xFF6F6A60;
            if (on) {
                this.drawTracked(g, label, x + 1, this.rowY[i] + 1, scale, 1, 0x28FFFFFF);
            }
            this.drawTracked(g, label, x, this.rowY[i], scale, 1, color);
        }
    }

    private void drawFooter(GuiGraphics g) {
        String version = ModList.get().getModContainerById("zombierool")
            .map(c -> "v" + c.getModInfo().getVersion().toString())
            .orElse("");
        if (!version.isEmpty()) {
            g.drawString(this.font, version, this.menuX(), this.height - 16, 0xFF3E3B36, false);
        }
    }

    private void layoutRows() {
        int y = this.menuTop();
        float scale = this.itemScale();
        int step = Math.round(this.font.lineHeight * scale) + this.itemGap();
        for (int i = 0; i < this.entries.size(); i++) {
            String label = I18n.get(this.entries.get(i).key());
            this.rowY[i] = y;
            this.rowH[i] = step;
            this.rowW[i] = this.textWidth(label, scale, 1);
            y += step;
        }
    }

    private int hit(double mouseX, double mouseY) {
        int x = this.menuX();
        for (int i = 0; i < this.entries.size(); i++) {
            if (mouseX >= x - 12 && mouseX <= x + this.rowW[i] + 36
                && mouseY >= this.rowY[i] && mouseY < this.rowY[i] + this.rowH[i]) {
                return i;
            }
        }
        return -1;
    }

    private int menuX() {
        return Math.max(46, this.width / 14);
    }

    private int menuTop() {
        int logoBlock = Math.max(24, (int) (this.height * 0.13)) + Math.round(this.font.lineHeight * this.logoScale()) + 36;
        return Math.max(logoBlock, (int) (this.height * 0.40));
    }

    private float logoScale() {
        float scale = this.width < 720 ? 2.4f : 3.15f;
        int natural = Math.max(1, this.textWidth(TITLE, 1f, 3));
        int maxW = this.width - this.menuX() - 36;
        if (natural * scale > maxW) {
            scale = maxW / (float) natural;
        }
        return Math.max(1.6f, scale);
    }

    private float itemScale() {
        return this.height < 640 ? 1.35f : 1.9f;
    }

    private int itemGap() {
        return this.height < 640 ? 7 : 13;
    }

    private int drawTracked(GuiGraphics g, String text, int x, int y, float scale, int tracking, int color) {
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
        return Math.round(cursor * scale);
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
        if (this.entries.isEmpty()) {
            return;
        }
        int next = Math.floorMod(this.selected + delta, this.entries.size());
        if (next != this.selected) {
            this.selected = next;
            remembered = next;
            this.playSelect();
        }
    }

    private void activate() {
        if (this.selected < 0 || this.selected >= this.entries.size()) {
            return;
        }
        remembered = this.selected;
        String key = this.entries.get(this.selected).key();
        boolean secondary = key.endsWith("options") || key.endsWith("mods");
        this.playUi(secondary ? ZombieroolModSounds.MENU_CLICK_ALT.get() : ZombieroolModSounds.MENU_CLICK.get(), 0.85F);
        this.entries.get(this.selected).action().run();
    }

    private void playSelect() {
        this.playUi(ZombieroolModSounds.MENU_SLIDER.get(), 0.8F);
    }

    private void playUi(SoundEvent event, float volume) {
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
            this.layoutRows();
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
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Entry(String key, Runnable action) {}
}
