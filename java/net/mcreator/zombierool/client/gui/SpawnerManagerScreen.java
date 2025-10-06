package net.mcreator.zombierool.client.gui;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.SetSpawnerChannelMessage;

import net.mcreator.zombierool.world.inventory.SpawnerManagerMenu;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;

import com.mojang.blaze3d.systems.RenderSystem;

public class SpawnerManagerScreen extends AbstractContainerScreen<SpawnerManagerMenu> {
    private final static HashMap<String, Object> guistate = SpawnerManagerMenu.guistate;
    private final Level world;
    private final int x, y, z;
    private final Player entity;
    EditBox canal_input;
    Button button_confirm;

    public SpawnerManagerScreen(SpawnerManagerMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
        this.world = container.world;
        this.x = container.x;
        this.y = container.y;
        this.z = container.z;
        this.entity = container.entity;
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    private static final ResourceLocation texture = new ResourceLocation("zombierool:textures/screens/spawner_manager.png");

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        canal_input.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(texture, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
        RenderSystem.disableBlend();
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.minecraft.player.closeContainer();
            return true;
        }
        if (canal_input.isFocused())
            return canal_input.keyPressed(key, b, c);
        return super.keyPressed(key, b, c);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        canal_input.tick();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.spawner_manager.label_canal"), 5, 11, -12829636, false);
        
        BlockEntity blockEntity = world.getBlockEntity(new BlockPos(x, y, z));
        if (blockEntity instanceof SpawnerZombieBlockEntity) {
            int currentChannel = ((SpawnerZombieBlockEntity) blockEntity).getCanal();
            if (currentChannel > 0) {
                guiGraphics.drawString(this.font, Component.literal("Current: " + currentChannel), 5, 40, -12829636, false);
            }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
	public void init() {
	    super.init();
	
	    // Récupérer la valeur actuelle du canal
	    int currentChannel = 0;
	    BlockEntity blockEntity = world.getBlockEntity(new BlockPos(x, y, z));
	    if (blockEntity instanceof SpawnerZombieBlockEntity spawner) {
		    currentChannel = spawner.getCanal();
		} else if (blockEntity instanceof SpawnerDogBlockEntity spawner) {
		    currentChannel = spawner.getCanal();
		} else if (blockEntity instanceof SpawnerCrawlerBlockEntity spawner) {
		    currentChannel = spawner.getCanal();
		}

	
	    // Créer le champ texte avec la valeur actuelle
	    canal_input = new EditBox(this.font, this.leftPos + 5, this.topPos + 28, 120, 20,
	        Component.translatable("gui.zombierool.spawner_manager.canal_input")) {
	        {
	            setSuggestion(Component.translatable("gui.zombierool.spawner_manager.canal_input").getString());
	        }
	
	        @Override
	        public void insertText(String text) {
	            super.insertText(text);
	            if (getValue().isEmpty())
	                setSuggestion(Component.translatable("gui.zombierool.spawner_manager.canal_input").getString());
	            else
	                setSuggestion(null);
	        }
	
	        @Override
	        public void moveCursorTo(int pos) {
	            super.moveCursorTo(pos);
	            if (getValue().isEmpty())
	                setSuggestion(Component.translatable("gui.zombierool.spawner_manager.canal_input").getString());
	            else
	                setSuggestion(null);
	        }
	    };
	    canal_input.setMaxLength(32767);
	
	    // **Ici** : remplir le champ texte avec la valeur actuelle
	    if (currentChannel > 0) {
	        canal_input.setValue(Integer.toString(currentChannel));
	        canal_input.setSuggestion(null);
	    }
	
	    guistate.put("text:canal_input", canal_input);
	    this.addWidget(this.canal_input);
	
	    button_confirm = Button.builder(Component.translatable("gui.zombierool.spawner_manager.button_confirm"), e -> {
	        try {
	            int channel = Integer.parseInt(canal_input.getValue());
	            NetworkHandler.INSTANCE.sendToServer(new SetSpawnerChannelMessage(new BlockPos(x, y, z), channel));
	            this.minecraft.player.closeContainer();
	        } catch (NumberFormatException ex) {
	            canal_input.setValue("");
	            canal_input.setSuggestion(Component.translatable("gui.zombierool.spawner_manager.canal_input").getString());
	        }
	    }).bounds(this.leftPos + 6, this.topPos + 78, 61, 20).build();
	
	    guistate.put("button:button_confirm", button_confirm);
	    this.addRenderableWidget(button_confirm);
	}
}