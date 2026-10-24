package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/** Copies inventory stacks into configuration without consuming or modifying them. */
public final class MapItemSlotScreen extends Screen {
    private final Screen parent;
    private final Consumer<ItemStack> save;
    private ItemStack selected, carried=ItemStack.EMPTY;
    public MapItemSlotScreen(Screen parent,Component title,ItemStack selected,Consumer<ItemStack> save){super(title);this.parent=parent;this.selected=selected.copy();this.save=save;}
    private int x(){return width/2-81;}
    private int y(){return height/2-10;}
    private boolean target(double mx,double my){return mx>=width/2-10&&mx<width/2+10&&my>=y()-38&&my<y()-18;}
    private int inventoryIndex(double mx,double my){int col=(int)Math.floor((mx-x())/18),row=(int)Math.floor((my-y())/18);if(col<0||col>=9||row<0||row>=4)return -1;return row==3?col:9+row*9+col;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.translatable("config.zombierool.apply"),b->{save.accept(selected.copy());onClose();}).bounds(width/2-105,y()+86,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2+5,y()+86,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.zombierool.slot.clear"),b->selected=ItemStack.EMPTY).bounds(width/2+24,y()-38,76,20).build());
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){int index=inventoryIndex(mx,my);if(index>=0){carried=minecraft.player.getInventory().getItem(index).copy();return true;}if(target(mx,my)){selected=carried.copy();carried=ItemStack.EMPTY;return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&target(mx,my)&&!carried.isEmpty()){selected=carried.copy();carried=ItemStack.EMPTY;return true;}return super.mouseReleased(mx,my,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);g.drawCenteredString(font,title,width/2,y()-78,0xFFFFFF);g.drawCenteredString(font,Component.translatable("config.zombierool.slot.hint"),width/2,y()-60,0xBCC8D8);drawSlot(g,width/2-9,y()-37,selected);
        for(int i=0;i<36;i++){int row=i<9?3:(i-9)/9,col=i%9;drawSlot(g,x()+col*18,y()+row*18,minecraft.player.getInventory().getItem(i));}
        super.render(g,mx,my,pt);if(!carried.isEmpty())g.renderItem(carried,mx-8,my-8);else{int i=inventoryIndex(mx,my);ItemStack hover=target(mx,my)?selected:i>=0?minecraft.player.getInventory().getItem(i):ItemStack.EMPTY;if(!hover.isEmpty())g.renderTooltip(font,hover,mx,my);}
    }
    private void drawSlot(GuiGraphics g,int x,int y,ItemStack stack){g.fill(x,y,x+18,y+18,0xFF8792A0);g.fill(x+1,y+1,x+17,y+17,0xFF202A38);g.renderItem(stack,x+1,y+1);g.renderItemDecorations(font,stack,x+1,y+1);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
