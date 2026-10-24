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
    private static long started, flashUntil, clock;
    private static int duration;
    private static long arrived=-1;
    public static final int TUNNEL_TICKS=me.cryo.zombierool.gameplay.TeleporterPlayerLock.TUNNEL_TICKS,DAZE_TICKS=me.cryo.zombierool.gameplay.TeleporterPlayerLock.DAZE_TICKS;
    private static net.minecraft.client.player.LocalPlayer owner;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private TeleporterChargeOverlay() {}
    public static void start(int ticks) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(owner==mc.player&&world==mc.level&&arrived>=0&&clock<flashUntil&&ticks>=0)return;
        owner=mc.player;world=mc.level;
        me.cryo.zombierool.gameplay.TeleporterPlayerLock.update(owner,ticks);
        if(ticks==0){me.cryo.zombierool.client.TeleporterInputLock.clearKeys();me.cryo.zombierool.handlers.KeyInputHandler.clearTeleportInput();me.cryo.zombierool.client.BrutusUnlockClient.cancel();}
        if(ticks==0&&mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)owner.closeContainer();
        if(ticks<=0){
            duration=0;arrived=ticks==0?clock:-1;flashUntil=ticks==0?arrived+TUNNEL_TICKS+DAZE_TICKS:0;
            mc.getSoundManager().stop(new ResourceLocation("zombierool","teleport_start"),SoundSource.PLAYERS);
        }else{started=clock;duration=Math.min(200,ticks);flashUntil=0;arrived=-1;}
    }
    public static void reset(){if(owner!=null)me.cryo.zombierool.gameplay.TeleporterPlayerLock.release(owner);duration=0;flashUntil=0;arrived=-1;owner=null;world=null;Minecraft.getInstance().getSoundManager().stop(new ResourceLocation("zombierool","teleport_start"),SoundSource.PLAYERS);}
    public static boolean active(){return duration>0||world!=null&&flashUntil>clock;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&!mc.isPaused())clock++;
        if(owner!=null&&(owner!=mc.player||world!=mc.level||!owner.isAlive()))reset();
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(duration==0&&flashUntil==0)return;
        if(owner!=mc.player||world!=mc.level||!mc.player.isAlive()){reset();return;}
        double now=clock+(mc.isPaused()?0:event.getPartialTick());
        if(duration>0&&now>started+duration+20)duration=0;
        float flash=(float)Math.max(0,Math.min(1,(flashUntil-now)/DAZE_TICKS));
        if(duration==0&&flash==0)return;
        GuiGraphics g=event.getGuiGraphics();int w=event.getWindow().getGuiScaledWidth(),h=event.getWindow().getGuiScaledHeight();
        if(arrived>=0&&now<arrived+TUNNEL_TICKS){tunnel(g,w,h,now-arrived);return;}
        float progress=duration>0?(float)Math.max(0,Math.min(1,(now-started)/duration)):0;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        int alpha=(int)(18+progress*42+flash*160);
        g.fill(0,0,w,h,(alpha<<24)|0x86DDEA);
        int edge=((int)(60+progress*100)<<24)|0x4CAABF;
        g.fillGradient(0,0,w,h/3,edge,0);g.fillGradient(0,h*2/3,w,h,0,edge);
        if(duration>0){
            Random random=new Random((long)(mc.player.tickCount/2)*912367L);
            int count=12+(int)(progress*18);
            for(int arc=0;arc<count;arc++){
                int side=random.nextInt(4),x=side<2?(side==0?0:w-1):random.nextInt(Math.max(1,w)),y=side>=2?(side==2?0:h-1):random.nextInt(Math.max(1,h));
                int targetX=(int)(w*(.2+random.nextDouble()*.6)),targetY=(int)(h*(.2+random.nextDouble()*.6));
                for(int segment=1;segment<=7;segment++){
                    int nx=x+(targetX-x)/(8-segment)+random.nextInt(25)-12;
                    int ny=y+(targetY-y)/(8-segment)+random.nextInt(25)-12;
                    line(g,x,y,nx,ny,0x283137DF,10);line(g,x,y,nx,ny,0x70477CEF,4);line(g,x,y,nx,ny,0xFFF4FCFF,1);
                    if(segment==3||segment==5){
                        int bx=nx+random.nextInt(81)-40,by=ny+random.nextInt(61)-30;
                        int mx=(nx+bx)/2+random.nextInt(13)-6,my=(ny+by)/2+random.nextInt(13)-6;
                        line(g,nx,ny,mx,my,0x60477CEF,3);line(g,nx,ny,mx,my,0xCEEAF4FF,1);
                        line(g,mx,my,bx,by,0x40477CEF,2);line(g,mx,my,bx,by,0xA0EAF4FF,1);
                    }
                    x=nx;y=ny;
                }
            }
        }
        g.flush();RenderSystem.disableBlend();
    }
    @SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(mc.level==null||owner!=mc.player||arrived<0)return;
        double t=clock+(mc.isPaused()?0:event.getPartialTick())-arrived-TUNNEL_TICKS;
        if(t<0||t>=DAZE_TICKS)return;float fade=(float)(1-t/DAZE_TICKS);
        event.setRoll(event.getRoll()+(float)Math.sin(t*.5)*fade*3);event.setPitch(event.getPitch()+(float)Math.sin(t*.3)*fade*1.4f);
    }
    private static void tunnel(GuiGraphics g,int w,int h,double time){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.fill(0,0,w,h,0xFF050410);
        float cx=w*.5f,cy=h*.5f;
        // Perspective rings rush from a bright centre, with red/indigo filaments like the reference.
        for(int ring=0;ring<22;ring++){
            double depth=(ring/22.0+time*.024)%1;float radius=(float)(Math.pow(depth,2.3)*Math.max(w,h));
            float twist=(float)(time*.03+depth*2.5);
            for(int segment=0;segment<64;segment++){
                double a=segment*Math.PI/32+twist,b=(segment+1)*Math.PI/32+twist;
                float turbulence=(float)(1+.16*Math.sin(segment*1.7+ring*.8+time*.09));
                int color=((int)(80+depth*145)<<24)|(segment%3==0?0xBA194A:0x34379E);
                line(g,(int)(cx+Math.cos(a)*radius*turbulence),(int)(cy+Math.sin(a)*radius*.65*turbulence),(int)(cx+Math.cos(b)*radius*turbulence),(int)(cy+Math.sin(b)*radius*.65*turbulence),color,Math.max(1,(int)(depth*9)));
            }
        }
        for(int i=16;i>0;i--)g.fill((int)cx-i,(int)cy-i,(int)cx+i,(int)cy+i,((18-i)<<24)|0xA8CDF8);
        if(time>TUNNEL_TICKS-6)g.fill(0,0,w,h,((int)((time-TUNNEL_TICKS+6)/6*240)<<24)|0xD9E6F3);
        g.flush();RenderSystem.disableBlend();
    }
    private static void line(GuiGraphics g,int x,int y,int nx,int ny,int color,int thickness){
        float dx=nx-x,dy=ny-y,length=(float)Math.sqrt(dx*dx+dy*dy);if(length<.01f)return;
        float ox=-dy/length*thickness,oy=dx/length*thickness;
        var pose=g.pose().last().pose();var vertices=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        int a=(color>>>24)&255,r=(color>>>16)&255,green=(color>>>8)&255,b=color&255;
        vertices.vertex(pose,nx+ox,ny+oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,nx-ox,ny-oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,x-ox,y-oy,0).color(r,green,b,a).endVertex();
        vertices.vertex(pose,x+ox,y+oy,0).color(r,green,b,a).endVertex();
    }
}
