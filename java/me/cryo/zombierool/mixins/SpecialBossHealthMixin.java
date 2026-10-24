package me.cryo.zombierool.mixins;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The vanilla 1024 HP ceiling must not truncate wave-scaled special boss health. */
@Mixin(LivingEntity.class)
public abstract class SpecialBossHealthMixin {
    @Inject(method="getMaxHealth",at=@At("HEAD"),cancellable=true)
    private void zombierool$bossHealth(CallbackInfoReturnable<Float> cir){
        if((Object)this instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&(boss.isBoss()||boss.isNapalm())){
            float health=boss.uncappedBossHealth();if(health>0)cir.setReturnValue(health);
        }
    }
}
