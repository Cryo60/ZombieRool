package me.cryo.zombierool.integration;

import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class TacZReloadSpeed {
    private TacZReloadSpeed() {}

    public static double bonus(LivingEntity shooter, ItemStack gun) {
        if (shooter == null || gun == null || gun.isEmpty()) return 0.0;
        double speedBonus = 0.0;
        if (shooter.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
            speedBonus += 0.5;
        }
        if (WeaponFacade.isTaczWeapon(gun) && WeaponFacade.isPackAPunched(gun)) {
            WeaponSystem.Definition def = WeaponFacade.getDefinition(gun);
            if (def != null && def.pap.reload_speed_mult > 0 && def.pap.reload_speed_mult < 1.0f) {
                speedBonus += (1.0f - def.pap.reload_speed_mult);
            }
        }
        return speedBonus;
    }
}
