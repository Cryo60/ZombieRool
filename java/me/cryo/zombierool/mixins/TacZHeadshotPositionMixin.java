package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.HitboxHelper;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Classify the head in the same compensated coordinate frame as the bullet collision. */
@Mixin(value=EntityUtil.class,remap=false)
public abstract class TacZHeadshotPositionMixin {
    @Inject(method="getHitResult",at=@At("RETURN"),cancellable=true,remap=false)
    private static void zombierool_headPosition(Projectile bullet,Entity target,Vec3 start,Vec3 end,CallbackInfoReturnable<EntityKineticBullet.EntityResult> ci){
        var hit=ci.getReturnValue();
        if(hit==null||!(target instanceof AbstractZombieRoolEntity)||target instanceof SpecialEnemyEntity boss&&boss.isBoss())return;
        var fixed=HitboxHelper.getFixedBoundingBox(target,bullet.getOwner());
        Vec3 local=hit.getHitPos().subtract(target.position()).subtract(fixed.getCenter().subtract(target.getBoundingBox().getCenter()));
        var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        var head=type==null?null:HeadShotAABBConfigRead.getAABB(type);
        boolean headshot=head!=null?head.contains(local):local.y>target.getEyeHeight()-.25&&local.y<target.getEyeHeight()+.25;
        ci.setReturnValue(new EntityKineticBullet.EntityResult(target,hit.getHitPos(),headshot));
    }
}
