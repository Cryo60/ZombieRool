package me.cryo.zombierool.client.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.apache.commons.io.FileUtils;
import me.cryo.zombierool.init.ZombieroolModSounds;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CopyWorldSelectionScreen extends Screen {
    private final Screen parent;
    private WorldList worldList;
    private Button playButton;
    private Button backButton;

    public CopyWorldSelectionScreen(Screen parent) {
        super(Component.translatable("gui.zombierool.copy_world.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int listTop = 40;
        int listBottom = this.height - 50;

        this.worldList = new WorldList(this.minecraft, this.width, listBottom - listTop, listTop, listBottom, 24);
        this.addWidget(this.worldList);

        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] worldFolders = savesDir.listFiles(File::isDirectory);
            if (worldFolders != null) {
                List<String> worldNames = Arrays.stream(worldFolders)
                    .filter(folder -> new File(folder, "level.dat").exists())
                    .sorted(Comparator.comparingLong(File::lastModified).reversed())
                    .map(File::getName)
                    .collect(Collectors.toList());

                for (String name : worldNames) {
                    this.worldList.addWorldEntry(new WorldEntry(name));
                }
            }
        }

        int buttonWidth = 100;
        int spacing = 10;
        int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;
        int buttonY = this.height - 35;

        this.playButton = Button.builder(Component.translatable("gui.zombierool.copy_world.play"), btn -> {
            playSound();
            playCopiedWorld(this.worldList.getSelected().worldName);
        }).bounds(startX, buttonY, buttonWidth, 20).build();
        this.playButton.active = false;

        this.backButton = Button.builder(Component.translatable("gui.zombierool.downloader.back"), btn -> {
            playSound();
            this.minecraft.setScreen(parent);
        }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, 20).build();

        this.addRenderableWidget(this.playButton);
        this.addRenderableWidget(this.backButton);
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    private void playCopiedWorld(String worldName) {
        this.minecraft.setScreen(new Screen(Component.translatable("gui.zombierool.copy_world.copying")) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                this.renderBackground(g);
                g.drawCenteredString(font, Component.translatable("gui.zombierool.copy_world.copying_desc"), width / 2, height / 2, 0xFFFFFF);
            }
            @Override
            public boolean shouldCloseOnEsc() {
                return false;
            }
        });

        new Thread(() -> {
            try {
                File source = new File(Minecraft.getInstance().gameDirectory, "saves/" + worldName);
                File target = new File(Minecraft.getInstance().gameDirectory, "saves/temp_zr_copy");

                if (target.exists()) {
                    FileUtils.deleteDirectory(target);
                }

                FileUtils.copyDirectory(source, target);

                File levelDat = new File(target, "level.dat");
                if (levelDat.exists()) {
                    CompoundTag root = NbtIo.readCompressed(levelDat);
                    CompoundTag data = root.getCompound("Data");
                    data.putString("LevelName", "temp_zr_copy");
                    NbtIo.writeCompressed(root, levelDat);
                }

                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().createWorldOpenFlows().loadLevel(parent, "temp_zr_copy");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(parent));
            }
        }).start();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.worldList.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private class WorldList extends ObjectSelectionList<WorldEntry> {
        public WorldList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }
        public void addWorldEntry(WorldEntry entry) {
            this.addEntry(entry);
        }
        @Override
        public void setSelected(WorldEntry entry) {
            super.setSelected(entry);
            playButton.active = entry != null;
        }
        @Override
        public int getRowWidth() {
            return 250;
        }
        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 130;
        }
    }

    private class WorldEntry extends ObjectSelectionList.Entry<WorldEntry> {
        public final String worldName;
        public WorldEntry(String worldName) {
            this.worldName = worldName;
        }
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            graphics.drawString(font, worldName, left + 5, top + 5, 0xFFFFFF);
        }
        @Override
        public Component getNarration() {
            return Component.literal(worldName);
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            worldList.setSelected(this);
            return true;
        }
    }
}