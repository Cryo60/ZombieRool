package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={com.tacz.guns.entity.shooter.LivingEntityAim.class},remap=false)
public abstract class TacZLunarAimMixin {
 @Shadow(remap=false) @Final private net.minecraft.world.entity.LivingEntity shooter;
 @ModifyVariable(method="aim",at=@At("HEAD"),argsOnly=true,remap=false)
 private boolean zr$aim(boolean aiming){return aiming&&!me.cryo.zombierool.gameplay.LunarEquipment.busy(shooter);}
}
