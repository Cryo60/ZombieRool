package me.cryo.zombierool.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import me.cryo.zombierool.SecretMapManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSecretConsoleCommandPacket;
import me.cryo.zombierool.util.MapPackagerUtil;

import java.util.ArrayList;
import java.util.List;

public class SecretConsoleScreen extends Screen {
    private final Screen parent;
    private EditBox commandBox;
    private static final List<Component> logs = new ArrayList<>();
    public static SecretConsoleScreen instance;

    public SecretConsoleScreen(Screen parent) {
        super(Component.translatable("gui.zombierool.console.title"));
        this.parent = parent;
        if (logs.isEmpty()) {
            logs.add(Component.translatable("gui.zombierool.console.welcome1"));
            logs.add(Component.translatable("gui.zombierool.console.welcome2"));
            logs.add(Component.translatable("gui.zombierool.console.welcome3"));
        }
        instance = this;
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
                if (keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT || keyCode == 161 || keyCode == GLFW.GLFW_KEY_BACKSLASH || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    onClose();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        this.commandBox.setMaxLength(1024);
        this.addWidget(this.commandBox);
        this.setInitialFocus(this.commandBox);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_GRAVE_ACCENT || keyCode == 161 || keyCode == GLFW.GLFW_KEY_BACKSLASH) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void executeCommand(String cmd) {
        logs.add(Component.literal("> " + cmd));
        
        if (cmd.startsWith("map ")) {
            if (this.minecraft.level != null) {
                addLog(Component.literal("§cCannot load a map while already in-game!"));
                return;
            }
            String mapName = cmd.substring(4).trim();
            SecretMapManager.loadSecretMap(mapName, true, this);
        } else if (cmd.startsWith("devmap ")) {
            if (this.minecraft.level != null) {
                addLog(Component.literal("§cCannot load a map while already in-game!"));
                return;
            }
            String mapName = cmd.substring(7).trim();
            SecretMapManager.loadSecretMap(mapName, false, this);
        } else if (cmd.startsWith("package ")) {
            if (this.minecraft.level != null) {
                addLog(Component.literal("§cCannot package a map while in-game. Please disconnect to the main menu."));
                return;
            }
            String mapName = cmd.substring(8).trim();
            if (mapName.isEmpty()) {
                addLog(Component.literal("§cUsage: package <map_folder_name>"));
                return;
            }
            addLog(Component.literal("§eStarting packaging for map folder: " + mapName + "..."));
            
            // Exécuter dans un nouveau thread pour ne pas bloquer le menu
            new Thread(() -> {
                boolean success = MapPackagerUtil.zipMapClientSide(mapName);
                this.minecraft.execute(() -> {
                    if (success) {
                        addLog(Component.literal("§aSuccessfully packaged map to zombierool_exports/" + mapName + ".zip"));
                    } else {
                        addLog(Component.literal("§cFailed to package map. Check if the folder exists in saves/."));
                    }
                });
            }).start();
        } else {
            if (this.minecraft.level != null) {
                NetworkHandler.INSTANCE.sendToServer(new C2SSecretConsoleCommandPacket(cmd));
            } else {
                addLog(Component.literal("§cThis command can only be used in-game!"));
            }
        }
    }

    public void addLog(Component message) {
        logs.add(message);
        if (logs.size() > 50) {
            logs.remove(0);
        }
    }

    public static void receiveLog(String message) {
        logs.add(Component.literal(message));
        if (logs.size() > 50) {
            logs.remove(0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        int y = this.height - 40;
        for (int i = logs.size() - 1; i >= 0 && y > 10; i--) {
            graphics.drawString(this.font, logs.get(i), 10, y, 0xFFFFFF);
            y -= 12;
        }
        this.commandBox.render(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        instance = null;
        this.minecraft.setScreen(parent);
    }
}