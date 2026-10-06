package me.cryo.zombierool.mixins;

import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;

@Mixin(value = ObjectAnimationRunner.class, remap = false)
public class TacZReloadAnimationMixin {

    @Shadow(remap = false)
    private ObjectAnimation animation;

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true, remap = false)
    private long zombierool_speedReloadAnimation(long elapsed) {
        if (elapsed <= 0 || this.animation == null || this.animation.name == null) return elapsed;
        if (!this.animation.name.toLowerCase(Locale.ROOT).contains("reload")) return elapsed;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return elapsed;
        double bonus = TacZReloadSpeed.bonus(mc.player, mc.player.getMainHandItem());
        if (bonus <= 0.0) return elapsed;
        return elapsed + (long) (elapsed * bonus);
    }
}
