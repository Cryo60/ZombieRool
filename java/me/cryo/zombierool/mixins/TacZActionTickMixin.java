package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityBolt.class, remap = false)
public abstract class TacZActionTickMixin {

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private LivingEntity shooter;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private ShooterDataHolder data;

    @Inject(method = "tickBolt", at = @At("HEAD"))
    private void zombierool_accelerateBoltPumpAction(CallbackInfo ci) {
        // Sécurité pour s'assurer que le joueur est bien en train d'actionner le verrou
        if (this.shooter == null || this.data == null || !this.data.isBolting) return;
        if (this.data.currentGunItem == null) return;
        
        ItemStack gunItem = this.data.currentGunItem.get();
        if (gunItem == null || gunItem.isEmpty()) return;
        if (!WeaponFacade.isTaczWeapon(gunItem)) return;

        boolean hasDoubleTap = this.shooter.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get());
        if (hasDoubleTap) {
            // Accélère artificiellement la complétion de l'action de pompe/verrou
            // En repoussant le point de départ de l'action de 25ms dans le passé chaque tick.
            this.data.boltTimestamp -= 25;
        }
    }
}