package me.cryo.zombierool.mixins;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    private float currentEyeHeight;

    @Inject(method = "setup", at = @At("HEAD"))
    private void smoothEyeHeight(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (entity != null && !detached) { 
            float targetHeight = entity.getEyeHeight();
            if (Math.abs(currentEyeHeight - targetHeight) > 1.5f) {
                currentEyeHeight = targetHeight;
            } else {
                currentEyeHeight += (targetHeight - currentEyeHeight) * 0.05f;
            }
            this.eyeHeight = currentEyeHeight;
            this.eyeHeightOld = currentEyeHeight;
        }
    }
}