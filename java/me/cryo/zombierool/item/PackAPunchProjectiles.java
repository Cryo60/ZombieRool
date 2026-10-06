package me.cryo.zombierool.item;

import me.cryo.zombierool.ZombieroolKeys;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.nbt.CompoundTag;

public final class PackAPunchProjectiles {
    private PackAPunchProjectiles() {}

    public static void applyCustom(CompoundTag nbt, float damage, boolean isPap) {
        nbt.putBoolean(ZombieroolKeys.CUSTOM_PROJECTILE, true);
        nbt.putFloat(ZombieroolKeys.DAMAGE, damage);
        nbt.putBoolean(ZombieroolKeys.PAP, isPap);
    }

    public static void applyInvisibleTrail(CompoundTag nbt, String trailVfx) {
        nbt.putBoolean(ZombieroolKeys.INVISIBLE, true);
        nbt.putString(ZombieroolKeys.TRAIL_VFX, trailVfx);
    }

    public static void applyExplosive(CompoundTag nbt, float radius, float damageMultiplier,
                                     float selfMultiplier, float selfCap, float knockback,
                                     String vfx, String sound) {
        nbt.putBoolean(ZombieroolKeys.EXPLOSIVE, true);
        nbt.putFloat(ZombieroolKeys.EXP_RADIUS, radius);
        nbt.putFloat(ZombieroolKeys.EXP_DMG_MULT, damageMultiplier);
        nbt.putFloat(ZombieroolKeys.EXP_SELF_MULT, selfMultiplier);
        nbt.putFloat(ZombieroolKeys.EXP_SELF_CAP, selfCap);
        nbt.putFloat(ZombieroolKeys.EXP_KB, knockback);
        nbt.putString(ZombieroolKeys.EXP_VFX, vfx);
        nbt.putString(ZombieroolKeys.EXP_SOUND, sound);
    }

    public static void applyExplosive(CompoundTag nbt, WeaponSystem.Definition def, boolean isPap) {
        if (def.explosion == null || (def.explosion.pap_only && !isPap)) {
            return;
        }
        float radius = def.explosion.radius + (isPap && def.pap != null ? def.pap.explosion_radius_bonus : 0);
        applyExplosive(
                nbt,
                radius,
                def.explosion.damage_multiplier,
                def.explosion.self_damage_multiplier,
                def.explosion.self_damage_cap,
                def.explosion.knockback,
                def.explosion.vfx_type,
                def.explosion.sound
        );
    }
}
