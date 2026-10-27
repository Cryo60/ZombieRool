package me.cryo.zombierool.mixins;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Respect configured mod health without raising vanilla entities' attribute limits. */
@Mixin(LivingEntity.class)
public abstract class SpecialBossHealthMixin {
    @Inject(method="getMaxHealth",at=@At("HEAD"),cancellable=true)
    private void zombierool$bossHealth(CallbackInfoReturnable<Float> cir){
        if((Object)this instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&(boss.isBoss()||boss.isNapalm())){
            float health=boss.uncappedBossHealth();if(health>0)cir.setReturnValue(health);
        }else if((Object)this instanceof me.cryo.zombierool.entity.MapActorEntity actor){
            float health=actor.uncappedMapHealth();if(health>0)cir.setReturnValue(health);
        }
    }
}
