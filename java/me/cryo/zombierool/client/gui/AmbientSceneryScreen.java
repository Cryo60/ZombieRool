package me.cryo.zombierool.client.gui;

import java.util.*;
import me.cryo.zombierool.block.system.MapDevicePackets;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** All configuration goes through the same permission-checked map device packet. */
public final class AmbientSceneryScreen extends Screen {
    private final MapDevicePackets.Open packet;private final CompoundTag config,scene;private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private int left,top,w,page;private String error="";
    public AmbientSceneryScreen(MapDevicePackets.Open p){super(Component.translatable("gui.zombierool.scenery."+p.kind().toLowerCase(Locale.ROOT)));packet=p;config=p.config().copy();scene=config.getCompound("scenery");}
    private Component tr(String key){return Component.translatable("gui.zombierool.scenery."+key);}
    private void button(int y,int x,int width,Component text,Runnable action){addRenderableWidget(Button.builder(text,b->action.run()).bounds(x,y,width,20).build());}
    private List<String> keys(){return packet.kind().equals("VEHICLE")?List.of("yaw","pitch","roll","size"):packet.kind().equals("DCA")?List.of("interval","tracerMinHeight","tracerMaxHeight","volume","trackingTurnSpeed"):packet.kind().equals("AIRCRAFT")?java.util.Set.of("airship","necropolis").contains(scene.getString("variant"))?List.of("altitude","heading","pathLength","flightSpeed","size","amount","volume"):List.of("altitude","radius","flightSpeed","size","amount","interval","volume"):List.of("flightSpeed","size","amount");}
    @Override protected void init(){fields.clear();w=Math.min(520,width-24);left=(width-w)/2;top=20;
        button(top+18,left,w/2-3,tr(config.getBoolean("enabled")?"enabled":"disabled"),()->{capture();config.putBoolean("enabled",!config.getBoolean("enabled"));rebuildWidgets();});
        button(top+18,left+w/2+3,w/2-3,tr(config.getBoolean("requires_power")?"requiresPower":"always"),()->{capture();config.putBoolean("requires_power",!config.getBoolean("requires_power"));rebuildWidgets();});
        int rows=Math.max(1,(height-top-188)/38),pages=(keys().size()+rows-1)/rows;page=Math.min(page,pages-1);
        for(int i=page*rows;i<Math.min(keys().size(),(page+1)*rows);i++){String key=keys().get(i);if(packet.kind().equals("VEHICLE")&&java.util.Set.of("yaw","pitch","roll").contains(key)){double limit=key.equals("yaw")?360:180;int y=top+64+(i%rows)*38;addRenderableWidget(new net.minecraft.client.gui.components.AbstractSliderButton(left+38,y,w-76,20,Component.empty(),(scene.getDouble(key)+limit)/(2*limit)){
                {updateMessage();}protected void updateMessage(){setMessage(Component.literal(String.format(java.util.Locale.ROOT,"%.0f\u00b0",scene.getDouble(key))));}protected void applyValue(){scene.putDouble(key,Math.rint((value*2-1)*limit));updateMessage();}
            });
            button(y,left,34,Component.literal("−5°"),()->{scene.putDouble(key,Math.max(-limit,scene.getDouble(key)-5));rebuildWidgets();});
            button(y,left+w-34,34,Component.literal("+5°"),()->{scene.putDouble(key,Math.min(limit,scene.getDouble(key)+5));rebuildWidgets();});
            continue;}var box=new EditBox(font,left,top+64+(i%rows)*38,w,20,tr(key));box.setMaxLength(24);box.setValue(key.equals("interval")?Integer.toString(config.getInt(key)):key.equals("amount")?Integer.toString(scene.getInt(key)):key.equals("volume")?Float.toString(config.getFloat(key)):Double.toString(scene.getDouble(key)));addRenderableWidget(box);fields.put(key,box);}
        if(pages>1)button(height-128,left,w/2-3,Component.literal("<  "+(page+1)+" / "+pages+"  >"),()->{if(capture()){page=(page+1)%pages;rebuildWidgets();}});
        if(packet.kind().equals("VEHICLE")){
            button(top+40,left,w/2-3,tr("orientHere"),()->{if(minecraft.player!=null){scene.putDouble("yaw",180-minecraft.player.getYRot());scene.putDouble("pitch",0);scene.putDouble("roll",0);rebuildWidgets();}});
            button(top+40,left+w/2+3,w/2-3,tr("resetAngles"),()->{for(String angle:List.of("yaw","pitch","roll"))scene.putDouble(angle,0);rebuildWidgets();});
            var states=me.cryo.zombierool.gameplay.AmbientScenery.states(scene.getString("vehicle"));
            button(height-104,left,w,tr("variant."+scene.getString("variant")),()->{if(capture()){scene.putString("variant",states.get(Math.floorMod(states.indexOf(scene.getString("variant"))+1,states.size())));rebuildWidgets();}});
            if(scene.getString("vehicle").equals("car"))button(height-80,left,w,tr("carModel."+scene.getString("carModel")),this::chooseCar);
            else if(!scene.getString("vehicle").equals("helicopter")&&!me.cryo.zombierool.gameplay.AmbientScenery.transports().contains(scene.getString("vehicle")))button(height-80,left,w,tr("era."+scene.getString("era")),()->{if(capture()){scene.putString("era",scene.getString("era").equals("modern")?"legacy":"modern");rebuildWidgets();}});
        }
        else if(packet.kind().equals("DCA")){button(top+40,left+w-180,180,tr("model."+scene.getString("dcaModel")),()->{if(capture()){var models=List.of("flak88","light20","quad14","modern","dwarven");scene.putString("dcaModel",models.get((models.indexOf(scene.getString("dcaModel"))+1)%models.size()));rebuildWidgets();}});button(height-104,left,w,tr("fireMode."+scene.getString("fireMode")),()->{if(capture()){var modes=List.of("regular","varied","tracking");scene.putString("fireMode",modes.get((modes.indexOf(scene.getString("fireMode"))+1)%modes.size()));rebuildWidgets();}});button(height-80,left,w,tr("targetTool"),this::requestTool);}
        else{button(height-80,left,w/2-3,tr("variant."+scene.getString("variant")),()->{if(capture()){var variants=packet.kind().equals("AIRCRAFT")?List.of("normal","airship","necropolis"):List.of("normal","fire");String selected=variants.get((variants.indexOf(scene.getString("variant"))+1)%variants.size());scene.putString("variant",selected);if(selected.equals("necropolis")){scene.putDouble("size",24);scene.putDouble("altitude",40);scene.putDouble("flightSpeed",0);scene.putInt("amount",1);}else if(scene.getDouble("size")>4){scene.putDouble("size",1);scene.putDouble("flightSpeed",8);}rebuildWidgets();}});
            if(packet.kind().equals("AIRCRAFT")&&scene.getString("variant").equals("airship"))button(height-80,left+w/2+3,w/2-3,tr(scene.getBoolean("loop")?"loop":"once"),()->{if(capture()){scene.putBoolean("loop",!scene.getBoolean("loop"));rebuildWidgets();}});
            if(packet.kind().equals("TORNADO")){button(top+40,left+w-180,180,tr("routeTool"),this::requestTool);var route=scene.getList("route",10);button(height-104,left,w/2-3,tr("addPoint").copy().append(" ("+route.size()+"/32)"),()->{if(capture()&&minecraft.player!=null&&route.size()<32){var at=minecraft.player.position();var n=new CompoundTag();n.putDouble("x",at.x);n.putDouble("y",at.y);n.putDouble("z",at.z);route.add(n);scene.put("route",route);rebuildWidgets();}});button(height-104,left+w/2+3,w/2-3,tr("undoPoint"),()->{if(capture()&&!route.isEmpty()){route.remove(route.size()-1);rebuildWidgets();}});button(height-80,left+w/2+3,w/2-3,tr(scene.getBoolean("loop")?"loop":"once"),()->{if(capture()){scene.putBoolean("loop",!scene.getBoolean("loop"));rebuildWidgets();}});}
        }
        button(height-56,left,w,Component.translatable("gui.zombierool.block_power.button"),()->{if(capture())BlockPowerScreen.request(packet.pos(),this,null);});
        button(height-30,left,w/2-3,Component.translatable("gui.zombierool.device.save"),()->{if(capture()){config.put("scenery",scene);NetworkHandler.INSTANCE.sendToServer(new MapDevicePackets.Save(packet.pos(),config.copy(),false));onClose();}});button(height-30,left+w/2+3,w/2-3,Component.translatable("gui.back"),this::onClose);
    }
    private void chooseCar(){
        if(!capture())return;var parent=this;
        minecraft.setScreen(new Screen(tr("carModels")){
            @Override protected void init(){int x=width/2-150,y=Math.max(30,height/2-110);var models=me.cryo.zombierool.gameplay.AmbientScenery.carModels();
                for(int i=0;i<models.size();i++){String model=models.get(i);addRenderableWidget(Button.builder(tr("carModel."+model),b->{scene.putString("carModel",model);minecraft.setScreen(parent);}).bounds(x+(i%2)*154,y+(i/2)*28,146,22).build());}
                addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,y+122,300,20).build());
            }
            @Override public void onClose(){minecraft.setScreen(parent);}
            @Override public boolean isPauseScreen(){return false;}
            @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,Math.max(12,height/2-140),0xFFFFFF);super.render(g,mx,my,partial);}
        });
    }
    private void requestTool(){if(!capture())return;config.put("scenery",scene);var request=config.copy();request.putBoolean("giveRouteTool",true);NetworkHandler.INSTANCE.sendToServer(new MapDevicePackets.Save(packet.pos(),request,false));onClose();}
    private boolean capture(){try{for(var e:fields.entrySet()){double value=Double.parseDouble(e.getValue().getValue());if(!Double.isFinite(value))throw new NumberFormatException();if(e.getKey().equals("interval"))config.putInt(e.getKey(),(int)value);else if(e.getKey().equals("volume"))config.putFloat("volume",(float)Math.max(0,Math.min(4,value)));else if(e.getKey().equals("amount"))scene.putInt(e.getKey(),(int)value);else scene.putDouble(e.getKey(),value);}error="";return true;}catch(NumberFormatException e){error=Component.translatable("gui.zombierool.device.invalid_number").getString();return false;}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.fill(left-8,top-12,left+w+8,height-4,0xF014202C);g.drawCenteredString(font,title,width/2,top-7,0xFFFFFF);int rows=Math.max(1,(height-top-188)/38);for(int i=page*rows;i<Math.min(keys().size(),(page+1)*rows);i++)g.drawString(font,tr(keys().get(i)),left,top+53+(i%rows)*38,0xBFD2E0);if(!error.isEmpty())g.drawString(font,error,left,top+40,0xFF7777);super.render(g,mx,my,partial);}
}
