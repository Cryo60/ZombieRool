package me.cryo.zombierool.item.throwable;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MonkeyBomb {

    public static class MonkeyBombItem extends ThrowableCore.BaseThrowableItem {
        public MonkeyBombItem() {
            super(new Item.Properties());
        }
    }

    public static class MonkeyBombEntity extends ThrowableCore.BaseThrowableEntity {
        
        // Registre global anti-lag pour que les zombies les trouvent instantanément
        public static final Set<MonkeyBombEntity> ACTIVE_MONKEYS = ConcurrentHashMap.newKeySet();
        
        private int fuse = 160; 
        private boolean isLanded = false;
        private boolean songPlayed = false;

        public MonkeyBombEntity(EntityType<? extends MonkeyBombEntity> type, Level level) {
            super(type, level);
        }

        public MonkeyBombEntity(Level level, LivingEntity shooter, int cookedTicks) {
            super(ZRThrowableRegistry.MONKEY_BOMB_ENTITY.get(), shooter, level);
            this.fuse = 160; 
        }

        public boolean isLanded() {
            return this.isLanded;
        }

        @Override
        protected Item getDefaultItem() {
            return ZRThrowableRegistry.MONKEY_BOMB_ITEM.get();
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.level().isClientSide) {
                if (isLanded) {
                    if (this.fuse == 30) {
                        SoundEvent cymb = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "monkey_cymb"));
                        if (cymb != null) {
                            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), cymb, SoundSource.PLAYERS, 2.0f, 1.0f);
                        }
                    }

                    this.fuse--;
                    if (this.fuse <= 0) {
                        explode();
                    }
                }
            }
        }

        private void explode() {
            if (!this.level().isClientSide) {
                ExplosionControl.doGrenadeExplosion(this.level(), this.getOwner(), this.position(), 1000.0f, 5.0f, 8.0f);
                this.discard();
            }
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {}

        @Override
        protected void onHitBlock(BlockHitResult result) {
            Vec3 motion = this.getDeltaMovement();
            Direction face = result.getDirection();
            
            if (face == Direction.UP) {
                this.setDeltaMovement(0, 0, 0);
                
                if (!this.isLanded) {
                    this.isLanded = true;
                    if (!this.level().isClientSide) {
                        ACTIVE_MONKEYS.add(this); // Enregistrement pour les zombies
                        
                        if (!songPlayed) {
                            SoundEvent music = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "monkey_song"));
                            if (music != null) {
                                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), music, SoundSource.PLAYERS, 2.0f, 1.0f);
                            }
                            songPlayed = true;
                        }
                    }
                }
            } else {
                if (face.getAxis() == Direction.Axis.X) motion = new Vec3(-motion.x, motion.y, motion.z);
                if (face.getAxis() == Direction.Axis.Y) motion = new Vec3(motion.x, -motion.y, motion.z);
                if (face.getAxis() == Direction.Axis.Z) motion = new Vec3(motion.x, motion.y, -motion.z);
                this.setDeltaMovement(motion.scale(0.3));
            }
        }

        @Override
        public void remove(RemovalReason reason) {
            ACTIVE_MONKEYS.remove(this); // Nettoyage de la liste pour éviter les fuites de mémoire (Memory Leaks)
            super.remove(reason);
        }

        @Override
        public boolean isPickable() { return false; }
    }
}