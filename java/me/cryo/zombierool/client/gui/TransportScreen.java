package me.cryo.zombierool.client.gui;



import java.util.*;

import me.cryo.zombierool.block.system.*;

import me.cryo.zombierool.network.NetworkHandler;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.*;

import net.minecraft.client.gui.screens.Screen;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.network.chat.Component;



/** Named sections, persistent helper and destination picker; only Save sends the draft. */

public final class TransportScreen extends Screen {

    private final TransportPackets.Open packet;

    private CompoundTag draft;

    private final Map<String,EditBox> fields=new LinkedHashMap<>();

    private final Map<String,int[]> labels=new LinkedHashMap<>();

    private final Map<String,int[]> helpAreas=new LinkedHashMap<>();

    private int page,left,widthPanel;

    private String error="";

    private static final Set<String> BOOLEANS=Set.of("invisible","enabled","allMobs","requiresPower","requiresLink","allPlayers","automatic","relink","sharedCooldown","jumpOnly");

    private static final Set<String> INTS=Set.of("chargeTime","duration","cost","linkTimeout","stay","secretStay","cooldown","papLinks","requiredSwitches");

    private static final Set<String> DOUBLES=Set.of("clearance","secretChance","yaw");

    private static final String[] SECTIONS={"route","activation","linking","journey","advanced"};

    private static final String[][] TELEPORTER={

        {"mode","role","id","channel","destination","returnId"},

        {"enabled","requiresPower","requiresLink","cost","chargeTime","allPlayers"},

        {"linkTimeout","relink","cooldown","sharedCooldown","automatic","jumpOnly"},

        {"stay","secretIds","secretStay","secretChance","invisible","yaw"},

        {"condition","blockedCondition","requiredSwitches","papLinks"}

    };

    private static final String[] TRAVERSAL={"enabled","allMobs","duration","clearance"};

    public static void open(TransportPackets.Open p){if(p.config()!=null)Minecraft.getInstance().setScreen(new TransportScreen(p));}

    private TransportScreen(TransportPackets.Open p){super(Component.translatable("gui.zombierool.transport."+p.kind()));packet=p;draft=p.config().copy();}

    private static Component tr(String key){return Component.translatable("gui.zombierool.transport."+key);}

    private boolean traversal(){return packet.kind().equals("traversal");}

    private Component value(String key){

        if(BOOLEANS.contains(key))return tr(key).copy().append(": ").append(Component.translatable(draft.getBoolean(key)?"options.on":"options.off"));

        return tr(key).copy().append(": ").append(tr(draft.getString(key).toLowerCase(Locale.ROOT)));

    }

    private String[] keys(){

        if(traversal())return TRAVERSAL;

        boolean receiver=draft.getString("role").equals("RECEIVER"),sw=draft.getString("role").equals("SWITCH");

        if(page==0&&receiver)return new String[]{"mode","role","id","channel","invisible","yaw"};

        if(page==0&&sw)return new String[]{"role","id","channel","condition","enabled","requiresPower"};

        if(receiver)return page==1?new String[]{"enabled","requiresPower"}:page==2?new String[]{"cooldown","sharedCooldown"}:page==3?new String[]{"invisible","yaw"}:new String[]{"condition","blockedCondition"};

        return TELEPORTER[page];

    }

    @Override protected void init(){

        fields.clear();labels.clear();helpAreas.clear();widthPanel=Math.min(520,width-16);left=(width-widthPanel)/2;

        if(!traversal())for(int i=0;i<SECTIONS.length;i++){

            final int target=i;Button tab=Button.builder(tr("section."+SECTIONS[i]),b->{if(capture()){page=target;rebuildWidgets();}}).bounds(left+i*widthPanel/5,38,widthPanel/5-2,20).build();tab.active=i!=page;addRenderableWidget(tab);

        }

        int column=(widthPanel-8)/2;

        String[] keys=keys();

        for(int i=0;i<keys.length;i++){

            String key=keys[i];int x=left+(i%2)*(column+8),y=100+(i/2)*34;

            helpAreas.put(key,new int[]{x,y,column,32});

            Tooltip tooltip=Tooltip.create(tr(key+".help"));

            if(BOOLEANS.contains(key))addRenderableWidget(Button.builder(value(key),b->{draft.putBoolean(key,!draft.getBoolean(key));b.setMessage(value(key));}).bounds(x,y+10,column,20).tooltip(tooltip).build());

            else if(key.equals("mode"))addRenderableWidget(Button.builder(value(key),b->{

                if(!capture())return;

                var modes=TeleporterConfig.Mode.values();int next=(TeleporterConfig.Mode.valueOf(draft.getString("mode")).ordinal()+1)%modes.length;

                CompoundTag preset=TeleporterConfig.defaults(modes[next]);

                for(String keep:new String[]{"id","channel","destination","returnId","secretIds","role"})preset.putString(keep,draft.getString(keep));

                preset.putBoolean("invisible",draft.getBoolean("invisible"));draft=preset;rebuildWidgets();

            }).bounds(x,y+10,column,20).tooltip(tooltip).build());

            else if(key.equals("role")){

                Button role=Button.builder(value(key),b->{if(capture()){draft.putString(key,draft.getString(key).equals("SOURCE")?"RECEIVER":"SOURCE");rebuildWidgets();}}).bounds(x,y+10,column,20).tooltip(tooltip).build();role.active=!draft.getString(key).equals("SWITCH");addRenderableWidget(role);

            }else{

                labels.put(key,new int[]{x,y});boolean picker=Set.of("destination","returnId").contains(key);

                EditBox box=new EditBox(font,x,y+12,column-(picker?48:0),20,tr(key));box.setMaxLength(128);

                box.setValue(INTS.contains(key)?Integer.toString(draft.getInt(key)):DOUBLES.contains(key)?Double.toString(draft.getDouble(key)):draft.getString(key));

                box.setTooltip(tooltip);fields.put(key,box);addRenderableWidget(box);

                if(picker)addRenderableWidget(Button.builder(tr("pick"),b->{if(capture())Minecraft.getInstance().setScreen(new TargetScreen(this,key));}).bounds(x+column-46,y+12,46,20).build());

            }

        }

        addRenderableWidget(Button.builder(Component.translatable("zombierool.config.save"),b->{if(capture()){NetworkHandler.INSTANCE.sendToServer(new TransportPackets.Save(packet.pos(),draft.copy()));onClose();}}).bounds(left,height-26,column,20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left+column+8,height-26,column,20).build());

    }

