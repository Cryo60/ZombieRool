package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.ContainerWeaponGuard;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void zombierool$blockPrivateDrops(Container container, ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (item.getPersistentData().hasUUID("zr_drop_owner")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void zombierool$blockMapWeapons(Container source, Container destination, ItemStack stack, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerWeaponGuard.blocks(destination, stack)) {
            cir.setReturnValue(stack);
        }
    }
}
