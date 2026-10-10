package me.cryo.zombierool.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cryo.zombierool.client.HalloweenManager;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket;
import me.cryo.zombierool.network.packet.C2SSyncClientPrefsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Edits a draft; only Save writes the client TOML and synchronizes preferences. */
public class ZRConfigScreen extends Screen {
    private final Screen parentScreen;
    private final List<Setting> settings = new ArrayList<>();
    private int page;

    private static class Setting {
        final int page;
        final String key;
        final ForgeConfigSpec.ConfigValue<?> value;
        Object draft;
        Setting(int page, String key, ForgeConfigSpec.ConfigValue<?> value) {
            this.page = page;
            this.key = key;
            this.value = value;
            this.draft = value.get();
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        void apply() { ((ForgeConfigSpec.ConfigValue) value).set(draft); }
        Component label() {
            Component state = draft instanceof Boolean enabled
                    ? Component.translatable(enabled ? "options.on" : "options.off")
                    : Component.translatable("zombierool.config.halloween." + ((Enum<?>) draft).name().toLowerCase(Locale.ROOT));
            return Component.translatable(key).append(": ").append(state);
        }
    }

    public ZRConfigScreen(Screen parentScreen) {
        super(Component.translatable("zombierool.config.client.title"));
        this.parentScreen = parentScreen;
        settings.add(new Setting(0, "zombierool.config.setting.halloween", ZRClientConfig.HALLOWEEN_MODE));
        settings.add(new Setting(0, "zombierool.config.setting.classic", ZRClientConfig.PREFER_ZR_WEAPONS));
        settings.add(new Setting(0, "zombierool.config.setting.preview", ZRClientConfig.ANIMATE_WEAPON_PREVIEW));
        settings.add(new Setting(0, "zombierool.config.setting.gore", ZRClientConfig.REDUCED_GORE));
        settings.add(new Setting(1, "zombierool.config.setting.xp", ZRClientConfig.HIDE_XP_NOTIFICATIONS));
        settings.add(new Setting(1, "zombierool.config.setting.counter", ZRClientConfig.SHOW_ENEMIES_REMAINING));
        settings.add(new Setting(2, "zombierool.config.setting.network", ZRClientConfig.ALLOW_NETWORK_REQUESTS));
        settings.add(new Setting(2, "zombierool.config.setting.discord", ZRClientConfig.DISCORD_PRESENCE));
        settings.add(new Setting(2, "zombierool.config.setting.discord_id", ZRClientConfig.DISCORD_APPLICATION_ID));
    }

    @Override
    protected void init() {
        int w = Math.min(310, width - 20);
        int x = (width - w) / 2;
        String[] tabs = {"visual", "hud", "online"};
        for (int i = 0; i < tabs.length; i++) {
            final int selectedPage = i;
            Button tab = Button.builder(Component.translatable("zombierool.config.tab." + tabs[i]), b -> {
                page = selectedPage;
                rebuildWidgets();
            }).bounds(x + i * (w / 3), 38, w / 3 - 2, 20).build();
            tab.active = page != i;
            addRenderableWidget(tab);
        }
        int y = 70;
        for (Setting setting : settings) {
            if (setting.page != page) continue;
            if (setting.draft instanceof String text) {
                EditBox id = new EditBox(font, x, y + 12, w, 20, Component.translatable(setting.key));
                id.setMaxLength(25);
                id.setFilter(s -> s.isEmpty() || s.matches("[0-9]+"));
                id.setValue(text);
                id.setResponder(s -> { setting.draft = s; });
                id.setTooltip(Tooltip.create(Component.translatable(setting.key + ".help")));
                addRenderableWidget(id);
            } else {
                Button option = Button.builder(setting.label(), b -> {
                    if (setting.draft instanceof Boolean enabled) setting.draft = !enabled;
                    else {
                        ZRClientConfig.HalloweenMode mode = (ZRClientConfig.HalloweenMode) setting.draft;
                        var modes = ZRClientConfig.HalloweenMode.values();
                        setting.draft = modes[(mode.ordinal() + 1) % modes.length];
                    }
                    b.setMessage(setting.label());
                }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable(setting.key + ".help"))).build();
                addRenderableWidget(option);
            }
            y += 24;
        }
        Button map = Button.builder(Component.translatable("zombierool.config.map"), b -> {
            NetworkHandler.INSTANCE.sendToServer(new C2SRequestConfigMenuPacket());
        }).bounds(x, height - 50, w / 2 - 2, 20)
                .tooltip(Tooltip.create(Component.translatable("zombierool.config.map.help"))).build();
        map.active = minecraft.player != null && minecraft.player.hasPermissions(2) && minecraft.getConnection() != null;
        addRenderableWidget(map);
        addRenderableWidget(Button.builder(Component.translatable("zombierool.config.reset"), b -> {
            for (Setting setting : settings) setting.draft = setting.value.getDefault();
            rebuildWidgets();
        }).bounds(x + w / 2 + 2, height - 50, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("zombierool.config.save"), b -> save())
                .bounds(x, height - 26, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + w / 2 + 2, height - 26, w / 2 - 2, 20).build());
    }

    private void save() {
        for (Setting setting : settings) setting.apply();
        ZRClientConfig.HAS_ANSWERED_NETWORK_PROMPT.set(true);
        ZRClientConfig.SPEC.save();
        HalloweenManager.updateFromConfig();
        if (minecraft.getConnection() != null && minecraft.player != null) {
            NetworkHandler.INSTANCE.sendToServer(new C2SSyncClientPrefsPacket(
                    ZRClientConfig.getHalloweenMode().name(), ZRClientConfig.prefersZrWeapons()));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        if (page == 2) graphics.drawString(font, Component.translatable("zombierool.config.setting.discord_id"),
                (width - Math.min(310, width - 20)) / 2, 118, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parentScreen); }
}
