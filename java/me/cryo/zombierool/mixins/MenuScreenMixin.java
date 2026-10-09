package me.cryo.zombierool.mixins;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import me.cryo.zombierool.client.gui.MenuTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public abstract class MenuScreenMixin {
    @Inject(method="renderWithTooltip",at=@At("HEAD"))
    private void zombierool_base(GuiGraphics g,int x,int y,float partial,CallbackInfo ci){Screen screen=(Screen)(Object)this;if(MenuTheme.applies(screen)&&!(screen instanceof me.cryo.zombierool.client.gui.WaWMainMenuScreen)&&!(screen instanceof me.cryo.zombierool.client.gui.WaWPauseScreen))MenuTheme.begin(g,screen);}
    @Inject(method="renderWithTooltip",at=@At("RETURN"))
    private void zombierool_end(GuiGraphics g,int x,int y,float partial,CallbackInfo ci){MenuTheme.end();}
    @Inject(method={"renderBackground","renderDirtBackground"},at=@At("HEAD"),cancellable=true)
    private void zombierool_background(GuiGraphics g,CallbackInfo ci){Screen screen=(Screen)(Object)this;if(MenuTheme.applies(screen)){MenuTheme.background(g,screen);ci.cancel();}}
}
