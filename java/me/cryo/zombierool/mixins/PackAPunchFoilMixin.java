package me.cryo.zombierool.mixins;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import me.cryo.zombierool.core.system.WeaponFacade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class PackAPunchFoilMixin {
    @Inject(method="isFoil",at=@At("HEAD"),cancellable=true)
    private void zr$upgraded(ItemStack stack,CallbackInfoReturnable<Boolean> result){
        if(WeaponFacade.isTaczWeapon(stack)&&WeaponFacade.isPackAPunched(stack))result.setReturnValue(true);
    }
}
