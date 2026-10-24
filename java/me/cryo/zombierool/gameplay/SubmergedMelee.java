package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Reach a swimmer directly above, while solid floors still separate storeys. */
public final class SubmergedMelee {
    private SubmergedMelee() {}
    public static boolean canReach(Mob mob, LivingEntity target) {
        if(!(target instanceof Player player)||!mob.isInWater()||!player.isInWater())return false;
        double dy=player.getY()-mob.getEyeY(),dx=player.getX()-mob.getX(),dz=player.getZ()-mob.getZ();
        if(dy<-.5||dy>2.5||dx*dx+dz*dz>2.25)return false;
        Vec3 aim=player.position().add(0,.3,0);
        return mob.level().clip(new ClipContext(mob.getEyePosition(),aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS;
    }
}
