package me.cryo.zombierool.mixins;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractButton.class)
public abstract class MenuButtonMixin {
    @Inject(method="renderWidget",at=@At("HEAD"),cancellable=true)
    private void zombierool_button(GuiGraphics g,int x,int y,float partial,CallbackInfo ci){if(me.cryo.zombierool.client.gui.MenuTheme.applies(net.minecraft.client.Minecraft.getInstance().screen)){me.cryo.zombierool.client.gui.MenuTheme.button(g,(AbstractButton)(Object)this);ci.cancel();}}
}
