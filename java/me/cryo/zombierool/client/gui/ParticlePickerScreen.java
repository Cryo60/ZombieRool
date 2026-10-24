package me.cryo.zombierool.client.gui;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

/** Includes installed addon particles which can actually be used without extra parameters. */
public final class ParticlePickerScreen extends Screen {
    private final Screen parent;private final Consumer<String> accept;private final String selected;
    private boolean mods;private String query="";private Choices list;
    public ParticlePickerScreen(Screen parent,String selected,Consumer<String> accept){super(tr("title"));this.parent=parent;this.selected=selected;this.accept=accept;mods=!selected.isBlank()&&!selected.startsWith("minecraft:");}
    public static List<String> choices(boolean mods,String query){return ForgeRegistries.PARTICLE_TYPES.getEntries().stream().filter(e->e.getValue() instanceof ParticleOptions).map(e->e.getKey().location()).filter(id->mods!=id.getNamespace().equals("minecraft")).map(Object::toString).filter(id->id.contains(query.toLowerCase(Locale.ROOT))).sorted().toList();}
    private static Component tr(String key){return Component.translatable("gui.zombierool.particles."+key);}
    @Override protected void init(){clearWidgets();int w=Math.min(460,width-32),x=(width-w)/2;
        for(int i=0;i<2;i++){boolean category=i==1;addRenderableWidget(Button.builder(tr(category?"mods":"vanilla"),b->{mods=category;init();}).bounds(x+i*(w/2),36,w/2-3,20).build()).active=mods!=category;}
        var search=new EditBox(font,x,61,w,20,tr("search"));search.setValue(query);search.setHint(tr("search"));addRenderableWidget(search);list=new Choices(w,x);addWidget(list);search.setResponder(q->{query=q;refresh();});refresh();addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-28,w,20).build());}
    private void refresh(){list.reset();choices(mods,query).forEach(id->list.add(new Row(id)));}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xEF0D141E);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);list.render(g,x,y,partial);super.render(g,x,y,partial);}
    private final class Choices extends ObjectSelectionList<Row>{Choices(int w,int x){super(ParticlePickerScreen.this.minecraft,w,ParticlePickerScreen.this.height,86,ParticlePickerScreen.this.height-36,28);setLeftPos(x);setRenderBackground(false);setRenderTopAndBottom(false);}void reset(){clearEntries();setScrollAmount(0);}void add(Row row){addEntry(row);}@Override public int getRowWidth(){return width-14;}@Override protected int getScrollbarPosition(){return getRight()-6;}}
    private final class Row extends ObjectSelectionList.Entry<Row>{final String id;Row(String id){this.id=id;}@Override public Component getNarration(){return Component.literal(id);}@Override public void render(GuiGraphics g,int index,int top,int left,int w,int h,int mx,int my,boolean hovered,float p){g.fill(left,top,left+w,top+h-2,hovered?0xFF303E50:0x80202B39);g.drawString(font,font.plainSubstrByWidth(id,w-12),left+6,top+8,id.equals(selected)?0xF3D18A:0xFFFFFF,false);}@Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;accept.accept(id);onClose();return true;}}
}
