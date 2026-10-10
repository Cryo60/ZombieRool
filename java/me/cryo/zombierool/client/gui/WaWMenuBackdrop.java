package me.cryo.zombierool.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
/** Bundled screenshots, with a slow pan and a gentle transition. No world rendering. */
public final class WaWMenuBackdrop {
    private static final ResourceLocation[] VIEWS={
        new ResourceLocation("zombierool","textures/gui/panorama/view_0.png"),
        new ResourceLocation("zombierool","textures/gui/panorama/view_1.png"),
        new ResourceLocation("zombierool","textures/gui/panorama/view_2.png")};
    private static final long EPOCH=net.minecraft.Util.getMillis();
    private static int released=-1;
    private WaWMenuBackdrop(){}
    public static void draw(GuiGraphics g,int w,int h,float ignored){
        float seconds=(net.minecraft.Util.getMillis()-EPOCH)/1000f;
        int cycle=(int)(seconds/18),current=Math.floorMod(cycle,VIEWS.length),next=(current+1)%VIEWS.length;
        float phase=seconds%18,blend=Math.max(0,(phase-16)/2);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        view(g,VIEWS[current],w,h,seconds,1);
        if(blend>0)view(g,VIEWS[next],w,h,seconds,blend);
        // Keep at most the current view and the upcoming view in GPU memory.
        if(released!=current){net.minecraft.client.Minecraft.getInstance().getTextureManager().release(VIEWS[(current+2)%VIEWS.length]);released=current;}
        g.setColor(1,1,1,1);
        g.fill(0,0,w,h,0x30000000);
        g.fillGradient(0,0,w,h,0x44000000,0xAA000000);
    }
    private static void view(GuiGraphics g,ResourceLocation texture,int w,int h,float time,float alpha){
        float scale=Math.max(w/1920f,h/1080f)*1.07f;
        int dw=(int)Math.ceil(1920*scale),dh=(int)Math.ceil(1080*scale);
        int x=(w-dw)/2+(int)(Math.sin(time*.035)*(dw-w)/2),y=(h-dh)/2;
        g.setColor(1,1,1,alpha);g.blit(texture,x,y,dw,dh,0,0,1920,1080,1920,1080);
    }
}
