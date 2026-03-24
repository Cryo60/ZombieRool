package me.cryo.zombierool.mixins;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.client.gui.ZRConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addZombieRoolButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙ ZombieRool Options"),
            button -> this.minecraft.setScreen(new ZRConfigScreen(this)))
            .bounds(this.width / 2 - 100, this.height / 6 + 192, 200, 20)
            .build());
    }
}