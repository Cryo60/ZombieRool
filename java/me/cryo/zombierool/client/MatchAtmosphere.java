package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MatchAtmosphere {
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static long start=-1,end=-1;
    private static int remaining;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){world=null;remaining=0;start=-1;end=-1;}
    public static void update(int count,long from,long until){world=Minecraft.getInstance().level;remaining=Math.max(0,count);start=from;end=until;}
    public static int remaining(){return world==Minecraft.getInstance().level?remaining:0;}
    public static boolean active(){var level=Minecraft.getInstance().level;return level!=null&&level==world&&start>=0&&level.getGameTime()<end;}
    public static float yaw(net.minecraft.world.entity.Entity entity,float original,float partial){
        var mc=Minecraft.getInstance();if(!active()||mc.player==null)return original;
        // Every participant watches the viewer during the same pauses.
        float face=(float)Math.toDegrees(Math.atan2(mc.player.getZ()-entity.getZ(),mc.player.getX()-entity.getX()))-90;
        return face+FiestaChoreography.turn((float)(world.getGameTime()-start)+partial);
    }
}
