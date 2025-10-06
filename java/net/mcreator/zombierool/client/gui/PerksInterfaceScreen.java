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

import net.mcreator.zombierool.PerksManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.BlockPos;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.SavePerksConfigMessage;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity; // Ajout de cet import
import net.mcreator.zombierool.world.inventory.PerksInterfaceMenu;

import java.util.HashMap;

import com.mojang.blaze3d.systems.RenderSystem;

public class PerksInterfaceScreen extends AbstractContainerScreen<PerksInterfaceMenu> {
	private final static HashMap<String, Object> guistate = PerksInterfaceMenu.guistate;
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	EditBox prix_input;
	Button button_confirm;
	Button button_prev;
	Button button_next;
	private final java.util.List<PerksManager.Perk> perksList = new java.util.ArrayList<>(PerksManager.ALL_PERKS.values());
	private int currentPerkIndex = 0;


	public PerksInterfaceScreen(PerksInterfaceMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	private void updateDisplayedPerk() {
		PerksManager.Perk currentPerk = perksList.get(currentPerkIndex);
		
		// Met à jour le texte affiché
		// Tu peux remplacer ça par un champ texte si tu veux afficher dynamiquement
		guistate.put("label:perks_actuel", currentPerk.getName());
		guistate.put("label:description_box", currentPerk.getDescription().getString());
		
		// Facultatif : rafraîchit un champ de texte si tu veux afficher dynamiquement dans l'écran
	}


	private static final ResourceLocation texture = new ResourceLocation("zombierool:textures/screens/perks_interface.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		prix_input.render(guiGraphics, mouseX, mouseY, partialTicks);
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
		return super.keyPressed(key, b, c);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		prix_input.tick();
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		String perksName = (String) guistate.getOrDefault("label:perks_actuel", "...");
		String descriptionRaw = (String) guistate.getOrDefault("label:description_box", "...");
	
		guiGraphics.drawString(this.font, perksName, 55, 50, -12829636, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.zombierool.perks_interface.label_description"), 3, 72, -6724096, false);
	
		// Wrapping de la description
		int descriptionY = 85; // position de départ en Y
		int wrapWidth = 165; // largeur max (à ajuster si besoin)
	
		for (FormattedCharSequence line : this.font.split(Component.literal(descriptionRaw), wrapWidth)) {
		    guiGraphics.drawString(this.font, line, 3, descriptionY, -12829636, false);
		    descriptionY += this.font.lineHeight + 1;
		}
	}


	@Override
	public void onClose() {
		super.onClose();
	}

	@Override
	public void init() {
	    super.init();
	
	    // --- Initialisation du champ prix (chiffres uniquement) ---
	    prix_input = new EditBox(this.font, this.leftPos + 26, this.topPos + 18, 120, 20,
	            Component.translatable("gui.zombierool.perks_interface.prix_input")) {
	        {
	            setSuggestion(Component.translatable("gui.zombierool.perks_interface.prix_input").getString());
	        }
	
	        @Override
	        public void insertText(String text) {
	            super.insertText(text.replaceAll("[^0-9]", ""));
	            if (getValue().isEmpty()) {
	                setSuggestion(Component.translatable("gui.zombierool.perks_interface.prix_input").getString());
	            } else {
	                setSuggestion(null);
	            }
	        }
	
	        @Override
	        public boolean charTyped(char chr, int keyCode) {
	            if (Character.isDigit(chr)) {
	                return super.charTyped(chr, keyCode);
	            }
	            return false;
	        }
	
	        @Override
	        public void moveCursorTo(int pos) {
	            super.moveCursorTo(pos);
	            if (getValue().isEmpty()) {
	                setSuggestion(Component.translatable("gui.zombierool.perks_interface.prix_input").getString());
	            } else {
	                setSuggestion(null);
	            }
	        }
	    };
	    prix_input.setMaxLength(10);
	    guistate.put("text:prix_input", prix_input);
	    this.addWidget(prix_input);
	
	    // --- Boutons ---
	    // Bouton CONFIRM (envoi packet)
		button_confirm = Button.builder(Component.translatable("gui.zombierool.perks_interface.button_confirm"), e -> {
		    String txt = prix_input.getValue();
		    int prix = txt.isEmpty() ? 0 : Integer.parseInt(txt);
		    PerksManager.Perk sel = perksList.get(currentPerkIndex);
		    String perkId = sel.getId(); // Utilisez l'ID interne de la perk
		
		    NetworkHandler.INSTANCE.sendToServer(
		          new SavePerksConfigMessage(new BlockPos(this.x, this.y, this.z), prix, perkId)
		    );
		
		    // Ferme l'écran
		    if (this.minecraft != null) {
		        this.onClose(); // Optionnel : appelle les routines de fermeture
		        this.minecraft.setScreen(null); // Ferme vraiment le GUI côté client
		    }
		}).bounds(this.leftPos + 175, this.topPos + 144, 61, 20).build();

	    guistate.put("button:button_confirm", button_confirm);
	    this.addRenderableWidget(button_confirm);
	
	    // Boutons PREV / NEXT
	    button_prev = Button.builder(Component.translatable("gui.zombierool.perks_interface.button_prev"), e -> {
	        if (currentPerkIndex > 0) {
	            currentPerkIndex--;
	            updateDisplayedPerk();
	        }
	    }).bounds(this.leftPos + 3, this.topPos + 46, 46, 20).build();
	    guistate.put("button:button_prev", button_prev);
	    this.addRenderableWidget(button_prev);
	
	    button_next = Button.builder(Component.translatable("gui.zombierool.perks_interface.button_next"), e -> {
	        if (currentPerkIndex < perksList.size() - 1) {
	            currentPerkIndex++;
	            updateDisplayedPerk();
	        }
	    }).bounds(this.leftPos + 124, this.topPos + 46, 46, 20).build();
	    guistate.put("button:button_next", button_next);
	    this.addRenderableWidget(button_next);
	
	    // --- Pré-chargement ---
	    // Initialisation sûre de currentPerkIndex
	    if (!perksList.isEmpty()) {
	        currentPerkIndex = 0;
	    }
	
	    if (this.minecraft != null && this.minecraft.level != null) {
	        BlockPos pos = new BlockPos(this.x, this.y, this.z);
	        // La classe BlockEntity devrait maintenant être reconnue grâce à l'import
	        BlockEntity be = this.minecraft.level.getBlockEntity(pos);
	        if (be instanceof PerksLowerBlockEntity perksBE) {
	            // Récupère et applique le prix
	            int savedPrice = perksBE.getSavedPrice();
	            prix_input.setValue(String.valueOf(savedPrice));
	            if (!prix_input.getValue().isEmpty() && !"0".equals(prix_input.getValue())) {
	                 prix_input.setSuggestion(null);
	            } else if (prix_input.getValue().isEmpty()) { 
	                 prix_input.setSuggestion(Component.translatable("gui.zombierool.perks_interface.prix_input").getString());
	            }
	
	            // Récupère et sélectionne le perk
	            String savedId = perksBE.getSavedPerkId();
	            if (savedId != null && !savedId.isEmpty()) {
	                for (int i = 0; i < perksList.size(); i++) {
	                    // Correction : Utilisation de getName() formaté au lieu de getId() pour la comparaison
	                    if (perksList.get(i).getId().equals(savedId)) { // Comparez avec l'ID interne
	                        currentPerkIndex = i;
	                        break;
	                    }
	                }
	            }
	        }
	    }
	    // Mettre à jour l'affichage du perk après le chargement ou avec les valeurs par défaut
	    if (!perksList.isEmpty()) {
	        updateDisplayedPerk();
	    }
	}
}