package me.cryo.zombierool.client;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class PanzerCaptureInput{
    public static boolean caught;
    @SubscribeEvent public static void input(net.minecraftforge.client.event.MovementInputUpdateEvent e){if(caught){var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.up=false;input.down=false;input.left=false;input.right=false;input.jumping=false;input.shiftKeyDown=false;}}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){caught=false;}
}
