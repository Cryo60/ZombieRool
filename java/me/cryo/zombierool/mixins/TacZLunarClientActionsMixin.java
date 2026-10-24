package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={com.tacz.guns.client.gameplay.LocalPlayerReload.class,com.tacz.guns.client.gameplay.LocalPlayerBolt.class,com.tacz.guns.client.gameplay.LocalPlayerInspect.class,com.tacz.guns.client.gameplay.LocalPlayerMelee.class,com.tacz.guns.client.gameplay.LocalPlayerFireSelect.class},remap=false)
public abstract class TacZLunarClientActionsMixin {
 @Inject(method={"reload","bolt","inspect","melee","fireSelect"},at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$block(CallbackInfo ci){if(me.cryo.zombierool.client.LunarActionInput.active())ci.cancel();}
}
