package me.cryo.zombierool.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void zombierool_hasLineOfSight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        
        if (thisEntity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity || thisEntity instanceof me.cryo.zombierool.entity.WhiteKnightEntity) {
            Vec3 start = new Vec3(thisEntity.getX(), thisEntity.getEyeY(), thisEntity.getZ());
            Vec3 end = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            
            if (start.distanceTo(end) > 128.0D) {
                cir.setReturnValue(false);
                return;
            }
            
            Boolean hit = net.minecraft.world.level.BlockGetter.traverseBlocks(start, end, null, (ctx, pos) -> {
                BlockState state = thisEntity.level().getBlockState(pos);
                if (state.is(net.minecraft.tags.BlockTags.FENCES) || 
                    state.is(net.minecraft.tags.BlockTags.WALLS) || 
                    state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock ||
                    state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor ||
                    state.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock) {
                    return Boolean.TRUE; 
                }
                VoxelShape shape = state.getCollisionShape(thisEntity.level(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(thisEntity));
                if (!shape.isEmpty()) {
                    if (shape.clip(start, end, pos) != null) return Boolean.TRUE;
                }
                return null;
            }, (ctx) -> Boolean.FALSE);

            if (hit != null && hit) {
                cir.setReturnValue(false);
            }
        }
    }
}