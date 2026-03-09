package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityReload.class, remap = false)
public class TacZReloadOperatorMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private LivingEntity shooter;

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private ShooterDataHolder data;

    @Inject(method = "tickReloadState", at = @At("HEAD"))
    private void zombierool_accelerateReloadTick(CallbackInfoReturnable<com.tacz.guns.api.entity.ReloadState> cir) {
        if (data.reloadTimestamp == -1 || this.shooter == null) {
            return;
        }

        if (data.currentGunItem == null) return;
        ItemStack gunItem = data.currentGunItem.get();
        if (gunItem == null || gunItem.isEmpty()) return;

        double speedBonus = 0.0;

        // Speed Cola = +50% de vitesse
        if (this.shooter.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
            speedBonus += 0.5;
        }

        // Pack-A-Punch
        if (WeaponFacade.isTaczWeapon(gunItem) && WeaponFacade.isPackAPunched(gunItem)) {
            WeaponSystem.Definition def = WeaponFacade.getDefinition(gunItem);
            if (def != null && def.pap.reload_speed_mult > 0 && def.pap.reload_speed_mult < 1.0f) {
                speedBonus += (1.0f - def.pap.reload_speed_mult);
            }
        }

        if (speedBonus > 0.0) {
            long extraTimePassed = (long) (50.0 * speedBonus); 
            data.reloadTimestamp -= extraTimePassed;
        }
    }
}