package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;

/** One setup page, a searchable machine pool, and a separate animation page. */
public final class MachineDeliveryScreen extends Screen {
    private static Screen returnTo;private final Screen parent;private final boolean sky;
    private final BlockPos pos;private final CompoundTag values;private final List<String> catalog=new ArrayList<>();
    private int tab,page,left,panelWidth,rowY;private String selected="",query="";private boolean invalid;
    private final Map<String,EditBox> numbers=new HashMap<>();
    private MachineDeliveryScreen(BlockPos p,CompoundTag n){super(tr(n.getBoolean("skySettings")?"sky_title":"site_title"));sky=n.getBoolean("skySettings");parent=sky?returnTo:null;pos=p;values=n.copy();for(Tag t:n.getList("catalog",10))catalog.add(((CompoundTag)t).getString("id"));}
    public static void open(BlockPos p,CompoundTag n){if(n!=null)Minecraft.getInstance().setScreen(new MachineDeliveryScreen(p,n));}
    public static void requestSky(Screen parent){returnTo=parent;MachineDeliveryPackets.request(MachineDeliverySystem.SKY_POS,"open",new CompoundTag());}
    @Override public void onClose(){minecraft.setScreen(parent);}
    public static Component tr(String k){return Component.translatable("gui.zombierool.delivery."+k);}
    private Component name(String id){return id.equals(MachineDeliverySystem.PACK)?Component.translatable("block.zombierool.pack_a_punch"):Component.translatable("perk.zombierool."+id+".name");}
    private int right(){return left+panelWidth-12;}
    private void row(Component label,java.util.function.IntConsumer widget){int y=rowY;rowY+=26;labels.add(new Label(label,y));widget.accept(y-4);}
    private record Label(Component text,int y){}private final List<Label> labels=new ArrayList<>();
    @Override protected void init(){
        clearWidgets();numbers.clear();labels.clear();panelWidth=Math.min(620,width-24);left=(width-panelWidth)/2;rowY=79;
        String[] tabs={"setup","machines","animation"};for(int i=0;i<3;i++){final int t=i;addRenderableWidget(Button.builder(tr(tabs[i]),b->{if(capture()){tab=t;page=0;rebuildWidgets();}}).bounds(left+8+i*(panelWidth-16)/3,36,(panelWidth-22)/3,20).build());}
        if(tab==0)setup();else if(tab==1)machines();else animation();
        if(!sky&&values.getString("role").equals("landing")){for(var child:children())if(child instanceof Button button&&(button.getMessage().equals(tr("machines"))||button.getMessage().equals(tr("animation"))))button.active=false;}
        int w=(panelWidth-32)/4;addRenderableWidget(Button.builder(tr("save"),b->{if(capture()){MachineDeliveryPackets.request(pos,"save",values);onClose();}}).bounds(left+8,height-30,w,20).build());
        var preview=addRenderableWidget(Button.builder(tr("preview"),b->{if(capture()){MachineDeliveryPackets.request(pos,"preview",values);onClose();}}).bounds(left+12+w,height-30,w,20).build());preview.active=!values.getString("role").equals("landing");
        addRenderableWidget(Button.builder(tr(sky?"reset_sky":"reset_site"),b->MachineDeliveryPackets.request(pos,"reset",values)).bounds(left+16+2*w,height-30,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+20+3*w,height-30,w,20).build());
    }
    private void setup(){
        if(sky){
            toggle("enabled");slider("firstRound",1,100,false);slider("everyRounds",1,50,false);toggle("noRepeats");
            row(tr("sky_sites"),y->{});
        }else if(page==0){
            row(tr("site_use"),y->addRenderableWidget(Button.builder(tr(values.getString("role").equals("landing")?"option.landing_site":"option.floating_roulette"),b->pick("site_use",List.of("landing_site","floating_roulette"),id->{boolean landing=id.equals("landing_site");values.putString("role",landing?"landing":"controller");values.putString("mode",landing?"drop":"roll");if(!landing)values.putString("trigger","door_channel");rebuildWidgets();})).bounds(right()-250,y,250,20).build()));
            toggle("enabled");if(!values.getString("role").equals("landing")){select("trigger",List.of("door_channel","signal","manual"));if(values.getString("trigger").equals("door_channel"))number("doorChannel");if(values.getString("trigger").equals("signal"))text("channel");toggle("noRepeats");}
        }
    }
    private Set<String> pool(){var set=new HashSet<String>();for(Tag t:values.getList("pool",Tag.TAG_STRING))set.add(t.getAsString());return set;}
    private void machines(){
        var search=new EditBox(font,left+12,69,panelWidth-24,20,tr("search"));search.setHint(tr("search"));search.setValue(query);search.setResponder(s->{query=s;page=0;rebuildWidgets();setFocusedSearch();});addRenderableWidget(search);
        var filtered=catalog.stream().filter(id->(id+" "+name(id).getString()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();int visible=Math.max(1,(height-193)/24);page=Math.min(page,Math.max(0,(filtered.size()-1)/visible));var enabled=pool();
        for(int i=page*visible;i<Math.min(filtered.size(),(page+1)*visible);i++){String id=filtered.get(i);int y=99+(i%visible)*24;
            addRenderableWidget(Button.builder(Component.literal(enabled.contains(id)?"✓ ":"— ").append(name(id)),b->{var set=pool();if(!set.add(id))set.remove(id);var p=new ListTag();catalog.stream().filter(set::contains).forEach(k->p.add(StringTag.valueOf(k)));values.put("pool",p);rebuildWidgets();}).bounds(left+12,y,panelWidth-130,20).build());
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(price(id))),b->{selected=id;tab=3;rebuildWidgets();}).bounds(right()-100,y,100,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left+12,height-65,40,20).build());
        addRenderableWidget(Button.builder(Component.literal("›"),b->{if((page+1)*visible<filtered.size())page++;rebuildWidgets();}).bounds(right()-40,height-65,40,20).build());
    }
    private void setFocusedSearch(){for(var widget:children())if(widget instanceof EditBox e&&e.getMessage().equals(tr("search"))){setFocused(e);e.moveCursorToEnd();break;}}
    private int price(String id){return values.getCompound("prices").contains(id)?values.getCompound("prices").getInt(id):MachineDeliverySystem.defaultPrice(id);}
    private void animation(){if(tab==3){row(name(selected),y->{var box=new EditBox(font,right()-220,y,220,20,tr("price"));box.setValue(Integer.toString(price(selected)));box.setResponder(s->{try{int p=Integer.parseInt(s);if(p>=0)values.getCompound("prices").putInt(selected,p);}catch(NumberFormatException ignored){}});addRenderableWidget(box);});return;}slider("duration",1,30,true);if(sky)slider("dropHeight",4,192,false);else slider("hoverHeight",.1,3,false);}
    private void text(String key){row(tr(key),y->{var b=new EditBox(font,right()-220,y,220,20,tr(key));b.setMaxLength(64);b.setValue(values.getString(key));b.setResponder(s->values.putString(key,s));addRenderableWidget(b);});}
    private void number(String key){row(tr(key),y->{var b=new EditBox(font,right()-220,y,220,20,tr(key));b.setValue(Integer.toString(values.getInt(key)));numbers.put(key,b);addRenderableWidget(b);});}
    private boolean capture(){invalid=false;for(var e:numbers.entrySet())try{values.putInt(e.getKey(),Integer.parseInt(e.getValue().getValue()));}catch(NumberFormatException ex){invalid=true;}return !invalid;}
    private void toggle(String key){row(tr(key),y->addRenderableWidget(Button.builder(Component.translatable(values.getBoolean(key)?"options.on":"options.off"),b->{values.putBoolean(key,!values.getBoolean(key));b.setMessage(Component.translatable(values.getBoolean(key)?"options.on":"options.off"));}).bounds(right()-220,y,220,20).build()));}
    private void select(String key,List<String> options){row(tr(key),y->addRenderableWidget(Button.builder(tr("option."+values.getString(key)),b->pick(key,options,id->{values.putString(key,id);rebuildWidgets();})).bounds(right()-220,y,220,20).build()));}
    private void pick(String key,List<String> options,java.util.function.Consumer<String> accept){var library=new ListTag();for(String id:options){var n=new CompoundTag();n.putString("id",id);n.putString("name",tr("option."+id).getString());library.add(n);}minecraft.setScreen(new AtmospherePresetPickerScreen(this,library,values.getString(key),accept,tr(key),false));}
    private void slider(String key,double min,double max,boolean seconds){row(tr(key),y->addRenderableWidget(new AbstractSliderButton(right()-220,y,220,20,Component.empty(),Math.max(0,Math.min(1,((seconds?values.getInt(key)/20d:values.getDouble(key))-min)/(max-min)))){
        {updateMessage();}protected void updateMessage(){setMessage(Component.literal(String.format(Locale.ROOT,key.equals("hoverHeight")?"%.1f":seconds?"%.1f s":"%.0f",min+value*(max-min))));}protected void applyValue(){double n=min+value*(max-min);if(key.equals("dropHeight")||key.equals("hoverHeight"))values.putDouble(key,n);else values.putInt(key,(int)Math.round(n*(seconds?20:1)));}
    }));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.fill(left,10,left+panelWidth,height-6,0xF0101925);g.drawCenteredString(font,title,width/2,18,0xFFFFFF);for(var l:labels)g.drawString(font,l.text,left+12,l.y,0xD6E5F3);if(sky&&tab==0)g.drawString(font,tr("sky_sites_hint"),right()-250,183,0x9DAFBE);if(!sky&&tab==0&&page==0)g.drawString(font,font.plainSubstrByWidth(tr("placement").getString(),panelWidth-24),left+12,height-84,0x9DAFBE);g.drawString(font,tr("phase."+values.getString("phase")).copy().append(" · "+values.getInt("deliveries")),left+12,height-104,0x9DAFBE);if(invalid)g.drawString(font,tr("invalid"),left+12,height-124,0xFF7777);super.render(g,mx,my,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
