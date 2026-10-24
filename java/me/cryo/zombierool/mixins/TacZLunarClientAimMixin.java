package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={com.tacz.guns.client.gameplay.LocalPlayerAim.class},remap=false)
public abstract class TacZLunarClientAimMixin {
 @ModifyVariable(method="aim",at=@At("HEAD"),argsOnly=true,remap=false)
 private boolean zr$aim(boolean aiming){return aiming&&!me.cryo.zombierool.client.LunarActionInput.active();}
}
