package me.cryo.zombierool.mixins;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractSliderButton.class)
public abstract class MenuSliderMixin {
    @Shadow protected double value;
    @Inject(method="renderWidget",at=@At("HEAD"),cancellable=true)
    private void zombierool_slider(GuiGraphics g,int x,int y,float partial,CallbackInfo ci){if(me.cryo.zombierool.client.gui.MenuTheme.applies(net.minecraft.client.Minecraft.getInstance().screen)){var b=(AbstractSliderButton)(Object)this;me.cryo.zombierool.client.gui.MenuTheme.button(g,b);int pos=b.getX()+4+(int)((b.getWidth()-8)*value);g.fill(b.getX()+4,b.getY()+b.getHeight()-3,pos,b.getY()+b.getHeight()-2,0xFF9A1C14);ci.cancel();}}
}
