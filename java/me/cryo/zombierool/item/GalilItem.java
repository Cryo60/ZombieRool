package me.cryo.zombierool.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.system.WeaponImplementations;

public class GalilItem extends WeaponImplementations.HitscanGunItem {

	public GalilItem(WeaponSystem.Definition def) {
	    super(def);
	}

	@Override
	protected void performShooting(ItemStack stack, Player player, float charge) {
	    if (isPackAPunched(stack)) {
	        if (player.level().isClientSide) return;

	        float damage = getWeaponDamage(stack);
	        float spread = getDynamicSpread(stack, player);
	        float velocity = 2.5f;

            Arrow projectile = new Arrow(player.level(), player);
            projectile.setBaseDamage(0);
            
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, spread);
            projectile.setSilent(true);
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            if (!def.ballistics.gravity) projectile.setNoGravity(true);

            CompoundTag nbt = projectile.getPersistentData();
            PackAPunchProjectiles.applyCustom(nbt, damage, true);
            PackAPunchProjectiles.applyInvisibleTrail(nbt, "FIREBALL");
            PackAPunchProjectiles.applyExplosive(nbt, 1.5f + def.pap.explosion_radius_bonus, 1.0f, 0.1f, 2.0f, 0.2f, "EXPLOSION", "zombierool:explosion_old");

            player.level().addFreshEntity(projectile);
	    } else {
	        super.performShooting(stack, player, charge);
	    }
	}
}