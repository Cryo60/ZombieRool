package me.cryo.zombierool.mixins;

import me.cryo.zombierool.client.ClientSniperHandler;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientSniperHandler.class)
public class TacZScopeOverlayMixin {

    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zombierool_taczDisableScope(CallbackInfoReturnable<Boolean> cir) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            if (WeaponFacade.isTaczWeapon(stack)) {
                cir.setReturnValue(false);
            }
        }
    }
}