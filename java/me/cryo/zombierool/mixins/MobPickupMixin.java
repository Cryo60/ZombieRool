package me.cryo.zombierool.mixins;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mob.class)
public abstract class MobPickupMixin {
    @Shadow
    protected abstract void pickUpItem(ItemEntity itemEntity);

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;pickUpItem(Lnet/minecraft/world/entity/item/ItemEntity;)V"))
    private void zombierool$skipPrivatePickup(Mob instance, ItemEntity item) {
        if (item.getPersistentData().hasUUID("zr_drop_owner")) {
            return;
        }
        ((MobPickupMixin) (Object) instance).pickUpItem(item);
    }
}
