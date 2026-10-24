package me.cryo.zombierool.mixins;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
/** Only cosmetic career draws may resolve local assets before server gun data exists. */
@Mixin(value=TimelessAPI.class,remap=false)
public class TacZCareerDisplayMixin {
 @Inject(method="getGunDisplay(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true,remap=false)
 private static void zr$careerDisplay(ItemStack stack,CallbackInfoReturnable<Optional<GunDisplayInstance>> ci){
  if(!me.cryo.zombierool.client.TacZVisuals.isCareerRender())return;
  var gun=IGun.getIGunOrNull(stack);
  if(gun!=null&&TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isEmpty())ci.setReturnValue(TimelessAPI.getGunDisplay(gun.getGunDisplayId(stack),gun.getGunId(stack)));
 }
}
