package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Basic authoring controls; the full portable model remains available as JSON. */
final class MapActorSettingsScreen extends Screen {
    private final Screen parent;private final CompoundTag draft;private final boolean marker;private final Consumer<CompoundTag> accept;
    private final Map<String,EditBox> fields=new LinkedHashMap<>();private int page;private String error="";
    private List<String> keys(){return marker?List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","spawnRadius","firstWave","waveInterval"):List.of("name","health","damage","speed","aggroRadius","wanderRadius","leashRadius","scale","width","height","callback");}
    MapActorSettingsScreen(Screen parent,CompoundTag draft,boolean marker,Consumer<CompoundTag> accept){super(tr("settings"));this.parent=parent;this.draft=draft.copy();this.marker=marker;this.accept=accept;}
    private static Component tr(String key){return Component.translatable("gui.zombierool.actors."+key);}
    private void capture(){for(var entry:fields.entrySet()){String key=entry.getKey(),value=entry.getValue().getValue();if(List.of("name","callback","profile").contains(key))draft.putString(key,value);else{double number=Double.parseDouble(value);if(!Double.isFinite(number))throw new IllegalArgumentException(key);draft.putDouble(key,number);}}}
    private void button(int x,int y,int w,Component label,Runnable action){addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,w,20).build());}
    private void change(int next){try{capture();page=next;fields.clear();rebuildWidgets();}catch(Exception e){error=tr("invalid_number").getString();}}
    @Override protected void init(){fields.clear();int x=Math.max(15,width/2-165),w=Math.min(330,width-30),perPage=Math.max(1,(height-160)/24);var keys=keys();int y=40;
        for(String key:keys.stream().skip((long)page*perPage).limit(perPage).toList()){var edit=addRenderableWidget(new EditBox(font,x+w/2,y,w/2,20,tr(key)));edit.setMaxLength(key.equals("name")?80:48);edit.setValue(draft.contains(key,99)?Double.toString(draft.getDouble(key)):draft.getString(key));fields.put(key,edit);y+=24;}
        String kind="behavior";var choices=List.of("melee","guard","wander","script");
        button(x,height-112,w,tr(kind).copy().append(": ").append(tr(draft.getString(kind))),()->{try{capture();int next=(choices.indexOf(draft.getString(kind))+1)%choices.size();draft.putString(kind,choices.get(next));rebuildWidgets();}catch(Exception e){error=tr("invalid_number").getString();}});
        String toggle="countsForWave";button(x,height-88,w,tr(toggle).copy().append(": ").append(Component.literal(draft.getBoolean(toggle)?"On":"Off")),()->{try{capture();draft.putBoolean(toggle,!draft.getBoolean(toggle));rebuildWidgets();}catch(Exception e){error=tr("invalid_number").getString();}});
        button(x,height-60,48,Component.literal("<"),()->change(Math.max(0,page-1)));button(x+52,height-60,48,Component.literal(">"),()->change((page+1)*perPage<keys.size()?page+1:page));
        button(x,height-28,w/2-2,Component.literal("Done"),()->{try{capture();accept.accept(draft.copy());minecraft.setScreen(parent);}catch(Exception e){error=tr("invalid_number").getString();}});
        button(x+w/2+2,height-28,w/2-2,Component.literal("Cancel"),this::onClose);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xFF10151D);g.drawCenteredString(font,title,width/2,14,0xFFFFFF);super.render(g,mx,my,partial);int y=46,x=Math.max(15,width/2-165),w=Math.min(330,width-30);for(String key:fields.keySet()){g.drawString(font,font.plainSubstrByWidth(tr(key).getString(),w/2-6),x,y,0xE0EAF2);y+=24;}if(!error.isEmpty())g.drawCenteredString(font,error,width/2,height-40,0xFF8888);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
