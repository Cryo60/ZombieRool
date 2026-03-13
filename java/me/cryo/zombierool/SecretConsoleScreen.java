package me.cryo.zombierool.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import me.cryo.zombierool.SecretMapManager;

import java.util.ArrayList;
import java.util.List;

public class SecretConsoleScreen extends Screen {
    private final Screen parent;
    private EditBox commandBox;
    private final List<String> logs = new ArrayList<>();

    public SecretConsoleScreen(Screen parent) {
        super(Component.literal("Developer Console"));
        this.parent = parent;
        logs.add("ZombieRool Secret Developer Console");
        logs.add("Type 'map zr_[name]' to load and start.");
        logs.add("Type 'devmap zr_[name]' to load in creative.");
    }

    @Override
    protected void init() {
        int boxWidth = this.width - 20;
        this.commandBox = new EditBox(this.font, 10, this.height - 25, boxWidth, 20, Component.empty()) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    String cmd = this.getValue().trim();
                    if (!cmd.isEmpty()) {
                        executeCommand(cmd);
                        this.setValue("");
                    }
                    return true;
                }
                // FIXED : GLFW_KEY_GRAVE -> GLFW_KEY_GRAVE_ACCENT
                if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT || keyCode == 161 || keyCode == GLFW.GLFW_KEY_BACKSLASH) {
                    onClose();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        this.commandBox.setMaxLength(256);
        this.addWidget(this.commandBox);
        this.setInitialFocus(this.commandBox);
    }

    private void executeCommand(String cmd) {
        logs.add("> " + cmd);
        if (cmd.startsWith("map ")) {
            String mapName = cmd.substring(4).trim();
            SecretMapManager.loadSecretMap(mapName, true, this);
        } else if (cmd.startsWith("devmap ")) {
            String mapName = cmd.substring(7).trim();
            SecretMapManager.loadSecretMap(mapName, false, this);
        } else {
            logs.add("Unknown command.");
        }
    }

    public void addLog(String message) {
        logs.add(message);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        
        int y = this.height - 40;
        for (int i = logs.size() - 1; i >= 0 && y > 10; i--) {
            graphics.drawString(this.font, logs.get(i), 10, y, 0xFFFFFF);
            y -= 12;
        }

        this.commandBox.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}