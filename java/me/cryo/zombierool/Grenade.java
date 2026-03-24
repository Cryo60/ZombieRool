package me.cryo.zombierool.item.throwable;

import net.minecraft.core.BlockPos;
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

public class Grenade {

    public static class GrenadeItem extends ThrowableCore.BaseThrowableItem {
        public GrenadeItem() {
            super(new Item.Properties());
        }
    }

    public static class GrenadeEntity extends ThrowableCore.BaseThrowableEntity {
        private int fuse = 100;

        public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
            super(type, level);
        }

        public GrenadeEntity(Level level, LivingEntity shooter, int cookedTicks) {
            super(ZRThrowableRegistry.GRENADE_ENTITY.get(), shooter, level);
            this.fuse = Math.max(10, 100 - cookedTicks);
        }

        public void setFuse(int fuse) {
            this.fuse = fuse;
        }

        public int getFuse() {
            return this.fuse;
        }

        @Override
        protected Item getDefaultItem() {
            return ZRThrowableRegistry.GRENADE_ITEM.get();
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
                    float radius = nbt.getFloat("zr_exp_radius");
                    float damage = nbt.getFloat("zombierool:damage");
                    float dmgMult = nbt.getFloat("zr_exp_dmg_mult");
                    float selfMult = nbt.getFloat("zr_exp_self_mult");
                    float selfCap = nbt.getFloat("zr_exp_self_cap");
                    float kb = nbt.getFloat("zr_exp_kb");
                    String vfx = nbt.getString("zr_exp_vfx");
                    String sound = nbt.getString("zr_exp_sound");
                    boolean isPap = nbt.getBoolean("zombierool:pap");
                    
                    ExplosionControl.doCustomExplosion(
                        this.level(), this.getOwner(), this.position(), damage, radius,
                        dmgMult, selfMult, selfCap, kb, vfx, sound, isPap
                    );
                } else {
                    ExplosionControl.doGrenadeExplosion(
                        this.level(),
                        this.getOwner(),
                        this.position(),
                        250.0f, 
                        2.5f,   
                        5.0f    
                    );
                }
                this.discard();
            }
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            super.onHitEntity(result);
            if (!this.level().isClientSide) {
                result.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 2.0f);
            }
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.scale(-0.3));
            if (!this.level().isClientSide && motion.lengthSqr() > 0.02) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "bounce_metal"));
                if (sound != null) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }

        @Override
        protected void onHitBlock(BlockHitResult result) {
            Vec3 motion = this.getDeltaMovement();
            Direction face = result.getDirection();
            
            if (face.getAxis() == Direction.Axis.X) motion = new Vec3(-motion.x, motion.y, motion.z);
            if (face.getAxis() == Direction.Axis.Y) motion = new Vec3(motion.x, -motion.y, motion.z);
            if (face.getAxis() == Direction.Axis.Z) motion = new Vec3(motion.x, motion.y, -motion.z);

            motion = motion.scale(0.5); 
            this.setDeltaMovement(motion);

            if (!this.level().isClientSide && motion.lengthSqr() > 0.02) {
                BlockState state = this.level().getBlockState(result.getBlockPos());
                SoundType st = state.getSoundType();
                String soundName = "bounce_earth";
                if (state.getFluidState().isSourceOfType(Fluids.WATER) || state.getFluidState().isSourceOfType(Fluids.FLOWING_WATER)) {
                    soundName = "bounce_water";
                } else if (st == SoundType.WOOD || st == SoundType.BAMBOO_WOOD || st == SoundType.SCAFFOLDING) {
                    soundName = "bounce_wood";
                } else if (st == SoundType.METAL || st == SoundType.ANVIL || st == SoundType.CHAIN) {
                    soundName = "bounce_metal";
                } else if (st == SoundType.STONE || st == SoundType.DEEPSLATE || st == SoundType.GLASS || st == SoundType.BASALT) {
                    soundName = "bounce_stone";
                }
                
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", soundName));
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