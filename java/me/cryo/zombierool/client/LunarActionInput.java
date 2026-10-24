package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class LunarActionInput {
 public static boolean active(){var p=Minecraft.getInstance().player;return p!=null&&(me.cryo.zombierool.gameplay.LunarEquipment.busy(p)||LunarEquipmentClient.helmetAnimation(p));}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent e){if(!active())return;var mc=Minecraft.getInstance();
  for(var key:mc.options.keyMappings)if(key==mc.options.keyAttack||key==mc.options.keyUse||key==mc.options.keyDrop||key==mc.options.keySwapOffhand||key.getName().startsWith("key.tacz.")){key.setDown(false);while(key.consumeClick()){}}
  for(var key:mc.options.keyHotbarSlots){key.setDown(false);while(key.consumeClick()){}}
  for(var key:new net.minecraft.client.KeyMapping[]{mc.options.keyPickItem,mc.options.keyInventory}){key.setDown(false);while(key.consumeClick()){}}
  mc.player.stopUsingItem();
  if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")){
   var op=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);var data=op.getDataHolder();
   if(op.isAim())op.aim(false);data.clientIsAiming=false;data.clientAimingProgress=0;com.tacz.guns.client.gameplay.LocalPlayerDataHolder.oldAimingProgress=0;data.isCharging=false;data.chargeProgress=0;
  }
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interaction(InputEvent.InteractionKeyMappingTriggered e){if(active()){e.setCanceled(true);e.setSwingHand(false);}}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre e){if(active()&&Minecraft.getInstance().screen==null)e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent e){if(active()&&Minecraft.getInstance().screen==null)e.setCanceled(true);}
}
