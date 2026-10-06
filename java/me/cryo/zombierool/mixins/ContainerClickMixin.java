package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.ContainerWeaponGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class ContainerClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void zombierool$beginClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        ContainerWeaponGuard.begin(player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void zombierool$endClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        ContainerWeaponGuard.end();
    }
}
