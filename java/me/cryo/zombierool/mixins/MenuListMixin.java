package me.cryo.zombierool.mixins;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractSelectionList.class)
public abstract class MenuListMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void zombierool_list(GuiGraphics g,int x,int y,float partial,CallbackInfo ci){if(me.cryo.zombierool.client.gui.MenuTheme.applies(net.minecraft.client.Minecraft.getInstance().screen)){var list=(AbstractSelectionList<?>)(Object)this;list.setRenderBackground(false);list.setRenderTopAndBottom(false);}}
}
