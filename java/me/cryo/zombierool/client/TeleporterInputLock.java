package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.TeleporterPlayerLock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class TeleporterInputLock {
    private TeleporterInputLock(){}
    public static boolean active(){return Minecraft.getInstance().player!=null&&TeleporterPlayerLock.active(Minecraft.getInstance().player);}
    public static void clearKeys(){
        var mc=Minecraft.getInstance();
        for(var key:mc.options.keyMappings){key.setDown(false);while(key.consumeClick()){} }
        if(mc.player!=null){var input=mc.player.input;input.forwardImpulse=input.leftImpulse=0;input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent event){if(active())clearKeys();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void movement(MovementInputUpdateEvent event){if(TeleporterPlayerLock.active(event.getEntity())){var i=event.getInput();i.forwardImpulse=i.leftImpulse=0;i.up=i.down=i.left=i.right=i.jumping=i.shiftKeyDown=false;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(InputEvent.InteractionKeyMappingTriggered event){if(active()){event.setCanceled(true);event.setSwingHand(false);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event){if(active()&&Minecraft.getInstance().screen==null)event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent event){if(active()&&Minecraft.getInstance().screen==null)event.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){var player=Minecraft.getInstance().player;if(player!=null)TeleporterPlayerLock.release(player);}
}
