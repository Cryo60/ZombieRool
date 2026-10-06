package me.cryo.zombierool.item.throwable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import me.cryo.zombierool.item.FlamethrowerItem.VirtualFireManager;

public class Molotov {

    public static class MolotovItem extends ThrowableCore.BaseThrowableItem {
        public MolotovItem() {
            super(new Item.Properties());
        }
    }

    public static class MolotovEntity extends ThrowableCore.BaseThrowableEntity {

        public MolotovEntity(EntityType<? extends MolotovEntity> type, Level level) {
            super(type, level);
        }

        public MolotovEntity(Level level, LivingEntity shooter) {
            super(ZRThrowableRegistry.MOLOTOV_ENTITY.get(), shooter, level);
        }

        @Override
        protected Item getDefaultItem() {
            return ZRThrowableRegistry.MOLOTOV_ITEM.get();
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            super.onHitEntity(result);
            if (!this.level().isClientSide) {
                explode();
            }
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);
            if (!this.level().isClientSide && result.getType() != HitResult.Type.ENTITY) {
                explode();
            }
        }

        private void explode() {
            ServerLevel sl = (ServerLevel) this.level();
            Vec3 pos = this.position();
            
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "molotov_explode"));
            if (sound != null) {
                sl.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            
            float patchDamage = 3.5f;
            
            VirtualFireManager.addFirePatch(sl, pos, this.getOwner() != null ? this.getOwner().getUUID() : null, patchDamage, 140, false);
            VirtualFireManager.addFirePatch(sl, pos.add(1, 0, 0), this.getOwner() != null ? this.getOwner().getUUID() : null, patchDamage, 140, false);
            VirtualFireManager.addFirePatch(sl, pos.add(-1, 0, 0), this.getOwner() != null ? this.getOwner().getUUID() : null, patchDamage, 140, false);
            VirtualFireManager.addFirePatch(sl, pos.add(0, 0, 1), this.getOwner() != null ? this.getOwner().getUUID() : null, patchDamage, 140, false);
            VirtualFireManager.addFirePatch(sl, pos.add(0, 0, -1), this.getOwner() != null ? this.getOwner().getUUID() : null, patchDamage, 140, false);

            this.discard();
        }
    }
}