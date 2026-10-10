package me.cryo.zombierool.mixins;

import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ObjectAnimationRunner.class, remap = false)
public class TacZReloadAnimationMixin {
    // TacZ 1.1.7 and 1.1.8 pass a delta in nanoseconds, shared by animation and sound keyframes.
    @ModifyVariable(method = "updateProgress(J)V", at = @At("HEAD"), argsOnly = true, remap = false, require = 1)
    private long zombierool$speedReload(long deltaNs) {
        var player = Minecraft.getInstance().player;
        if (player == null || deltaNs <= 0) return deltaNs;
        var animation = ((ObjectAnimationRunner) (Object) this).getAnimation();
        if (animation.name == null || !animation.name.toLowerCase(java.util.Locale.ROOT).contains("reload")) return deltaNs;
        return Math.round(deltaNs * (1.0 + TacZReloadSpeed.bonus(player, player.getMainHandItem())));
    }
    // Keyframe timing alone does not shorten an OGG sample. Accelerate its playback too.
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = {"update(Z)V", "updateSoundOnly()V"},
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/animation/ObjectAnimationSoundChannel;playSound(DDLnet/minecraft/world/entity/Entity;IFF)V"), index = 5, remap = false, require = 2)
    private float zombierool$speedReloadSound(float pitch) {
        var player = Minecraft.getInstance().player;
        var animation = ((ObjectAnimationRunner)(Object)this).getAnimation();
        if (player != null && animation.name != null && animation.name.toLowerCase(java.util.Locale.ROOT).contains("reload")) {
            return pitch * (float)(1.0 + TacZReloadSpeed.bonus(player, player.getMainHandItem()));
        }
        return pitch;
    }
}
