package me.cryo.zombierool.item;
import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;

public class GalilItem extends WeaponImplementations.HitscanGunItem {

	public GalilItem(WeaponSystem.Definition def) {
	    super(def);
	}

	@Override
	protected void performShooting(ItemStack stack, Player player, float charge) {
	    if (isPackAPunched(stack)) {
	        if (player.level().isClientSide) return;
	        
	        float damage = getWeaponDamage(stack);
	        float spread = def.ballistics.spread * def.pap.spread_mult;
	        float velocity = 2.5f;

            Arrow projectile = new Arrow(player.level(), player);
            projectile.setBaseDamage(0);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, spread);
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
            nbt.putFloat("zr_exp_radius", 1.5f + def.pap.explosion_radius_bonus);
            nbt.putFloat("zr_exp_dmg_mult", 1.0f);
            nbt.putFloat("zr_exp_self_mult", 0.1f);
            nbt.putFloat("zr_exp_self_cap", 2.0f);
            nbt.putFloat("zr_exp_kb", 0.2f);
            nbt.putString("zr_exp_vfx", "EXPLOSION");
            nbt.putString("zr_exp_sound", "zombierool:explosion_old");

            player.level().addFreshEntity(projectile);
	    } else {
	        super.performShooting(stack, player, charge);
	    }
	}
}