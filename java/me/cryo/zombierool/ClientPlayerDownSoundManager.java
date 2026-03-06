package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import me.cryo.zombierool.init.ZombieroolModSounds;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
	public class ClientPlayerDownSoundManager {
	@Nullable
	private static SoundInstance currentLastStandSound = null;
	@Nullable
	private static SoundInstance currentScreamingEasterEggSound = null;
	public static void startLastStandSound() {
	    Minecraft mc = Minecraft.getInstance();
	    if (mc.player == null) return;
	
	    if (currentLastStandSound != null && mc.getSoundManager().isActive(currentLastStandSound)) {
	        return;
	    }
	
	    SoundEvent soundEvent = ZombieroolModSounds.LASTSTAND_LP.get();
	    if (soundEvent == null) return;
	
	    currentLastStandSound = new SimpleSoundInstance(
	        soundEvent.getLocation(),
	        SoundSource.AMBIENT, 
	        1.0F, 
	        1.0F, 
	        RandomSource.create(), 
	        true, 
	        0, 
	        SoundInstance.Attenuation.NONE, 
	        0.0D, 0.0D, 0.0D, 
	        true 
	    );
	    mc.getSoundManager().play(currentLastStandSound);
	
	    if (mc.player.getRandom().nextInt(50) == 0) {
	        SoundEvent screamingSoundEvent = ZombieroolModSounds.SCREAMING.get();
	        if (screamingSoundEvent != null) {
	            if (currentScreamingEasterEggSound != null && mc.getSoundManager().isActive(currentScreamingEasterEggSound)) {
	                mc.getSoundManager().stop(currentScreamingEasterEggSound);
	            }
	            currentScreamingEasterEggSound = new SimpleSoundInstance(
	                screamingSoundEvent.getLocation(),
	                SoundSource.AMBIENT,
	                0.05F, 
	                1.0F,
	                RandomSource.create(),
	                false, 
	                0,
	                SoundInstance.Attenuation.NONE,
	                0.0D, 0.0D, 0.0D,
	                true
	            );
	            mc.getSoundManager().play(currentScreamingEasterEggSound);
	        }
	    }
	}
	
	public static void stopLastStandSound() {
	    Minecraft mc = Minecraft.getInstance();
	    if (currentLastStandSound != null) {
	        mc.getSoundManager().stop(currentLastStandSound);
	        currentLastStandSound = null;
	    }
	    if (currentScreamingEasterEggSound != null) {
	        mc.getSoundManager().stop(currentScreamingEasterEggSound);
	        currentScreamingEasterEggSound = null;
	    }
	}
}