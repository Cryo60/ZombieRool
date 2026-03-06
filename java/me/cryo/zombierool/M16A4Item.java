package me.cryo.zombierool.item;
import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

public class M16A4Item extends WeaponImplementations.HitscanGunItem {

	public M16A4Item(WeaponSystem.Definition def) {
	    super(def);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
	    ItemStack stack = player.getItemInHand(hand);
	    
	    if (hand == InteractionHand.MAIN_HAND && isPackAPunched(stack)) {
	        if (!player.getCooldowns().isOnCooldown(this)) {
	            if (getAmmo(stack) >= 3 || player.isCreative()) {
	                if (!level.isClientSide) {
                        int multiplier = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get()) ? 2 : 1;
                        for (int m = 0; m < multiplier; m++) {
	                        shootGrenade(stack, player);
                        }
	                    if (!player.isCreative()) {
	                        consumeAmmo(stack, player, 3);
	                    }
	                }
	                player.getCooldowns().addCooldown(this, 40); 
	                return InteractionResultHolder.consume(stack);
	            } else {
	                if (level.isClientSide) {
	                    playSound(level, player, def.sounds.dry);
	                }
	                return InteractionResultHolder.fail(stack);
	            }
	        }
	    }
	    
	    return super.use(level, player, hand);
	}

	private void shootGrenade(ItemStack stack, Player player) {
        Arrow projectile = new Arrow(player.level(), player);
        projectile.setBaseDamage(0);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.0f, 1.0f);
        projectile.setSilent(true);
        projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
        
        CompoundTag nbt = projectile.getPersistentData();
        nbt.putBoolean("zombierool:custom_projectile", true);
        nbt.putFloat("zombierool:damage", getWeaponDamage(stack) * 5.0f);
        nbt.putBoolean("zombierool:invisible", true);
        nbt.putBoolean("zombierool:pap", true);
        nbt.putString("zombierool:trail_vfx", "RPG");

        nbt.putBoolean("zombierool:explosive", true);
        nbt.putFloat("zr_exp_radius", 3.5f);
        nbt.putFloat("zr_exp_dmg_mult", 1.0f);
        nbt.putFloat("zr_exp_self_mult", 0.25f);
        nbt.putFloat("zr_exp_self_cap", 4.0f);
        nbt.putFloat("zr_exp_kb", 0.8f);
        nbt.putString("zr_exp_vfx", "EXPLOSION");
        nbt.putString("zr_exp_sound", "zombierool:explosion_old");

        player.level().addFreshEntity(projectile);

	    SoundEvent fireSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:granade_launcher_fire"));
	    if (fireSound != null) {
	        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), fireSound, SoundSource.PLAYERS, 1.0f, 1.0f);
	    }
	}
}