package me.cryo.zombierool.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.manager.BallisticManager;

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
            float spread = getDynamicSpread(stack, player);
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
            PackAPunchProjectiles.applyCustom(nbt, damage, true);
            PackAPunchProjectiles.applyInvisibleTrail(nbt, "FIREBALL");
            if (def.explosion != null) {
                PackAPunchProjectiles.applyExplosive(nbt, def, true);
            } else {
                PackAPunchProjectiles.applyExplosive(nbt, 2.5f, 1.0f, 0.15f, 3.0f, 0.0f, "EXPLOSION", "zombierool:explosion_old");
            }

            player.level().addFreshEntity(projectile);
        } else {
            float spread = getDynamicSpread(stack, player);
            int penetration = def.stats.penetration;
            BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, spread, penetration, stack, 0.0f);
        }
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        performShooting(stack, player, charge, false);
    }
}