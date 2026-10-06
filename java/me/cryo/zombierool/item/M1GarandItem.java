package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class M1GarandItem extends WeaponImplementations.HitscanGunItem {
	public M1GarandItem(WeaponSystem.Definition def) {
	    super(def);
	}
	
	@Override
	protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
	    // On exécute le tir normal
	    boolean success = super.executeShot(stack, player, charge, isLeft);
	
	    // Si le tir a réussi, on vérifie l'état des munitions
	    if (success && !player.level().isClientSide) {
	        int currentAmmo = isLeft ? getAmmoLeft(stack) : getAmmo(stack);
	        
	        // Le "Ping" caractéristique du M1 Garand se joue quand la dernière balle est tirée
	        if (currentAmmo == 0) {
	            player.level().playSound(
	                null, 
	                player.getX(), 
	                player.getY(), 
	                player.getZ(), 
	                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "m1_garand_ping")), 
	                SoundSource.PLAYERS, 
	                1.0f, 
	                1.0f
	            );
	        }
	    }
	    
	    return success;
	}
}
