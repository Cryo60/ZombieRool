package me.cryo.zombierool.mixins;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.client.gui.HalloweenConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin pour ajouter un bouton Halloween dans le menu Options
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    
    protected OptionsScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("RETURN"))
    private void addHalloweenButton(CallbackInfo ci) {
        // Ajout du bouton Halloween centré, après le bouton Done
        this.addRenderableWidget(Button.builder(
            Component.literal("🎃 Halloween Settings"),
            button -> this.minecraft.setScreen(new HalloweenConfigScreen(this)))
            .bounds(this.width / 2 - 100, this.height / 6 + 192, 200, 20)
            .build());
    }
}