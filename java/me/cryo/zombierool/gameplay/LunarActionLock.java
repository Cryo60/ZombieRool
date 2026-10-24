package me.cryo.zombierool.gameplay;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.entity.player.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class LunarActionLock {
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent e){if(LunarEquipment.busy(e.getEntity())&&e.isCancelable())e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent e){if(LunarEquipment.busy(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start e){if(LunarEquipment.busy(e.getEntity()))e.setCanceled(true);}
}
