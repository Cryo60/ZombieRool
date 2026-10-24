package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BlockPowerScreen extends Screen {
    private record Parent(net.minecraft.core.BlockPos pos,Screen screen,Runnable restore){}
    private static Parent pending;
    private Parent parent;
    private final BlockPowerPackets.Open settings;
    private BlockPowerSettings.Mode mode;
    private boolean visible;
    public BlockPowerScreen(BlockPowerPackets.Open settings){super(tr(settings.signal()?"visibility":"title"));this.settings=settings;mode=settings.mode();visible=settings.visible();}
    private static Component tr(String key){return Component.translatable("gui.zombierool.block_power."+key);}
    public static void request(net.minecraft.core.BlockPos pos,Screen parent,Runnable restore){pending=new Parent(pos,parent,restore);NetworkHandler.INSTANCE.sendToServer(new BlockPowerPackets.Request(pos));}
    public static void open(BlockPowerPackets.Open settings){
        var screen=new BlockPowerScreen(settings);
        if(pending!=null&&pending.pos().equals(settings.pos()))screen.parent=pending;
        pending=null;Minecraft.getInstance().setScreen(screen);
    }
    @Override public void onClose(){
        Minecraft.getInstance().setScreen(parent==null?null:parent.screen());
        if(parent!=null&&parent.restore()!=null)parent.restore().run();
    }
    @Override protected void init(){
        int x=width/2-120,y=height/2-30;
        if(settings.signal())addRenderableWidget(CycleButton.onOffBuilder(visible).create(x,y,240,20,tr("show_survival"),(button,value)->visible=value));
        else addRenderableWidget(CycleButton.builder((BlockPowerSettings.Mode value)->tr(value.name().toLowerCase(java.util.Locale.ROOT))).withValues(BlockPowerSettings.Mode.values()).withInitialValue(mode).create(x,y,240,20,tr("source"),(button,value)->mode=value));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->{NetworkHandler.INSTANCE.sendToServer(new BlockPowerPackets.Save(settings.pos(),mode,visible));onClose();}).bounds(x,y+70,116,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button->onClose()).bounds(x+124,y+70,116,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){
        renderBackground(graphics);super.render(graphics,mx,my,partial);graphics.drawCenteredString(font,title,width/2,height/2-60,0xFFFFFF);
        graphics.drawWordWrap(font,tr(settings.signal()?"visibility_help":"help"),width/2-120,height/2,240,0xBBBBBB);
    }
    @Override public boolean isPauseScreen(){return false;}
}
