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

@Mixin(value = LivingEntityBolt.class)
public abstract class TacZActionTickMixin {
    
    @Shadow(remap = false)
    protected LivingEntity shooter;
    
    @Shadow(remap = false)
    protected ShooterDataHolder data;
    
    @Inject(method = "tickBolt", at = @At("HEAD"), remap = false)
    private void zombierool_accelerateBoltPumpAction(CallbackInfo ci) {
        if (this.shooter == null || this.data == null || !this.data.isBolting) return;
        if (this.data.currentGunItem == null) return;
        
        ItemStack gunItem = this.data.currentGunItem.get();
        if (gunItem == null || gunItem.isEmpty()) return;
        if (!WeaponFacade.isTaczWeapon(gunItem)) return;
        
        boolean hasDoubleTap = this.shooter.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get());
        if (hasDoubleTap) {
            this.data.boltTimestamp -= 25;
        }
    }
}