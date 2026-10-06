package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.entity.ReloadState;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntityReload.class)
public class TacZReloadOperatorMixin {
    
    @Shadow(remap = false)
    protected LivingEntity shooter;
    
    @Shadow(remap = false)
    protected ShooterDataHolder data;
    
    @Inject(method = "tickReloadState", at = @At("HEAD"), remap = false)
    private void zombierool_accelerateReloadTick(CallbackInfoReturnable<ReloadState> cir) {
        if (data.reloadTimestamp == -1 || this.shooter == null) {
            return;
        }
        
        if (data.currentGunItem == null) return;
        ItemStack gunItem = data.currentGunItem.get();
        if (gunItem == null || gunItem.isEmpty()) return;
        
        double speedBonus = TacZReloadSpeed.bonus(this.shooter, gunItem);
        
        if (speedBonus > 0.0) {
            long extraTimePassed = (long) (50.0 * speedBonus); 
            data.reloadTimestamp -= extraTimePassed;
        }
    }
}