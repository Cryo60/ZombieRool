package me.cryo.zombierool.item;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;

public class NeedlerItem extends WeaponSystem.BaseGunItem {

    public NeedlerItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        if (player.level().isClientSide) return;
        
        boolean isPap = isPackAPunched(stack);
        float damage = getWeaponDamage(stack);
        float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;
        float velocity = isPap ? def.ballistics.velocity * 1.5f : def.ballistics.velocity;
        
        int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;

        for (int i = 0; i < count; i++) {
            Arrow projectile = new Arrow(player.level(), player);
            projectile.setBaseDamage(0); 
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, spread);
            projectile.setSilent(true);
            projectile.setInvisible(true); 
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            projectile.setNoGravity(true); 
            
            CompoundTag nbt = projectile.getPersistentData();
            nbt.putBoolean("zombierool:custom_projectile", true);
            nbt.putBoolean("zombierool:is_needle", true);
            nbt.putFloat("zombierool:damage", damage);
            nbt.putBoolean("zombierool:pap", isPap);

            player.level().addFreshEntity(projectile);
        }
    }
}