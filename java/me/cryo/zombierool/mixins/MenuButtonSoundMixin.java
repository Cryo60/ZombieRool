package me.cryo.zombierool.mixins;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractWidget.class)
public abstract class MenuButtonSoundMixin {
 @Inject(method="playDownSound",at=@At("HEAD"),cancellable=true)
 private void zr$sound(SoundManager manager,CallbackInfo ci){
  if(me.cryo.zombierool.client.gui.MenuTheme.applies(net.minecraft.client.Minecraft.getInstance().screen)){
   manager.play(SimpleSoundInstance.forUI(me.cryo.zombierool.init.ZombieroolModSounds.MENU_CLICK.get(),1f,.85f));ci.cancel();
  }
 }
}
