package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.block.system.LunarZonePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LunarZoneScreen extends Screen {
    private final BlockPos pos;private final CompoundTag values;private final Map<String,EditBox> fields=new LinkedHashMap<>();private int left,top;private String error="";
    private LunarZoneScreen(BlockPos pos,CompoundTag config){super(tr("title"));this.pos=pos;values=config.copy();}
    private static Component tr(String k){return Component.translatable("gui.zombierool.lunar_zone."+k);}
    public static void open(BlockPos pos,CompoundTag config){if(config!=null)Minecraft.getInstance().setScreen(new LunarZoneScreen(pos,config));}
    @Override protected void init(){left=width/2-150;top=Math.max(5,(height-236)/2);fields.clear();
        int i=0;for(String key:List.of("id","radiusX","radiusZ","height","gravity")){var box=new EditBox(font,left+164,top+40+i++*21,key.equals("id")?94:124,20,tr(key));box.setValue(key.equals("id")?values.getString(key):key.equals("gravity")?String.valueOf(values.getDouble(key)):String.valueOf(values.getInt(key)));box.setMaxLength(key.equals("id")?128:12);fields.put(key,addRenderableWidget(box));}
        addRenderableWidget(Button.builder(Component.literal("+ ID"),b->fields.get("id").setValue(me.cryo.zombierool.block.system.AtmosphereZoneBlock.Zone.randomId())).bounds(left+262,top+40,26,20).tooltip(Tooltip.create(tr("random_id"))).build());
        for(String key:List.of("enabled","vacuum")){int y=top+149+(key.equals("vacuum")?22:0);addRenderableWidget(Button.builder(tr(key+"."+values.getBoolean(key)),b->{values.putBoolean(key,!values.getBoolean(key));b.setMessage(tr(key+"."+values.getBoolean(key)));}).bounds(left+12,y,276,20).build());}
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->save()).bounds(left+154,top+211,134,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left+12,top+211,134,20).build());
    }
    private void save(){try{for(var e:fields.entrySet()){if(e.getKey().equals("id")){values.putString("id",e.getValue().getValue().trim());}else if(e.getKey().equals("gravity")){double v=Double.parseDouble(e.getValue().getValue());if(!Double.isFinite(v)||v<.02||v>4)throw new NumberFormatException();values.putDouble(e.getKey(),v);}else{int n=Integer.parseInt(e.getValue().getValue());if(n<1||n>256)throw new NumberFormatException();values.putInt(e.getKey(),n);}}LunarZonePackets.save(pos,values.copy());onClose();}catch(NumberFormatException e){error=tr("invalid").getString();}}
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);g.fill(left,top,left+300,top+236,0xf0182028);g.drawCenteredString(font,title,width/2,top+8,0xffffff);g.drawString(font,tr("hint"),left+12,top+25,0xb8cbd8);int i=0;for(String key:fields.keySet())g.drawString(font,tr(key),left+12,top+46+i++*21,0xffffff);if(!error.isEmpty())g.drawString(font,error,left+12,top+198,0xff7777);super.render(g,x,y,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
