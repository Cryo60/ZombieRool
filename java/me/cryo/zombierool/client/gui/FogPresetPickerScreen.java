package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Server-provided datapack tables; custom IDs also support tables added later. */
public final class FogPresetPickerScreen extends Screen {
    private final Screen parent;
    private final List<String> tables;
    private final Consumer<String> accept;
    private String selected;
    private EditBox query;
    private TableList list;
    private Button apply;
    public FogPresetPickerScreen(Screen parent,List<String> tables,String selected,Consumer<String> accept){super(Component.translatable("config.zombierool.fogPreset"));this.parent=parent;this.tables=tables;this.selected=selected;this.accept=accept;}
    @Override protected void init(){
        int w=Math.min(440,width-32),left=(width-w)/2;
        query=new EditBox(font,left,36,w,20,Component.translatable("config.zombierool.fogSearch"));query.setMaxLength(256);query.setValue("");addRenderableWidget(query);
        list=new TableList(w,left);addWidget(list);
        apply=addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.config.save"),b->{accept.accept(selected);minecraft.setScreen(parent);}).bounds(width/2-105,height-28,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(width/2+5,height-28,100,20).build());
        query.setResponder(text->{selected=text.trim();refresh();});refresh();setInitialFocus(query);
    }
    private void refresh(){list.clearRows();String filter=query.getValue().toLowerCase(Locale.ROOT);for(String id:tables)if(id.toLowerCase(Locale.ROOT).contains(filter))list.addRow(new TableRow(id));apply.active=tables.contains(selected);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float pt){g.fillGradient(0,0,width,height,0xF018202B,0xF00C1017);g.drawCenteredString(font,title,width/2,12,0xF3D18A);list.render(g,x,y,pt);super.render(g,x,y,pt);if(query.getValue().isEmpty())g.drawString(font,Component.translatable("config.zombierool.fogSearch"),query.getX()+6,42,0x777F8F,false);}
    private final class TableList extends ObjectSelectionList<TableRow>{
        TableList(int w,int left){super(FogPresetPickerScreen.this.minecraft,w,FogPresetPickerScreen.this.height,64,FogPresetPickerScreen.this.height-36,24);setLeftPos(left);setRenderBackground(false);setRenderTopAndBottom(false);}
        void clearRows(){clearEntries();}void addRow(TableRow row){addEntry(row);}
        @Override public int getRowWidth(){return width-12;}
        @Override protected int getScrollbarPosition(){return getRight()-6;}
    }
    private final class TableRow extends ObjectSelectionList.Entry<TableRow>{
        final String id;TableRow(String id){this.id=id;}
        @Override public Component getNarration(){return id.isEmpty()?Component.translatable("config.zombierool.enemies.noLoot"):Component.literal(id);}
        @Override public void render(GuiGraphics g,int i,int top,int left,int w,int h,int mx,int my,boolean hovered,float pt){g.fill(left,top,left+w,top+h,hovered?0xFF303E50:0x80202B39);g.drawString(font,font.plainSubstrByWidth(getNarration().getString(),w-12),left+6,top+6,id.equals(selected)?0xF3D18A:0xE5ECF5,false);}
        @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;selected=id;accept.accept(id);minecraft.setScreen(parent);return true;}
    }
}
