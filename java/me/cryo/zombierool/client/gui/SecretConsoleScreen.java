package me.cryo.zombierool.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import me.cryo.zombierool.gameplay.SecretMapManager;
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
            logs.add(Component.translatable("gui.zombierool.console.welcome4"));
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
        } else if (cmd.equals("help")) {
            printHelp();
        } else if (cmd.equals("copy") || cmd.startsWith("copy ")) {
            executeCopy(cmd.equals("copy") ? "" : cmd.substring(5).trim());
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
        } 
        else {
            if (this.minecraft.level != null) {
                NetworkHandler.INSTANCE.sendToServer(new C2SSecretConsoleCommandPacket(cmd));
            } else if (isInGameCommand(cmd)) {
                addLog(Component.literal("§cThis command can only be used in-game!"));
            } else {
                addLog(Component.translatable("gui.zombierool.console.unknown").withStyle(ChatFormatting.RED));
            }
        }
    }

    private void printHelp() {
        addHelp("gui.zombierool.console.help.ingame", ChatFormatting.GOLD);
        addHelp("gui.zombierool.console.help.lua", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.points", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.wave", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.killall", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.weapon", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.god", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.noclip", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.reload_scripts", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.reload_weapons", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.zombies", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.tp_last", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.menu", ChatFormatting.GOLD);
        addHelp("gui.zombierool.console.help.map", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.devmap", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.package", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.copy", ChatFormatting.GRAY);
        addHelp("gui.zombierool.console.help.copy2", ChatFormatting.GRAY);
    }

    private void addHelp(String key, ChatFormatting color) {
        addLog(Component.translatable(key).withStyle(color));
    }

    private void executeCopy(String rest) {
        if (this.minecraft.level != null) {
            addLog(Component.translatable("gui.zombierool.console.copy.ingame").withStyle(ChatFormatting.RED));
            return;
        }
        List<String> args = splitArgs(rest);
        if (args.size() < 2) {
            addLog(Component.translatable("gui.zombierool.console.copy.usage").withStyle(ChatFormatting.RED));
            return;
        }
        String dest = args.get(args.size() - 1);
        String source = args.size() == 2 ? args.get(0) : String.join(" ", args.subList(0, args.size() - 1));
        addLog(Component.translatable("gui.zombierool.console.copy.start", source, dest).withStyle(ChatFormatting.YELLOW));
        new Thread(() -> {
            SecretMapManager.CopyResult result = SecretMapManager.copyMap(source, dest);
            this.minecraft.execute(() -> {
                if (result.success()) {
                    String key = result.fromAssets ? "gui.zombierool.console.copy.ok_assets" : "gui.zombierool.console.copy.ok";
                    addLog(Component.translatable(key, source, dest).withStyle(ChatFormatting.GREEN));
                } else if (result.arg == null) {
                    addLog(Component.translatable(result.errorKey).withStyle(ChatFormatting.RED));
                } else {
                    addLog(Component.translatable(result.errorKey, result.arg).withStyle(ChatFormatting.RED));
                }
            });
        }).start();
    }

    private static List<String> splitArgs(String raw) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quote = !quote;
                continue;
            }
            if (Character.isWhitespace(c) && !quote) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    private static boolean isInGameCommand(String cmd) {
        return cmd.startsWith("lua ")
                || cmd.startsWith("points ")
                || cmd.startsWith("wave ")
                || cmd.startsWith("weapon ")
                || cmd.equals("cat") || cmd.equals("mrchief")
                || cmd.equals("killall")
                || cmd.equals("god")
                || cmd.equals("noclip")
                || cmd.equals("reload scripts")
                || cmd.equals("reload weapons")
                || cmd.equals("zombies")
                || cmd.equals("tp_last");
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