package me.cryo.zombierool.mixins;
import com.tacz.guns.client.event.FirstPersonRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** TacZ draws its gun inside its own Forge listener, independently of vanilla's hand pass. */
@Mixin(value=FirstPersonRenderEvent.class,remap=false)
public class TacZActionHandsMixin {
 @Inject(method="onRenderHand",at=@At("HEAD"),cancellable=true,remap=false)
 private static void zr$hands(RenderHandEvent event,CallbackInfo ci){
  if(me.cryo.zombierool.client.BrutusUnlockClient.hideHeldItems()||me.cryo.zombierool.client.LunarActionInput.active()){event.setCanceled(true);ci.cancel();}
 }
}
