package me.cryo.zombierool.item;

import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class M1911Item extends WeaponSystem.BaseGunItem implements IHandgunWeapon {

    public M1911Item(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    public boolean isAkimbo(ItemStack stack) {
        return isPackAPunched(stack); 
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
        if (player.level().isClientSide) return;

        boolean isPap = isPackAPunched(stack);
        float damage = getWeaponDamage(stack);

        if (isPap) {
            float spread = def.ballistics.spread * def.pap.spread_mult;
            float velocity = def.ballistics.velocity * 1.5f;

            Arrow projectile = new Arrow(player.level(), player);
            projectile.setBaseDamage(0);

            Vec3 startPos = getVisualMuzzlePos(player, isLeft);
            projectile.setPos(startPos.x, startPos.y, startPos.z);

            float yawOffset = isLeft ? -2.0f : 2.0f;
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0F, velocity, spread);

            projectile.setSilent(true);
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (!def.ballistics.gravity) projectile.setNoGravity(true);

            CompoundTag nbt = projectile.getPersistentData();
            nbt.putBoolean("zombierool:custom_projectile", true);
            nbt.putFloat("zombierool:damage", damage);
            nbt.putBoolean("zombierool:invisible", true);
            nbt.putBoolean("zombierool:pap", true);
            nbt.putString("zombierool:trail_vfx", "FIREBALL");
            
            nbt.putBoolean("zombierool:explosive", true);
            
            float radius = def.explosion != null ? def.explosion.radius + def.pap.explosion_radius_bonus : 2.5f;
            nbt.putFloat("zr_exp_radius", radius);
            nbt.putFloat("zr_exp_dmg_mult", def.explosion != null ? def.explosion.damage_multiplier : 1.0f);
            nbt.putFloat("zr_exp_self_mult", def.explosion != null ? def.explosion.self_damage_multiplier : 0.15f); 
            nbt.putFloat("zr_exp_self_cap", def.explosion != null ? def.explosion.self_damage_cap : 3.0f);
            nbt.putFloat("zr_exp_kb", def.explosion != null ? def.explosion.knockback : 0.0f);
            nbt.putString("zr_exp_vfx", def.explosion != null ? def.explosion.vfx_type : "EXPLOSION");
            nbt.putString("zr_exp_sound", def.explosion != null ? def.explosion.sound : "zombierool:explosion_old");

            player.level().addFreshEntity(projectile);
        } else {
            int penetration = def.stats.penetration;
            BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, def.ballistics.spread, penetration, stack, 0.0f);
        }
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        performShooting(stack, player, charge, false);
    }
}