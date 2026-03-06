package me.cryo.zombierool.event;

import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.ZombieroolMod;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonWeaponEventHandler {

    private static final TagKey<EntityType<?>> ZOMBIE_LIKE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("zombierool", "allowed_mobs"));

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity killedEntity = event.getEntity();
        Entity eventKiller = event.getSource().getEntity(); 
        
        if (eventKiller instanceof Player player) {
            if (killedEntity.getType().is(ZOMBIE_LIKE)) {
                PlayerVoiceManager.playKillConfirmedSound(player, player.level());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player != null) {
            PlayerVoiceManager.checkAndPlayReloadingSoundOnTick(event.player);
        }
    }
}