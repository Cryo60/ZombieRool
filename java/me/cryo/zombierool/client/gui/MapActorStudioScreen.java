package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.scripting.LuaWorkbenchPackets;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Forms by default; code is an explicit advanced view. */
public final class MapActorStudioScreen extends Screen {
    private final Screen parent;private final BlockPos marker;
    private String name="my_enemy",document=MapActors.json(MapActorTemplates.profile(false)),saved=document,version="",status="";
    private List<String> files=List.of();private CompoundTag names=new CompoundTag(),defaults=new CompoundTag();
    private int page,formPage,markerTab;private boolean advanced,initialized;
    private CodeEditorBox editor;private EditBox identifier;
    private final Map<String,EditBox> fields=new LinkedHashMap<>();private final Map<String,String> initialFields=new HashMap<>();
    public MapActorStudioScreen(Screen parent,BlockPos marker){super(Component.translatable(marker==null?"gui.zombierool.actor_studio":"block.zombierool.map_actor_marker"));this.parent=parent;this.marker=marker;if(marker!=null){name=Long.toString(marker.asLong());document=saved=MapActors.json(MapActorTemplates.marker());}}
    private Component tr(String key){return Component.translatable("gui.zombierool.actors."+key);}
    private Button button(int x,int y,int w,Component label,Runnable action){return addRenderableWidget(Button.builder(label,b->{try{action.run();}catch(Exception e){status=e.getMessage();}}).bounds(x,y,w,20).build());}
    private Button button(int x,int y,int w,String key,Runnable action){return button(x,y,w,tr(key),action);}
    private void capture(){try{if(editor!=null)document=editor.getValue();if(identifier!=null&&marker==null)name=identifier.getValue();if(!fields.isEmpty()){var n=MapActors.parse(document);for(var entry:fields.entrySet()){String key=entry.getKey(),value=entry.getValue().getValue();if(value.equals(initialFields.get(key)))continue;if(key.equals("name"))n.putString(key,value);else{double number=Double.parseDouble(value);if(!Double.isFinite(number))throw new IllegalArgumentException();if(key.equals("intervalSeconds"))n.putInt("cooldownTicks",(int)Math.round(number*20));else n.putDouble(key,number);}}document=MapActors.json(n);}}catch(Exception e){throw new IllegalArgumentException(tr("invalid_number").getString());}}
    private void clearInputs(){editor=null;identifier=null;fields.clear();initialFields.clear();}
    private void request(String action){capture();NetworkHandler.INSTANCE.sendToServer(new LuaWorkbenchPackets.Request(action,name,document,version));}
    private void discard(Runnable action){capture();if(document.equals(saved))action.run();else minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)action.run();},tr("discard"),tr("discard_help")));}
    private void settings(){capture();try{minecraft.setScreen(new MapActorSettingsScreen(this,settingsDraft(),marker!=null,updated->{document=MapActors.json(updated);clearInputs();}));}catch(Exception e){status=e.getMessage();}}
    private CompoundTag settingsDraft()throws Exception{var n=MapActors.parse(document);if(marker!=null){var base=defaults.getCompound(n.getString("profile"));if(base.isEmpty())throw new IllegalArgumentException(tr("choose_enemy").getString());if(!n.contains("knifeMode"))n.putString("knifeMode","wave");if(!n.contains("knifeHits"))n.putInt("knifeHits",1);if(!n.contains("spawnRadius"))n.putDouble("spawnRadius",32);for(String key:List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","behavior","countsForWave","knifeMode","knifeHits","speedMode","stayNearSpawn","healthMode"))if(!n.contains(key)&&base.contains(key))n.put(key,base.get(key).copy());}return n;}
    private void fresh(boolean boss){discard(()->{name=boss?"my_boss":"my_enemy";document=MapActors.json(MapActorTemplates.profile(boss));saved=document;version="";advanced=false;formPage=0;clearInputs();rebuildWidgets();});}
    private void edit(String key,int x,int y,int w,CompoundTag n){var field=addRenderableWidget(new EditBox(font,x+w/2,y,w/2,20,tr(key)));field.setMaxLength(key.equals("name")?80:32);field.setValue(n.contains(key,99)?String.format(java.util.Locale.ROOT,"%.3f",n.getDouble(key)).replaceAll("0+$","").replaceAll("\\.$",""):n.getString(key));fields.put(key,field);initialFields.put(key,field.getValue());if(marker!=null&&key.equals("batch"))field.setResponder(this::syncBatchLimit);}
    private void syncBatchLimit(String value){
        try{
            double batch=Double.parseDouble(value);if(!Double.isFinite(batch)||batch<1)return;
            var n=MapActors.parse(document);var limit=fields.get("maxAlive");
            if(limit!=null)n.putDouble("maxAlive",Double.parseDouble(limit.getValue()));
            double previous=n.getDouble("maxAlive");n.putDouble("batch",batch);MapActors.normalizeSpawnBatch(n);
            if(n.getDouble("maxAlive")>previous){
                if(limit!=null)limit.setValue(Integer.toString(n.getInt("maxAlive")));
                else{var draft=MapActors.parse(document);draft.putInt("maxAlive",n.getInt("maxAlive"));document=MapActors.json(draft);}
            }
        }catch(Exception ignored){/* Incomplete number while typing; validation runs when saving. */}
    }
    private void set(String key,String value){capture();try{var n=MapActors.parse(document);n.putString(key,value);document=MapActors.json(n);clearInputs();rebuildWidgets();}catch(Exception e){status=e.getMessage();}}
    private void toggle(String key){capture();try{var n=MapActors.parse(document);n.putBoolean(key,!n.getBoolean(key));document=MapActors.json(n);clearInputs();rebuildWidgets();}catch(Exception e){status=e.getMessage();}}
    private void picker(){capture();var owner=this;minecraft.setScreen(new Screen(tr("choose_enemy")){String query="";int p;
        @Override protected void init(){var search=addRenderableWidget(new EditBox(font,20,34,width-40,20,Component.translatable("gui.recipebook.search_hint")));search.setValue(query);search.setResponder(value->{query=value;p=0;rebuildWidgets();});int count=Math.max(1,(height-100)/24),y=62;var filtered=files.stream().filter(id->(id+names.getString(id)).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).toList();for(String id:filtered.stream().skip((long)p*count).limit(count).toList()){addRenderableWidget(Button.builder(Component.literal(names.getString(id)),b->{owner.set("profile",id);minecraft.setScreen(owner);}).bounds(20,y,width-40,20).build());y+=24;}addRenderableWidget(Button.builder(Component.literal("<"),b->{p=Math.max(0,p-1);rebuildWidgets();}).bounds(20,height-28,40,20).build());addRenderableWidget(Button.builder(Component.literal(">"),b->{if((p+1)*count<filtered.size())p++;rebuildWidgets();}).bounds(64,height-28,40,20).build());addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-100,height-28,80,20).build());setFocused(search);}
        @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF10151D);g.drawCenteredString(font,title,width/2,14,0xFFFFFF);super.render(g,x,y,partial);}
        @Override public void onClose(){minecraft.setScreen(owner);}@Override public boolean isPauseScreen(){return false;}
    });}
    @Override protected void init(){capture();clearInputs();if(marker!=null){initMarker();return;}int side=124,left=side+8,w=width-left-8;
        if(marker==null){button(8,35,112,"new_enemy",()->fresh(false));button(8,58,112,"new_boss",()->fresh(true));int count=Math.max(1,(height-164)/22),y=84;for(String id:files.stream().skip((long)page*count).limit(count).toList()){button(8,y,112,Component.literal(font.plainSubstrByWidth(names.getString(id),104)),()->discard(()->{name=id;identifier.setValue(id);request("actor_read");}));y+=22;}button(8,height-68,53,Component.literal("<"),()->{page=Math.max(0,page-1);rebuildWidgets();});button(65,height-68,55,Component.literal(">"),()->{if((page+1)*count<files.size())page++;rebuildWidgets();});button(8,height-43,112,"import",()->request("actor_import"));}
        try{var n=MapActors.parse(document);
            if(marker==null){identifier=addRenderableWidget(new EditBox(font,left,35,w,20,Component.literal("ID")));identifier.setMaxLength(48);identifier.setValue(name);}
            if(advanced){editor=addRenderableWidget(new CodeEditorBox(font,left+26,62,w-26,Math.max(30,height-160),Component.empty(),tr("definition")));editor.setValue(document);}
            else if(marker!=null){
                button(left,35,w,tr("choose_enemy").copy().append(": ").append(Component.literal(names.contains(n.getString("profile"))?names.getString(n.getString("profile")):tr("choose_enemy").getString())),this::picker);
                var choices=List.of("game_start","wave_start","interval");button(left,59,w,tr(n.getString("trigger")),()->{formPage=0;set("trigger",choices.get((choices.indexOf(n.getString("trigger"))+1)%choices.size()));});
                var keys=new ArrayList<>(List.of("batch","maxAlive"));if(!n.getString("trigger").equals("game_start")){keys.add("firstWave");keys.add("waveInterval");}if(n.getString("trigger").equals("interval"))keys.add("intervalSeconds");
                int count=Math.max(1,(height-210)/24),y=85;for(String key:keys.stream().skip((long)formPage*count).limit(count).toList()){if(key.equals("intervalSeconds"))n.putDouble(key,n.getInt("cooldownTicks")/20.0);edit(key,left,y,w,n);y+=24;}
                button(left,height-94,w/2-2,tr("enabled").copy().append(": ").append(Component.literal(n.getBoolean("enabled")?"On":"Off")),()->toggle("enabled"));button(left+w/2+2,height-94,w/2-2,tr("requireRedstone").copy().append(": ").append(Component.literal(n.getBoolean("requireRedstone")?"On":"Off")),()->toggle("requireRedstone"));
            }else{var keys=List.of("name","health","damage","speed","scale","aggroRadius","wanderRadius","leashRadius","meleeReach");int count=Math.max(1,(height-188)/24),y=64;for(String key:keys.stream().skip((long)formPage*count).limit(count).toList()){edit(key,left,y,w,n);y+=24;}}
            if(!advanced){button(left,height-119,38,Component.literal("<"),()->{capture();formPage=Math.max(0,formPage-1);clearInputs();rebuildWidgets();});button(left+42,height-119,38,Component.literal(">"),()->{capture();int count=Math.max(1,(height-(marker==null?188:210))/24);int total=marker==null?10:n.getString("trigger").equals("game_start")?2:n.getString("trigger").equals("interval")?5:4;if((formPage+1)*count<total)formPage++;clearInputs();rebuildWidgets();});button(left+84,height-119,w-84,marker==null?"settings":"advanced_settings",this::settings);}
        }catch(Exception e){status=e.getMessage();}
        int bw=(w-8)/3;button(left,height-68,bw,"save",()->request(marker==null?"actor_save":"actor_marker_save"));
        if(marker==null){button(left+bw+4,height-68,bw,"egg",()->request("actor_egg"));button(left+2*bw+8,height-68,w-2*bw-8,"preview",()->request("actor_spawn"));button(left,height-94,w,"create_script",()->request("actor_script"));}
        button(left,height-43,bw,"lua",()->minecraft.setScreen(new LuaWorkbenchScreen(this,1)));button(left+bw+4,height-43,bw,advanced?"simple":"advanced",()->{capture();advanced=!advanced;clearInputs();rebuildWidgets();});
        button(width-88,height-23,80,Component.literal("Back"),this::onClose);
        if(!initialized){initialized=true;request("actor_list");if(marker!=null)request("actor_marker_read");}
    }
    private void initMarker(){
        int left=Math.max(12,width/2-240),w=Math.min(480,width-24);
        try{boolean clean=document.equals(saved);var n=MapActors.parse(document);var base=defaults.getCompound(n.getString("profile"));
            for(String key:List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","behavior","countsForWave","knifeMode","knifeHits","speedMode","stayNearSpawn","healthMode"))if(!n.contains(key)&&base.contains(key))n.put(key,base.get(key).copy());
            if(!n.contains("knifeMode"))n.putString("knifeMode","wave");if(!n.contains("knifeHits"))n.putInt("knifeHits",1);if(!n.contains("spawnRadius"))n.putDouble("spawnRadius",32);if(!n.contains("knockbackResistance"))n.putDouble("knockbackResistance",1);document=MapActors.json(n);if(clean)saved=document;
            button(left,35,w,tr("choose_enemy").copy().append(": ").append(Component.literal(names.contains(n.getString("profile"))?names.getString(n.getString("profile")):"—")),this::picker);
            var triggers=List.of("game_start","wave_start","interval");button(left,59,w,tr(n.getString("trigger")),()->set("trigger",triggers.get((triggers.indexOf(n.getString("trigger"))+1)%triggers.size()))).active=!base.isEmpty();
            String[] tabs={"spawn_settings","behavior_settings","stats_settings"};for(int i=0;i<3;i++){final int tab=i;var b=Button.builder(tr(tabs[i]),v->{capture();markerTab=tab;clearInputs();rebuildWidgets();}).bounds(left+i*(w/3),85,w/3-3,20).build();b.active=!base.isEmpty()&&i!=markerTab;addRenderableWidget(b);}
            if(base.isEmpty()){markerTab=0;status=tr("choose_enemy").getString();}else{
            var keys=new ArrayList<String>();if(markerTab==0){keys.addAll(List.of("batch","maxAlive","spawnRadius"));if(!n.getString("trigger").equals("game_start"))keys.addAll(List.of("firstWave","waveInterval"));if(n.getString("trigger").equals("interval")){keys.add("intervalSeconds");n.putDouble("intervalSeconds",n.getInt("cooldownTicks")/20.0);}}
            else if(markerTab==1)keys.addAll(List.of("aggroRadius","wanderRadius","leashRadius"));else keys.addAll(List.of("health","damage","speed","knifeHits"));
            int y=115;for(String key:keys){edit(key,left,y,w,n);y+=26;}
            if(markerTab==2){button(left,y+56,w,tr("healthMode").copy().append(": ").append(tr("health_"+n.getString("healthMode"))),()->set("healthMode",n.getString("healthMode").equals("wave")?"fixed":"wave"));var speeds=List.of("custom","walk","trot","run");button(left,y+30,w,tr("speedMode").copy().append(": ").append(tr("speed_"+n.getString("speedMode"))),()->set("speedMode",speeds.get((speeds.indexOf(n.getString("speedMode"))+1)%speeds.size())));var modes=List.of("wave","hits","fixed");button(left,y+4,w,tr("knifeMode").copy().append(": ").append(tr("knife_"+n.getString("knifeMode"))),()->set("knifeMode",modes.get((modes.indexOf(n.getString("knifeMode"))+1)%modes.size())));}
            if(markerTab==0){button(left,height-88,w/2-3,tr("enabled").copy().append(": ").append(Component.literal(n.getBoolean("enabled")?"On":"Off")),()->toggle("enabled"));button(left+w/2,height-88,w/2,tr("requireRedstone").copy().append(": ").append(Component.literal(n.getBoolean("requireRedstone")?"On":"Off")),()->toggle("requireRedstone"));}
            if(markerTab==1){var behaviors=List.of("melee","guard","wander","script");button(left,y+4,w,tr("behavior").copy().append(": ").append(tr(n.getString("behavior"))),()->set("behavior",behaviors.get((behaviors.indexOf(n.getString("behavior"))+1)%behaviors.size())));button(left,y+30,w,tr("countsForWave").copy().append(": ").append(Component.literal(n.getBoolean("countsForWave")?"On":"Off")),()->toggle("countsForWave"));button(left,y+56,w,tr("stayNearSpawn").copy().append(": ").append(Component.literal(n.getBoolean("stayNearSpawn")?"On":"Off")),()->toggle("stayNearSpawn"));}
            }
        }catch(Exception e){status=e.getMessage();}
        var save=Button.builder(tr("save"),b->{try{request("actor_marker_save");}catch(Exception e){status=e.getMessage();}}).bounds(left,height-48,w/2-3,20).build();try{save.active=defaults.contains(MapActors.parse(document).getString("profile"));}catch(Exception e){save.active=false;}addRenderableWidget(save);button(left+w/2,height-48,w/2,Component.literal("Done"),this::onClose);
        if(!initialized){initialized=true;request("actor_list");request("actor_marker_read");}
    }
    public void response(CompoundTag n){if(!n.getBoolean("success")){status=n.getString("message");return;}String action=n.getString("action");status=n.getString("message");if(action.equals("actor_list")){files=n.getList("files",8).stream().map(Tag::getAsString).toList();names=n.getCompound("names").copy();for(String id:n.getCompound("nameKeys").getAllKeys()){String key=n.getCompound("nameKeys").getString(id);if(!key.isEmpty())names.putString(id,Component.translatable(key).getString());}defaults=n.getCompound("defaults");capture();rebuildWidgets();return;}if(n.contains("code")){name=n.getString("name");document=saved=n.getString("code");version=n.getString("version");formPage=0;clearInputs();rebuildWidgets();}if(action.equals("actor_save")||action.equals("actor_import"))request("actor_list");}
    @Override public void tick(){if(editor!=null)editor.tick();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF10151D);g.drawCenteredString(font,title,width/2,12,0xE0EAF2);super.render(g,x,y,partial);if(editor!=null)editor.renderLineNumbers(g);int left=marker==null?132:Math.max(12,width/2-240),w=marker==null?width-left-8:Math.min(480,width-24),fy=marker==null?70:121;for(String key:fields.keySet()){g.drawString(font,font.plainSubstrByWidth(tr(key).getString(),w/2-6),left,fy,0xD8E3ED);fy+=marker==null?24:26;}g.drawString(font,font.plainSubstrByWidth(status,width-100),8,height-14,0xE0D59A);}
    @Override public boolean isPauseScreen(){return false;}@Override public void onClose(){discard(()->minecraft.setScreen(parent));}
}