    private boolean capture(){

        try{

            for(var entry:fields.entrySet()){

                String key=entry.getKey(),v=entry.getValue().getValue().trim();

                if(INTS.contains(key))draft.putInt(key,Integer.parseInt(v));

                else if(DOUBLES.contains(key)){double number=Double.parseDouble(v);if(!Double.isFinite(number))throw new NumberFormatException();draft.putDouble(key,number);}

                else draft.putString(key,v);

            }

            error="";return true;

        }catch(NumberFormatException e){error=net.minecraft.client.resources.language.I18n.get("gui.zombierool.device.invalid_number");return false;}

    }

    @Override public void resize(Minecraft minecraft,int w,int h){capture();super.resize(minecraft,w,h);}

    @Override public void render(GuiGraphics g,int mx,int my,float partial){

        renderBackground(g);g.drawCenteredString(font,title,width/2,18,0xF4F1E9);

        Component help=tr(traversal()?"helper.traversal":draft.getString("role").equals("RECEIVER")?"helper.receiver":draft.getString("role").equals("SWITCH")?"helper.switch":"helper."+SECTIONS[page]);

        for(var entry:helpAreas.entrySet()){int[] r=entry.getValue();if(mx>=r[0]&&mx<r[0]+r[2]&&my>=r[1]&&my<r[1]+r[3])help=tr(entry.getKey()+".help");}

        g.fill(left,62,left+widthPanel,96,0xAA123021);g.enableScissor(left+4,64,left+widthPanel-4,95);g.drawWordWrap(font,help,left+6,66,widthPanel-12,0xB8E8C9);g.disableScissor();

        labels.forEach((key,pos)->g.drawString(font,font.plainSubstrByWidth(tr(key).getString(),(widthPanel-8)/2),pos[0],pos[1],0xBBD0D8));

        if(traversal())g.drawCenteredString(font,Component.literal(draft.getString("route")),width/2,height-50,0x80D8E8);

        if(!error.isEmpty())g.drawCenteredString(font,error,width/2,height-40,0xFF7777);

        super.render(g,mx,my,partial);

    }

    @Override public boolean isPauseScreen(){return false;}

    private static final class TargetScreen extends Screen {

        private final TransportScreen parent;private final String key;private final List<String> ids;private int page;

        TargetScreen(TransportScreen parent,String key){super(tr("pick.title"));this.parent=parent;this.key=key;var list=parent.packet.config().getList(key.equals("returnId")?"_receiverIds":"_platformIds",8);ids=new ArrayList<>();list.forEach(tag->ids.add(tag.getAsString()));}

        @Override protected void init(){

            int x=width/2-150;

            for(int i=page*6;i<Math.min(ids.size(),page*6+6);i++){String id=ids.get(i);addRenderableWidget(Button.builder(Component.literal(id),b->{parent.draft.putString(key,id);onClose();}).bounds(x,64+(i%6)*23,300,20).build());}

            addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,38,24,20).build());

            addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(ids.size()-1)/6),page+1);rebuildWidgets();}).bounds(x+276,38,24,20).build());

            addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(x,height-26,300,20).build());

        }

        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}

        @Override public boolean isPauseScreen(){return false;}

        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,18,0xF4F1E9);if(ids.isEmpty())g.drawWordWrap(font,tr("pick.empty"),width/2-145,70,290,0xB8E8C9);super.render(g,mx,my,partial);}

    }

}

