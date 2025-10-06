package net.mcreator.zombierool.client.gui;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.registries.ForgeRegistries;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.SetWallWeaponConfigPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.mcreator.zombierool.world.inventory.WallWeaponManagerMenu;

import java.util.HashMap;

import com.mojang.blaze3d.systems.RenderSystem;

public class WallWeaponManagerScreen extends AbstractContainerScreen<WallWeaponManagerMenu> {
	private final static HashMap<String, Object> guistate = WallWeaponManagerMenu.guistate;
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	EditBox price_input;
	Button button_confirm;
	

	public WallWeaponManagerScreen(WallWeaponManagerMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	private static final ResourceLocation texture = new ResourceLocation("zombierool:textures/screens/wall_weapon_manager.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		price_input.render(guiGraphics, mouseX, mouseY, partialTicks);
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
		if (price_input.isFocused())
			return price_input.keyPressed(key, b, c);
		return super.keyPressed(key, b, c);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		price_input.tick();
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.wall_weapon_manager.label_item"), 3, 5, -12829636, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.wall_weapon_manager.label_prix"), 52, 6, -12829636, false);
	}

	@Override
	public void onClose() {
		super.onClose();
	}

	@Override
	public void init() {
	    super.init();
	
	    // Création de l'EditBox (même code qu'avant)…
	    price_input = new EditBox(this.font, this.leftPos + 51, this.topPos + 22, 120, 20,
	        Component.translatable("gui.zombierool.wall_weapon_manager.price_input")) { /* … */ };
	    price_input.setMaxLength(6);
	    int existing = menu.getConfiguredPrice();
	    if (existing > 0) {
	        price_input.setValue(Integer.toString(existing));
	    }
	    this.addRenderableWidget(price_input);
	
	    // Bouton de confirmation avec message et fermeture
	    button_confirm = Button.builder(Component.literal("Valider"), btn -> {
	        String text = price_input.getValue();
	        int p;
	        try {
	            p = text.isEmpty() ? 0 : Integer.parseInt(text);
	        } catch (NumberFormatException e) {
	            // Message d'erreur en rouge
	            this.minecraft.player.displayClientMessage(
	                Component.literal("Prix invalide !").withStyle(style -> style.withColor(0xFF5555)),
	                false
	            );
	            return;
	        }
	
	        ItemStack stack = menu.getSlot(0).getItem();
	        if (stack.isEmpty()) {
	            this.minecraft.player.displayClientMessage(
	                Component.literal("Aucun item à vendre !").withStyle(style -> style.withColor(0xFF5555)),
	                false
	            );
	            return;
	        }
	
	        ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
	        // Envoi du paquet
	        NetworkHandler.INSTANCE.sendToServer(
	            new SetWallWeaponConfigPacket(new BlockPos(x, y, z), p, itemRL)
	        );
	
	        // Message de succès en vert
	        this.minecraft.player.displayClientMessage(
	            Component.literal("Configuration enregistrée !").withStyle(style -> style.withColor(0x55FF55)),
	            false
	        );
	        // Fermeture du GUI
	        this.minecraft.player.closeContainer();
	    })
	    .bounds(this.leftPos + 107, this.topPos + 53, 61, 20)
	    .build();
	
	    this.addRenderableWidget(button_confirm);
	}
}
