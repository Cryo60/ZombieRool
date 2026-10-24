package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.WaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small screen-space effects, independent of world sky/shader rendering. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class EncounterScreenEffects{
    private static Object world;private static long shockUntil;
    public static void shock(int ticks){var level=Minecraft.getInstance().level;if(level!=null){world=level;shockUntil=level.getGameTime()+Mth.clamp(ticks,0,60);}}
    public static boolean thiefAlarm(){return WaveManager.isGameRunning()&&WaveManager.isClientSpecialWave()&&!WaveManager.isClientHellhoundFog();}
    public static float shockOpacity(float partial){var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=world||mc.player==null||!mc.player.isAlive()||!WaveManager.isGameRunning())return 0;return Mth.clamp((shockUntil-mc.level.getGameTime()-partial)/20f,0,1);}
    @SubscribeEvent public static void draw(net.minecraftforge.client.event.RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!mc.player.isAlive())return;render(event.getGuiGraphics(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),mc.level.getGameTime()+mc.getFrameTime(),thiefAlarm(),shockOpacity(mc.getFrameTime()));}
    public static void render(GuiGraphics gui,int w,int h,float time,boolean alarm,float shock){
        gui.pose().pushPose();gui.pose().translate(0,0,510);
        if(alarm){float pulse=.5f+.5f*Mth.sin(time*.16f);gui.fill(0,0,w,h,((int)(42+32*pulse)<<24)|0xB40810);
            for(int i=0;i<7;i++){int inset=i*Math.max(1,Math.min(w,h)/65),alpha=(int)((18+16*pulse)*(1-i/8f));gui.fill(inset,inset,w-inset,inset+3,(alpha<<24)|0xF0141B);gui.fill(inset,h-inset-3,w-inset,h-inset,(alpha<<24)|0xF0141B);gui.fill(inset,inset,inset+3,h-inset,(alpha<<24)|0xF0141B);gui.fill(w-inset-3,inset,w-inset,h-inset,(alpha<<24)|0xF0141B);}}
        if(shock>0){gui.fill(0,0,w,h,((int)(12*shock)<<24)|0x75CAFF);var random=new java.util.Random((long)(time/2));
            for(int arc=0;arc<10;arc++){boolean horizontal=arc%2==0;float x=horizontal?(arc%4==0?0:w):random.nextFloat()*w,y=horizontal?random.nextFloat()*h:(arc%4==1?0:h);float dx=horizontal?(x==0?1:-1):0,dy=horizontal?0:(y==0?1:-1);
                for(int j=0;j<8;j++){float nx=x+dx*(5+random.nextFloat()*9)+(horizontal?0:random.nextFloat()*12-6),ny=y+dy*(5+random.nextFloat()*9)+(horizontal?random.nextFloat()*12-6:0);line(gui,x,y,nx,ny,((int)(190*shock*(1-j/10f))<<24)|0xA6E8FF);x=nx;y=ny;}}
        }
        gui.pose().popPose();
    }
    private static void line(GuiGraphics gui,float x,float y,float nx,float ny,int color){int steps=Math.max(1,(int)Math.max(Math.abs(nx-x),Math.abs(ny-y)));for(int i=0;i<=steps;i++){float t=i/(float)steps;int px=(int)Mth.lerp(t,x,nx),py=(int)Mth.lerp(t,y,ny);gui.fill(px,py,px+2,py+2,color);}}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){world=null;shockUntil=0;}
}
