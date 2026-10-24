package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Teleporter arrival presets, including map-authored entries sent by the server. */
public final class AtmospherePresetPickerScreen extends Screen {
    private record Choice(String id,String name){}
    private final Screen parent;private final Consumer<String> accept;private final List<Choice> choices=new ArrayList<>();private final String selected;private PresetList list;
    public AtmospherePresetPickerScreen(Screen parent,ListTag library,String selected,Consumer<String> accept){this(parent,library,selected,accept,tr("arrivalAtmospherePreset"),true);}
    public AtmospherePresetPickerScreen(Screen parent,ListTag library,String selected,Consumer<String> accept,Component title,boolean atmosphereOptions){super(title);this.parent=parent;this.accept=accept;this.selected=selected;if(atmosphereOptions){choices.add(new Choice("",tr("atmosphere.keep").getString()));choices.add(new Choice("@map",tr("atmosphere.map").getString()));}for(Tag tag:library){var row=(CompoundTag)tag;choices.add(new Choice(row.getString("id"),row.getString("name")));}}
    private static Component tr(String key){return Component.translatable("gui.zombierool.transport."+key);}
    @Override protected void init(){int w=Math.min(440,width-32),x=(width-w)/2;var search=new EditBox(font,x,40,w,20,Component.translatable("gui.zombierool.atmosphere.searchPresets"));search.setHint(Component.translatable("gui.zombierool.atmosphere.searchPresets"));search.setMaxLength(100);addRenderableWidget(search);list=new PresetList(w,x);addWidget(list);search.setResponder(query->refresh(query));refresh("");addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-28,w,20).build());}
    private void refresh(String query){list.clearRows();String filter=query.toLowerCase(Locale.ROOT);for(var row:choices)if((row.id+" "+row.name).toLowerCase(Locale.ROOT).contains(filter))list.addRow(new PresetRow(row));}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xE00D141E);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);list.render(g,mx,my,partial);super.render(g,mx,my,partial);}
    private final class PresetList extends ObjectSelectionList<PresetRow>{
        PresetList(int w,int x){super(AtmospherePresetPickerScreen.this.minecraft,w,AtmospherePresetPickerScreen.this.height,68,AtmospherePresetPickerScreen.this.height-36,27);setLeftPos(x);setRenderBackground(false);setRenderTopAndBottom(false);}
        void clearRows(){clearEntries();}void addRow(PresetRow row){addEntry(row);}@Override public int getRowWidth(){return width-14;}@Override protected int getScrollbarPosition(){return getRight()-6;}
    }
    private final class PresetRow extends ObjectSelectionList.Entry<PresetRow>{
        final Choice row;PresetRow(Choice row){this.row=row;}@Override public Component getNarration(){return Component.literal(row.name);}
        @Override public void render(GuiGraphics g,int index,int top,int left,int w,int h,int mx,int my,boolean hovered,float partial){g.fill(left,top,left+w,top+h-2,hovered?0xFF303E50:0x80202B39);g.drawString(font,font.plainSubstrByWidth(row.name,w-12),left+6,top+7,row.id.equals(selected)?0xF3D18A:0xFFFFFF,false);}
        @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;accept.accept(row.id);minecraft.setScreen(parent);return true;}
    }
}
