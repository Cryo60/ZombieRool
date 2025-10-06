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

import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.world.inventory.ObstacleDoorManagerMenu;
import net.mcreator.zombierool.network.ObstacleDoorGUIPacket;
import net.mcreator.zombierool.ZombieroolMod;

import java.util.HashMap;

import com.mojang.blaze3d.systems.RenderSystem;

public class ObstacleDoorManagerScreen extends AbstractContainerScreen<ObstacleDoorManagerMenu> {
	private final static HashMap<String, Object> guistate = ObstacleDoorManagerMenu.guistate;
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	EditBox prix_input;
	EditBox canal_input;
	private Button confirmButton;

	public ObstacleDoorManagerScreen(ObstacleDoorManagerMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	private static final ResourceLocation texture = new ResourceLocation("zombierool:textures/screens/obstacle_door_manager.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		prix_input.render(guiGraphics, mouseX, mouseY, partialTicks);
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
		if (prix_input.isFocused())
			return prix_input.keyPressed(key, b, c);
		if (canal_input.isFocused())
			return canal_input.keyPressed(key, b, c);
		return super.keyPressed(key, b, c);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		prix_input.tick();
		canal_input.tick();
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.obstacle_door_manager.label_prix"), 4, 6, -12829636, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.obstacle_door_manager.label_canal"), 6, 76, -12829636, false);
	}

	@Override
	public void onClose() {
		super.onClose();
	}

	private void updatePurchaseButton() {
        if (confirmButton != null) {
            confirmButton.setMessage(entity.isCreative() 
                ? Component.translatable("gui.zombierool.obstacle_door_manager.button_save") 
                : Component.translatable("gui.zombierool.obstacle_door_manager.button_buy"));
        }
    }

	 @Override
    public void init() {
        super.init();
        updatePurchaseButton();
        prix_input = new EditBox(this.font, this.leftPos + 3, this.topPos + 19, 120, 20, 
            Component.translatable("gui.zombierool.obstacle_door_manager.prix_input"));
        prix_input.setMaxLength(32767);
        prix_input.setValue("0");
        guistate.put("text:prix_input", prix_input);
        this.addWidget(this.prix_input);
        
        canal_input = new EditBox(this.font, this.leftPos + 4, this.topPos + 88, 120, 20, 
            Component.translatable("gui.zombierool.obstacle_door_manager.canal_input"));
        canal_input.setMaxLength(32767);
        guistate.put("text:canal_input", canal_input);
        this.addWidget(this.canal_input);

        Component buttonText = entity.isCreative() ? 
            Component.translatable("gui.zombierool.obstacle_door_manager.button_save") : 
            Component.translatable("gui.zombierool.obstacle_door_manager.button_buy");

        confirmButton = Button.builder(buttonText, button -> {
            if (minecraft != null) {
                String prixText = prix_input.getValue();
                String canalText = canal_input.getValue();
                
                try {
                    int prixValue = Integer.parseInt(prixText);
                    NetworkHandler.INSTANCE.sendToServer(
                        new ObstacleDoorGUIPacket(x, y, z, prixValue, canalText, entity.isCreative())
                    );
                    minecraft.setScreen(null);
                } catch (NumberFormatException e) {
                    entity.sendSystemMessage(Component.literal("Erreur: Prix invalide !"));
                }
            }
        }).bounds(this.leftPos + 50, this.topPos + 120, 80, 20).build();
        
        this.addRenderableWidget(confirmButton);
        
        if (entity.isCreative() && minecraft.level.getBlockEntity(new BlockPos(x, y, z)) instanceof ObstacleDoorBlockEntity be) {
            prix_input.setValue(String.valueOf(be.getPrix()));
            canal_input.setValue(be.getCanal());
        }
    }
}