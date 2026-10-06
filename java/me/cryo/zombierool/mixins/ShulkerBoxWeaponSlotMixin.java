package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.ContainerWeaponGuard;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxSlot.class)
public class ShulkerBoxWeaponSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void zombierool$blockMapWeapons(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerWeaponGuard.blocks(((Slot) (Object) this).container, stack)) {
            cir.setReturnValue(false);
        }
    }
}
