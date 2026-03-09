package me.cryo.zombierool.mixins;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.api.item.gun.FireMode;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunData.class, remap = false)
public abstract class TacZShootIntervalMixin {

    @Shadow
    public abstract Bolt getBolt();

    @Inject(method = "getShootInterval", at = @At("RETURN"), cancellable = true)
    private void zombierool_modifyShootInterval(LivingEntity shooter, FireMode fireMode, ItemStack gunStack, CallbackInfoReturnable<Long> cir) {
        long interval = cir.getReturnValue();

        // Élimine de manière générale la latence "fantôme" avant l'action de verrou/pompe.
        // L'arme entrera immédiatement dans son animation sans attendre le cooldown de tir.
        if (this.getBolt() == Bolt.MANUAL_ACTION) {
            interval = 0L; 
        }

        // Applique la réduction de cooldown globale si le joueur a l'atout Double Tap
        if (shooter != null && shooter.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
            interval = (long) (interval * 0.67);
        }

        cir.setReturnValue(interval);
    }
}