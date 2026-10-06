package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.PrivateDrops;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public class InventoryDropSlotMixin {
    @Shadow
    public Player player;

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void zombierool$pendingDropSlot(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || player.level().isClientSide()) return;
        ItemStack current = ((Inventory) (Object) this).getItem(slot);
        if (current.isEmpty()) return;
        PrivateDrops.markPendingSlot(player, slot);
    }
}
