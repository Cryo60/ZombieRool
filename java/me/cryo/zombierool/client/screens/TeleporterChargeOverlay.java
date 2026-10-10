package me.cryo.zombierool.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-controlled buildup; rendering never initiates a teleport. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class TeleporterChargeOverlay {
    private static int started, duration, flashUntil;
    private TeleporterChargeOverlay() {}
    public static void start(int ticks) {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(ticks<=0){
            duration=0;flashUntil=ticks==0?mc.player.tickCount+6:0;
            mc.getSoundManager().stop(new ResourceLocation("zombierool","teleport_start"),SoundSource.PLAYERS);
        }else{started=mc.player.tickCount;duration=Math.min(200,ticks);flashUntil=0;}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){duration=0;flashUntil=0;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        float now=mc.player.tickCount+event.getPartialTick();
        if(duration>0&&now>started+duration+20)duration=0;
        float flash=Math.max(0,(flashUntil-now)/6f);
        if(duration==0&&flash==0)return;
        GuiGraphics g=event.getGuiGraphics();int w=event.getWindow().getGuiScaledWidth(),h=event.getWindow().getGuiScaledHeight();
        float progress=duration>0?Math.min(1,(now-started)/duration):0;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        int alpha=(int)(18+progress*42+flash*160);
        g.fill(0,0,w,h,(alpha<<24)|0x86DDEA);
        int edge=((int)(60+progress*100)<<24)|0x4CAABF;
        g.fillGradient(0,0,w,h/3,edge,0);g.fillGradient(0,h*2/3,w,h,0,edge);
        if(duration>0){
            Random random=new Random((long)(mc.player.tickCount/2)*912367L);
            int count=5+(int)(progress*12);
            for(int arc=0;arc<count;arc++){
                int side=random.nextInt(4),x=side<2?(side==0?0:w-1):random.nextInt(Math.max(1,w)),y=side>=2?(side==2?0:h-1):random.nextInt(Math.max(1,h));
                int targetX=(int)(w*(.2+random.nextDouble()*.6)),targetY=(int)(h*(.2+random.nextDouble()*.6));
                for(int segment=1;segment<=7;segment++){
                    int nx=x+(targetX-x)/(8-segment)+random.nextInt(25)-12;
                    int ny=y+(targetY-y)/(8-segment)+random.nextInt(25)-12;
                    line(g,x,y,nx,ny,0x8070D6EF,3);line(g,x,y,nx,ny,0xDDE5FFFF,1);x=nx;y=ny;
                }
            }
        }
        g.flush();RenderSystem.disableBlend();
    }
    private static void line(GuiGraphics g,int x,int y,int nx,int ny,int color,int thickness){
        float dx=nx-x,dy=ny-y,length=(float)Math.sqrt(dx*dx+dy*dy);if(length<.01f)return;
        float ox=-dy/length*thickness,oy=dx/length*thickness;
        var pose=g.pose().last().pose();var vertices=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        int a=(color>>>24)&255,r=(color>>>16)&255,green=(color>>>8)&255,b=color&255;
        vertices.vertex(pose,x+ox,y+oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,x-ox,y-oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,nx-ox,ny-oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,nx+ox,ny+oy,0).color(r,green,b,a).endVertex();
    }
}
