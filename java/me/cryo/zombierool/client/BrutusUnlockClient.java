package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.network.InteractionType;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SUnifiedInteractPacket;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BrutusUnlockClient {
    private static BlockPos requested,active;
    private static int elapsed,duration;
    private static double receivedTick,shownElapsed;
    public static boolean active(){return active!=null;}
    public static void receive(CompoundTag n){
        if(n.getInt("elapsed")<0){active=null;return;}
        var pos=BlockPos.of(n.getLong("position"));
        if(pos.equals(requested)&&KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown()){
            if(!pos.equals(active))shownElapsed=0;
            active=pos;elapsed=n.getInt("elapsed");duration=n.getInt("duration");receivedTick=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        }
    }
    public static void update(boolean held,BlockPos target){
        var mc=Minecraft.getInstance();
        boolean allowed=held&&target!=null&&mc.player!=null&&!me.cryo.zombierool.handlers.KeyInputHandler.isLocalPlayerDown&&me.cryo.zombierool.gameplay.PointManager.getScore(mc.player)>=BrutusChains.unlockCost;
        if(!allowed||!target.equals(requested))cancel();
        if(allowed){
            boolean first=requested==null;requested=target;
            if(first||mc.player.tickCount%5==0)NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(target,InteractionType.BRUTUS_UNLOCK));
        }
    }
    public static void cancel(){
        if(requested!=null&&Minecraft.getInstance().getConnection()!=null)NetworkHandler.INSTANCE.sendToServer(new C2SUnifiedInteractPacket(requested,InteractionType.BRUTUS_UNLOCK_CANCEL));
        var player=Minecraft.getInstance().player;if(player!=null&&active!=null)ThirdPersonAnimHandler.stopAnim(player.getUUID(),"knuckle_crack");
        requested=active=null;elapsed=duration=0;shownElapsed=0;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){requested=active=null;elapsed=duration=0;}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void interaction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e){if(active()){e.setCanceled(true);e.setSwingHand(false);}}
    public static float progress(float partialTick){
        if(!active()||duration<=0)return 0;
        var level=Minecraft.getInstance().level;double now=level==null?receivedTick:level.getGameTime();
        shownElapsed=Math.max(shownElapsed,elapsed+Math.max(0,now-receivedTick+partialTick));
        return (float)Math.min(1,shownElapsed/duration);
    }
    public static boolean hideHeldItems(){
        var p=Minecraft.getInstance().player;return active()||p!=null&&"knuckle_crack".equals(ThirdPersonAnimHandler.currentAnim(p.getUUID()))&&ThirdPersonAnimHandler.isAnimationPlaying(p.getUUID());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void hand(net.minecraftforge.client.event.RenderHandEvent e){if(hideHeldItems())e.setCanceled(true);}
    public static void draw(net.minecraft.client.gui.GuiGraphics gui,int width,int height){
        if(!active()||duration<=0)return;
        var mc=Minecraft.getInstance();float progress=progress(mc.getFrameTime());
        var text=net.minecraft.network.chat.Component.translatable("message.zombierool.brutus.unlock_progress",(int)(progress*100));
        int x=(width-150)/2,y=height/2+55;
        gui.fill(x,y,x+150,y+10,0xCC151515);gui.fill(x+1,y+1,x+1+(int)(148*progress),y+9,0xFFFFB34A);
        gui.drawCenteredString(mc.font,text,width/2,y-mc.font.lineHeight-3,0xFFFFFFFF);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void overlay(net.minecraftforge.client.event.RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;draw(e.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void mouse(net.minecraftforge.client.event.InputEvent.MouseButton e){
        var mc=Minecraft.getInstance();if(active()&&(mc.options.keyAttack.matchesMouse(e.getButton())||mc.options.keyUse.matchesMouse(e.getButton()))&&e.isCancelable())e.setCanceled(true);
    }
}
