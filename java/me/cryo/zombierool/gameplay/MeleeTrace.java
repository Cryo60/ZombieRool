package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.*;

/** Barricades allow melee strikes; ordinary walls still stop the trace. */
public final class MeleeTrace {
    public static boolean clear(Level level,Vec3 from,Vec3 to){
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null){
            @Override public VoxelShape getBlockShape(BlockState s,BlockGetter world,BlockPos pos){
                return s.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor
                    ||me.cryo.zombierool.block.system.DefenseWallSystem.bulletsPass(s)
                    ||s.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock
                    ||s.getBlock() instanceof me.cryo.zombierool.block.AbstractTechnicalBlock?Shapes.empty():super.getBlockShape(s,world,pos);
            }
        }).getType()==HitResult.Type.MISS;
    }
    public static boolean visible(LivingEntity from,LivingEntity to){return clear(from.level(),from.getEyePosition(),to.getBoundingBox().getCenter());}
    private MeleeTrace(){}
}
