package me.cryo.zombierool.handlers;

import me.cryo.zombierool.gameplay.WaveManager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Combat impacts use camera feedback; scripted pulls, launches and travel keep their movement. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class PlayerInjuryEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent event){
        if(event.getEntity() instanceof Player player&&!player.isCreative()&&!player.isSpectator()&&WaveManager.isGameRunning())event.setCanceled(true);
    }
}
