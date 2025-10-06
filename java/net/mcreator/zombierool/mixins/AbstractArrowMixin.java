package net.mcreator.zombierool.mixins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Entity {

    // Nécessaire pour étendre Entity
    private AbstractArrowMixin(EntityType<?> type, net.minecraft.world.level.Level world) {
        super(type, world);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions original = super.getDimensions(pose);
        if (this.getPersistentData().getBoolean("zombierool:small")) {
            // 1% de la hitbox originale
            return EntityDimensions.scalable(original.width * 0.01F, original.height * 0.01F);
        }
        return original;
    }
}