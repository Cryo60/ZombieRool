package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the actual swept hit point, including pellet spread and body rotation. */
@Mixin(value=EntityKineticBullet.class,remap=false)
public abstract class TacZBossWeakPointMixin {
    @Inject(method="onHitEntity",at=@At("HEAD"),remap=false)
    private void zombierool_bossImpact(TacHitResult hit,Vec3 start,Vec3 end,CallbackInfo ci){
        if(hit.getEntity() instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&boss.isBoss()){
            var bullet=(EntityKineticBullet)(Object)this;
            var fixed=com.tacz.guns.util.HitboxHelper.getFixedBoundingBox(boss,bullet.getOwner());
            boss.recordShotImpact(hit.getLocation().subtract(fixed.getCenter().subtract(boss.getBoundingBox().getCenter())),bullet);
        }
    }
}
