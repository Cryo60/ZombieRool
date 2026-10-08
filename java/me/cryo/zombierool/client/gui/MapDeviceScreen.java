package me.cryo.zombierool.client.gui;

import java.util.*;
import me.cryo.zombierool.block.system.MapDevicePackets;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Searchable registry browser; mod particles may take the same arguments as /particle. */
public final class MapDeviceScreen extends Screen {
    private final MapDevicePackets.Open packet;
    private final CompoundTag values;
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private final List<String> catalog;
    private List<String> filtered=List.of();
    private String query="", category="Vanilla", error="";
    private int formPage, listPage, left, top, panelWidth;
    private EditBox effect;
    private final List<Button> rows=new ArrayList<>();
    private final List<String[]> specs=new ArrayList<>();
    private MapDeviceScreen(MapDevicePackets.Open p) {
        super(Component.translatable("block.zombierool." + switch(p.kind()) { case "SOUND" -> "sound_emitter"; case "PARTICLE" -> "particle_emitter"; case "CONTROL" -> "trap_controller"; case "ELECTRIC" -> "electric_trap"; case "FIRE" -> "fire_pit"; default -> "turret"; }));
        packet=p; values=p.config().copy(); catalog=new ArrayList<>(p.catalog());
        if(p.kind().equals("SOUND")) {
            specs.addAll(List.of(new String[]{"volume","gui.zombierool.device.volume"},new String[]{"pitch","gui.zombierool.device.pitch"},new String[]{"range","gui.zombierool.device.range"},new String[]{"interval","gui.zombierool.device.interval"}));
        } else if(p.kind().equals("PARTICLE")) {
            specs.addAll(List.of(new String[]{"count","gui.zombierool.device.count"},new String[]{"spread","gui.zombierool.device.spread"},new String[]{"speed","gui.zombierool.device.speed"},new String[]{"range","gui.zombierool.device.range"},new String[]{"interval","gui.zombierool.device.interval"}));
        } else {
            specs.add(new String[]{"channel","gui.zombierool.device.channel"});
            if(p.kind().equals("CONTROL") || p.kind().equals("TURRET")) specs.addAll(List.of(new String[]{"cost","gui.zombierool.device.cost"},new String[]{"duration","gui.zombierool.device.duration"},new String[]{"recharge","gui.zombierool.device.recharge"}));
            if(!p.kind().equals("CONTROL")) specs.addAll(List.of(new String[]{"range","gui.zombierool.device.range"},new String[]{"damage","gui.zombierool.device.damage"}));
            if(p.kind().equals("TURRET")) specs.add(new String[]{"interval","gui.zombierool.device.interval"});
        }
    }
    public static void open(MapDevicePackets.Open p) { Minecraft.getInstance().setScreen(new MapDeviceScreen(p)); }
    private static Component tr(String key) { return Component.translatable("gui.zombierool.device."+key); }
    private boolean emitter() { return packet.kind().equals("SOUND") || packet.kind().equals("PARTICLE"); }
    @Override protected void init() {
        fields.clear(); rows.clear(); panelWidth=Math.min(620,width-24); left=(width-panelWidth)/2; top=12;
        int fieldWidth=emitter() ? panelWidth/2-16 : panelWidth-16;
        if(emitter()) {
            effect=new EditBox(font,left+8,top+30,panelWidth-16,20,tr("effect")); effect.setMaxLength(512); effect.setValue(values.getString("effect")); addRenderableWidget(effect);
            EditBox search=new EditBox(font,left+panelWidth/2+4,top+62,panelWidth/2-12,20,tr("search")); search.setValue(query); search.setResponder(s -> {query=s; listPage=0; filter();}); addRenderableWidget(search);
            String[] categories=new String[]{"Vanilla","Mods","Custom"};
            int cw=(panelWidth/2-12)/categories.length;
            for(int i=0;i<categories.length;i++) { String c=categories[i]; addRenderableWidget(Button.builder(tr("category."+c.toLowerCase(Locale.ROOT)),b -> {category=c;listPage=0;filter();}).bounds(left+panelWidth/2+4+i*cw,top+86,cw-2,18).build()); }
            int visible=Math.max(1,Math.min(6,(height-top-168)/22));
            for(int i=0;i<visible;i++) { final int index=i; rows.add(addRenderableWidget(Button.builder(Component.empty(),b -> {int n=listPage*rows.size()+index; if(n<filtered.size()) effect.setValue(filtered.get(n));}).bounds(left+panelWidth/2+4,top+108+i*22,panelWidth/2-12,20).build())); }
            addRenderableWidget(Button.builder(Component.literal("<"),b -> {listPage=Math.max(0,listPage-1);filter();}).bounds(left+panelWidth/2+4,height-49,28,18).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b -> {if((listPage+1)*rows.size()<filtered.size())listPage++;filter();}).bounds(left+panelWidth-36,height-49,28,18).build()); filter();
        }
        int visibleFields=Math.max(1,(height-top-139)/38);
        formPage=Math.min(formPage,Math.max(0,(specs.size()-1)/visibleFields));
        for(int i=formPage*visibleFields;i<Math.min(specs.size(),(formPage+1)*visibleFields);i++) {
            String key=specs.get(i)[0]; EditBox box=new EditBox(font,left+8,top+78+(i%visibleFields)*38,fieldWidth,20,Component.translatable(specs.get(i)[1])); box.setMaxLength(64);
            box.setValue(key.equals("channel") ? values.getString(key) : values.get(key).getAsString()); fields.put(key,box); addRenderableWidget(box);
        }
        if(specs.size()>visibleFields) addRenderableWidget(Button.builder(tr("next"),b -> {if(capture()){formPage=(formPage+1)%((specs.size()+visibleFields-1)/visibleFields);rebuildWidgets();}}).bounds(left+8,height-70,fieldWidth,18).build());
        addRenderableWidget(Button.builder(tr(values.getBoolean("enabled") ? "enabled" : "disabled"),b -> {values.putBoolean("enabled",!values.getBoolean("enabled"));b.setMessage(tr(values.getBoolean("enabled") ? "enabled" : "disabled"));}).bounds(left+8,height-49,Math.min(100,fieldWidth),18).build());
        if(!emitter()) addRenderableWidget(Button.builder(tr(values.getBoolean("requires_power") ? "power_on" : "power_off"),b -> {values.putBoolean("requires_power",!values.getBoolean("requires_power"));b.setMessage(tr(values.getBoolean("requires_power") ? "power_on" : "power_off"));}).bounds(left+116,height-49,Math.max(90,panelWidth-124),18).build());
        int bw=(panelWidth-24)/(emitter()?3:2);
        addRenderableWidget(Button.builder(tr("save"),b -> save(false)).bounds(left+8,height-26,bw,20).build());
        if(emitter()) addRenderableWidget(Button.builder(tr("preview"),b -> save(true)).bounds(left+12+bw,height-26,bw,20).build());
        addRenderableWidget(Button.builder(tr("close"),b -> onClose()).bounds(left+panelWidth-8-bw,height-26,bw,20).build());
    }
    private void filter() {
        filtered=catalog.stream().filter(s -> category.equals("Custom") ? s.startsWith("zombierool:custom/") : category.equals("Vanilla") ? s.startsWith("minecraft:") : !s.startsWith("minecraft:") && !s.startsWith("zombierool:custom/"))
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted().toList();
        for(int i=0;i<rows.size();i++) { int n=listPage*rows.size()+i; Button b=rows.get(i); b.active=n<filtered.size(); String name=b.active?filtered.get(n):"—"; b.setMessage(Component.literal(font.plainSubstrByWidth(name,b.getWidth()-8))); b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name))); }
    }
    private boolean capture() {
        try {
            for(var entry:fields.entrySet()) {
                String k=entry.getKey(),v=entry.getValue().getValue(); if(k.equals("channel"))values.putString(k,v);
                else if(Set.of("interval","count","cost","duration","recharge").contains(k))values.putInt(k,Integer.parseInt(v));
                else {float f=Float.parseFloat(v);if(!Float.isFinite(f))throw new NumberFormatException();values.putFloat(k,f);}
            }
            if(emitter()) values.putString("effect",effect.getValue()); error="";return true;
        }catch(NumberFormatException e){error=net.minecraft.client.resources.language.I18n.get("gui.zombierool.device.invalid_number");return false;}
    }
    private void save(boolean preview) { if(capture()) { NetworkHandler.INSTANCE.sendToServer(new MapDevicePackets.Save(packet.pos(),values.copy(),preview)); if(!preview)onClose(); } }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);g.fill(left,top-4,left+panelWidth,height-2,0xED101821);g.drawString(font,title,left+8,top+4,0x77DDDD);
        if(emitter())g.drawString(font,tr("effect"),left+8,top+20,0xA8B9C6);
        int i=0;for(var e:fields.entrySet()){String label=specs.stream().filter(s -> s[0].equals(e.getKey())).findFirst().get()[1];g.drawString(font,Component.translatable(label),left+8,top+66+(i++)*38,0xD4DFE8);}
        if(!emitter())g.drawString(font,tr("hint"),left+8,top+30,0xA8B9C6);
        if(!error.isEmpty())g.drawString(font,error,left+8,height-82,0xFF7777);
        super.render(g,mx,my,partial);
    }
    @Override public boolean isPauseScreen(){return false;}
}
