package me.cryo.zombierool.item.throwable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrowableCore {
    public static abstract class BaseThrowableItem extends Item {
        public BaseThrowableItem(Properties properties) {
            super(properties.stacksTo(1));
        }
    }

    public static abstract class BaseThrowableEntity extends ThrowableItemProjectile {
        public BaseThrowableEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
            super(type, level);
        }

        public BaseThrowableEntity(EntityType<? extends ThrowableItemProjectile> type, double x, double y, double z, Level level) {
            super(type, x, y, z, level);
        }

        public BaseThrowableEntity(EntityType<? extends ThrowableItemProjectile> type, LivingEntity shooter, Level level) {
            super(type, shooter, level);
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);
        }
    }
}