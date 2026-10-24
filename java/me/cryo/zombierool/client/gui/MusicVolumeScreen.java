package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.block.system.MusicVolume;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Form controls with a searchable music catalogue. No JSON required. */
public final class MusicVolumeScreen extends Screen {
    private final BlockPos pos;private final CompoundTag values;private final Map<String,EditBox> fields=new LinkedHashMap<>();private int left,top,page;private String error="";
    public MusicVolumeScreen(BlockPos pos,CompoundTag n){super(Component.literal("Music Volume"));this.pos=pos;values=n.copy();}
    private void capture(){fields.forEach((key,box)->{if(key.equals("name")||key.equals("preset")||key.equals("id"))values.putString(key,box.getValue().trim());else{double v=Double.parseDouble(box.getValue());if(!Double.isFinite(v))throw new IllegalArgumentException("Enter a valid number");if(key.equals("volume"))values.putDouble(key,v);else values.putInt(key,(int)Math.round(v));}});}
    @Override protected void init(){left=width/2-160;top=Math.max(4,(height-235)/2);fields.clear();int y=top+44;
        var keys=page==0?List.of("name","preset","radiusX","radiusZ","height"):List.of("id","priority","volume","fadeTicks");
        for(String key:keys){var box=new EditBox(font,left+155,y,150,20,Component.literal(key));box.setMaxLength(key.equals("preset")?160:key.equals("name")||key.equals("id")?64:12);box.setValue(key.equals("name")||key.equals("preset")||key.equals("id")?values.getString(key):key.equals("volume")?Double.toString(values.getDouble(key)):Integer.toString(values.getInt(key)));fields.put(key,addRenderableWidget(box));y+=24;}
        if(page==0)addRenderableWidget(Button.builder(Component.literal("Choose music..."),b->{try{capture();minecraft.setScreen(new Picker());}catch(Exception ex){error=ex.getMessage();}}).bounds(left+12,top+168,293,20).build());
        else{addRenderableWidget(Button.builder(Component.literal("Enabled: "+values.getBoolean("enabled")),b->{values.putBoolean("enabled",!values.getBoolean("enabled"));b.setMessage(Component.literal("Enabled: "+values.getBoolean("enabled")));}).bounds(left+12,top+147,293,20).build());}
        addRenderableWidget(Button.builder(Component.literal(page==0?"Playback / priority >":"< Zone dimensions"),b->{try{capture();page=1-page;rebuildWidgets();}catch(Exception ex){error=ex.getMessage();}}).bounds(left+12,top+192,293,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"),b->{try{capture();MusicVolume.save(pos,MusicVolume.sanitize(values));onClose();}catch(Exception ex){error=ex.getMessage();}}).bounds(left+165,top+214,140,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+12,top+214,140,20).build());
    }
    private static String label(String key){return switch(key){case "id"->"Lua ID";case "radiusX"->"X half-width (blocks)";case "radiusZ"->"Z half-width (blocks)";case "height"->"Height (blocks)";case "fadeTicks"->"Fade (ticks; 20 = 1s)";case "preset"->"Music / playlist";case "priority"->"Priority (higher wins)";case "volume"->"Volume multiplier";default->"Name";};}
    @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g);g.fill(left,top,left+320,top+238,0xf0182028);g.drawCenteredString(font,title,width/2,top+9,0xffffff);g.drawString(font,Component.literal(page==0?"Enter to switch. Leave and reenter to switch back.":"Equal priority: the smallest containing volume wins."),left+12,top+27,0xb8cbd8);int at=top+50;for(String key:fields.keySet()){g.drawString(font,Component.literal(label(key)),left+12,at,0xffffff);at+=24;}if(!error.isEmpty())g.drawString(font,Component.literal(error),left+12,top+147,0xff7777);super.render(g,x,y,delta);}
    @Override public boolean isPauseScreen(){return false;}
    private final class Picker extends Screen {
        private String query="";private int offset;
        Picker(){super(Component.literal("Choose music"));}
        @Override protected void init(){var search=addRenderableWidget(new EditBox(font,20,30,width-40,20,Component.literal("Search music")));search.setValue(query);search.setResponder(s->{query=s;offset=0;rebuildWidgets();});var ids=new TreeSet<String>();ids.addAll(List.of("default","damned","none"));ids.addAll(me.cryo.zombierool.client.DynamicSoundLoader.musicPresets());ids.addAll(me.cryo.zombierool.client.DynamicSoundLoader.customAudioIds());var filtered=ids.stream().filter(s->s.contains(query.toLowerCase(Locale.ROOT))).toList();int count=Math.max(1,(height-116)/23),y=58;for(String id:filtered.stream().skip((long)offset*count).limit(count).toList()){addRenderableWidget(Button.builder(Component.literal(id),b->{values.putString("preset",id);minecraft.setScreen(MusicVolumeScreen.this);}).bounds(20,y,width-40,20).build());y+=23;}addRenderableWidget(Button.builder(Component.literal("<"),b->{offset=Math.max(0,offset-1);rebuildWidgets();}).bounds(20,height-49,40,20).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{if((offset+1)*count<filtered.size())offset++;rebuildWidgets();}).bounds(66,height-49,40,20).build());addRenderableWidget(Button.builder(Component.literal("Back"),b->minecraft.setScreen(MusicVolumeScreen.this)).bounds(width-110,height-49,90,20).build());}
        @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g);g.drawCenteredString(font,title,width/2,10,0xffffff);g.drawString(font,Component.literal("Custom playlists: zombierool/audio/music/<playlist>/*.ogg"),20,height-22,0xb8cbd8);super.render(g,x,y,delta);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
