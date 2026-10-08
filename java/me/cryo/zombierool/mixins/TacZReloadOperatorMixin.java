package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.entity.ReloadState;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntityReload.class, remap = false)
public class TacZReloadOperatorMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private long zombierool$lastTick = Long.MIN_VALUE;

    @Inject(method = "tickReloadState()Lcom/tacz/guns/api/entity/ReloadState;", at = @At("HEAD"), remap = false, require = 1)
    private void zombierool$speedReload(CallbackInfoReturnable<ReloadState> cir) {
        if (shooter == null || data.reloadTimestamp == -1 || data.currentGunItem == null) return;
        long tick = shooter.level().getGameTime();
        if (zombierool$lastTick == tick) return;
        zombierool$lastTick = tick;
        data.reloadTimestamp -= Math.round(50.0 * TacZReloadSpeed.bonus(shooter, data.currentGunItem.get()));
    }
}