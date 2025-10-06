package net.mcreator.zombierool;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.mcreator.zombierool.init.ZombieroolModSounds;

import java.util.List;

public class ExplosionControl {

    private final Level world;
    private final Entity source;

    public ExplosionControl(Level level, Entity source, double x, double y, double z, float radius) {
        this.world = level;
        this.source = source;

        // Particule explosion
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<Entity> entities = level.getEntities(source, area);

        for (Entity entity : entities) {
            if (entity instanceof Painting || entity instanceof ItemFrame) continue;
            if (entity.getType() == source.getType()) continue;

            entity.hurt(level.damageSources().explosion(source, source), 2.0F);
            entity.setDeltaMovement(0, 0, 0);
            entity.hurtMarked = true;
        }
    }

    // 🔥 Explosion spéciale pour flèche PaP
    public static void arrowExplosion(Level level, Entity source, double x, double y, double z) {
        float radius = 3.0F;
        float damage = 8.0F;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        level.playSound(null, x, y, z, ZombieroolModSounds.EXPLOSION_OLD.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

        AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<Entity> entities = level.getEntities(source, area);

        for (Entity entity : entities) {
            if (entity == source) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof Player) continue;

            entity.hurt(level.damageSources().explosion(source, source), damage);
            entity.setDeltaMovement(0, 0, 0);
            entity.hurtMarked = true;
        }
    }
}
