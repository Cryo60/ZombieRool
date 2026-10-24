package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Gameplay-tick timing keeps the self-flash frozen in a paused single-player world. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class EquipmentFlashOverlay {
    private static long until;
    public static void flash(int ticks){var level=Minecraft.getInstance().level;if(level!=null)until=level.getGameTime()+Math.max(0,Math.min(80,ticks));}
    public static float opacity(float partial){var level=Minecraft.getInstance().level;if(level==null)return 0;double left=until-level.getGameTime()-partial;return (float)Math.max(0,Math.min(1,left/60));}
    @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();float alpha=opacity(mc.getFrameTime());if(alpha<=0)return;
        var gui=event.getGuiGraphics();gui.pose().pushPose();gui.pose().translate(0,0,600);
        gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),((int)(alpha*255)<<24)|0xFFFFFF);gui.pose().popPose();
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){until=0;}
}
