package me.cryo.zombierool.item.throwable;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;

public class Stielhandgranate {

    public static class StielhandgranateItem extends ThrowableCore.BaseThrowableItem {
        public StielhandgranateItem() {
            super(new Item.Properties());
        }
    }

    public static class StielhandgranateEntity extends ThrowableCore.BaseThrowableEntity {
        private int fuse = 100;

        public StielhandgranateEntity(EntityType<? extends StielhandgranateEntity> type, Level level) {
            super(type, level);
        }

        public StielhandgranateEntity(Level level, LivingEntity shooter, int cookedTicks) {
            super(ZRThrowableRegistry.STIELHANDGRANATE_ENTITY.get(), shooter, level);
            this.fuse = Math.max(10, 100 - cookedTicks);
        }

        public void setFuse(int fuse) { this.fuse = fuse; }
        public int getFuse() { return this.fuse; }

        @Override
        protected Item getDefaultItem() {
            return ZRThrowableRegistry.STIELHANDGRANATE_ITEM.get();
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.level().isClientSide) {
                if (this.isInWater() && !this.getPersistentData().getBoolean("WaterBounced")) {
                    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "bounce_water"));
                    if (sound != null) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                    }
                    this.getPersistentData().putBoolean("WaterBounced", true);
                }

                this.fuse--;
                if (this.fuse <= 0) {
                    explode();
                }
            }
        }

        private void explode() {
            if (!this.level().isClientSide) {
                CompoundTag nbt = this.getPersistentData();
                if (nbt.getBoolean("zombierool:custom_projectile") && nbt.getBoolean("zombierool:explosive")) {
                    ExplosionControl.doCustomExplosion(
                        this.level(), this.getOwner(), this.position(), nbt.getFloat("zombierool:damage"), nbt.getFloat("zr_exp_radius"),
                        nbt.getFloat("zr_exp_dmg_mult"), nbt.getFloat("zr_exp_self_mult"), nbt.getFloat("zr_exp_self_cap"), nbt.getFloat("zr_exp_kb"), nbt.getString("zr_exp_vfx"), nbt.getString("zr_exp_sound"), nbt.getBoolean("zombierool:pap")
                    );
                } else {
                    ExplosionControl.doGrenadeExplosion(this.level(), this.getOwner(), this.position(), 300.0f, 3.0f, 6.0f);
                }
                this.discard();
            }
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            super.onHitEntity(result);
            if (!this.level().isClientSide) {
                result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 4.0f);
            }
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.scale(-0.2));
        }

        @Override
        protected void onHitBlock(BlockHitResult result) {
            Vec3 motion = this.getDeltaMovement();
            Direction face = result.getDirection();
            if (face.getAxis() == Direction.Axis.X) motion = new Vec3(-motion.x, motion.y, motion.z);
            if (face.getAxis() == Direction.Axis.Y) motion = new Vec3(motion.x, -motion.y, motion.z);
            if (face.getAxis() == Direction.Axis.Z) motion = new Vec3(motion.x, motion.y, -motion.z);
            motion = motion.scale(0.3); // Stielhandgranate bounce less
            this.setDeltaMovement(motion);

            if (!this.level().isClientSide && motion.lengthSqr() > 0.02) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "bounce_metal"));
                if (sound != null) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }

        @Override
        public boolean isPickable() {
            return false;
        }
    }
}