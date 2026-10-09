package me.cryo.zombierool.client.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
public final class MenuTheme {
    private static Screen frame;
    private MenuTheme(){}
    public static void begin(GuiGraphics g,Screen screen){frame=null;if(applies(screen)){background(g,screen);frame=screen;}}
    public static void end(){frame=null;}
    public static boolean applies(Screen screen){return screen!=null&&!(screen instanceof net.minecraft.client.gui.screens.ChatScreen)&&!(screen instanceof net.minecraft.client.gui.screens.InBedChatScreen);}
    public static void background(GuiGraphics g,Screen screen){
        if(frame==screen)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null)WaWMenuBackdrop.draw(g,screen.width,screen.height,0);
        else g.fill(0,0,screen.width,screen.height,0xB0101012);
        g.fill(0,0,screen.width,2,0xFF9A1C14);g.fill(0,screen.height-2,screen.width,screen.height,0xFF9A1C14);
    }
    public static void button(GuiGraphics g,AbstractWidget b){
        int x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight();boolean selected=b.active&&(b.isHoveredOrFocused());
        g.fill(x,y,x+w,y+h,selected?0xDD343434:0xBB131315);
        g.fill(x,y+h-1,x+w,y+h,selected?0xFFB0B0B0:0xFF444444);
        if(selected)g.fill(x,y,x+2,y+h,0xFF9A1C14);
        var font=Minecraft.getInstance().font;
        g.enableScissor(x+3,y,x+w-3,y+h);
        g.drawCenteredString(font,b.getMessage(),x+w/2,y+(h-font.lineHeight)/2,b.active?(selected?0xFFF4F1E9:0xFFB0ACA4):0xFF666666);
        g.disableScissor();
    }
}
