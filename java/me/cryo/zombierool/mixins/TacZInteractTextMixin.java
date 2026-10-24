package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay",remap=false)
public abstract class TacZInteractTextMixin {
 @Inject(method="render",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$hideInteractionText(net.minecraftforge.client.gui.overlay.ForgeGui gui,net.minecraft.client.gui.GuiGraphics graphics,float partial,int width,int height,CallbackInfo ci){ci.cancel();}
}
