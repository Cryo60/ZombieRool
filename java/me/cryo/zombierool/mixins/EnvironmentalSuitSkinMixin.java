package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.LunarEquipment;
import me.cryo.zombierool.client.EnvironmentalSuitVisuals;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetic skin substitution also reaches vanilla and TacZ first-person arms. */
@Mixin(AbstractClientPlayer.class)
public abstract class EnvironmentalSuitSkinMixin {
    @Inject(method="getSkinTextureLocation",at=@At("RETURN"),cancellable=true)
    private void zr$suitSkin(CallbackInfoReturnable<ResourceLocation> ci){
        var player=(AbstractClientPlayer)(Object)this;
        ci.setReturnValue(EnvironmentalSuitVisuals.skin(player,ci.getReturnValue()));
    }
}
