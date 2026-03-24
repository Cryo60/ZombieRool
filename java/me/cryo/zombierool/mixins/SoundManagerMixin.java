package me.cryo.zombierool.mixins;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = "getSoundEvent", at = @At("HEAD"), cancellable = true)
    private void zombierool_getDynamicSoundEvent(ResourceLocation location, CallbackInfoReturnable<WeighedSoundEvents> cir) {
        if (location.getNamespace().equals("zombierool") && location.getPath().startsWith("dynamic_")) {
            WeighedSoundEvents wse = new WeighedSoundEvents(location, null);
            Sound sound = new Sound(location.toString(), ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
            wse.addSound(sound);
            cir.setReturnValue(wse);
        }
    }
}