package me.cryo.zombierool.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private boolean zombierool$isModMob() {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!(entity instanceof net.minecraft.world.entity.Mob)) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && id.getNamespace().equals("zombierool");
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void zombierool$ignoreEnvironmentalDamage(net.minecraft.world.damagesource.DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (zombierool$isModMob() && (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN))) cir.setReturnValue(false);
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void zombierool$ignoreLandingDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (zombierool$isModMob()) cir.setReturnValue(false);
    }

    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void zombierool$breatheUnderwater(CallbackInfoReturnable<Boolean> cir) {
        // Player oxygen is owned by LunarEquipment for water and vacuum alike.
        if ((Object)this instanceof net.minecraft.world.entity.player.Player || zombierool$isModMob()) cir.setReturnValue(true);
    }

    @Inject(method = "getWaterSlowDown", at = @At("HEAD"), cancellable = true)
    private void zombierool$waterDrag(CallbackInfoReturnable<Float> cir) {
        if (zombierool$isModMob()) cir.setReturnValue(.9f);
    }

    @ModifyArg(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V", ordinal = 0), index = 0)
    private float zombierool$waterSpeed(float acceleration) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!zombierool$isModMob() || !entity.isInWater() || !entity.isAlive()) return acceleration;
        // Paired with .9 drag, retain 85% of the mob's configured walking speed.
        return (float)(entity.getSpeed() * (.85*(1-.9)/(1-.6*.91)) * entity.getAttributeValue(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()));
    }

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
