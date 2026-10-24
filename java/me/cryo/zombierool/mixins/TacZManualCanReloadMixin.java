package me.cryo.zombierool.mixins;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.integration.TacZManualReload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Manual actions count the chamber as part of the configured total capacity. */
@Mixin(value=AbstractGunItem.class,remap=false)
public class TacZManualCanReloadMixin {
 @Inject(method="canReload",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$totalCapacity(LivingEntity shooter,ItemStack stack,CallbackInfoReturnable<Boolean> c){
  var gun=(AbstractGunItem)(Object)this;
  if(TacZManualReload.manual(stack)&&gun.useDummyAmmo(stack)&&!gun.useInventoryAmmo(stack))
   c.setReturnValue(WeaponFacade.getAmmo(stack)<WeaponFacade.getMaxAmmo(stack)&&gun.getDummyAmmoAmount(stack)>0);
 }
}
