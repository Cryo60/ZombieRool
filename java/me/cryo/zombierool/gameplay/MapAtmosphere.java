package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Map-authored presentation. Persistent definitions; playback clocks are session-local. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MapAtmosphere extends SavedData {
    public static final int MAX_SOURCES=64;
    private CompoundTag settings=defaults();
    private CompoundTag matchBaseline;
    private final Map<String,CompoundTag> customPresets=new LinkedHashMap<>();
    private final Map<String,Long> next=new HashMap<>();
    private final Map<UUID,Map<String,Long>> playerNext=new HashMap<>();
    private static final String OVERRIDE="zr_atmosphere_preset";
    public static MapAtmosphere get(ServerLevel level){return level.getDataStorage().computeIfAbsent(MapAtmosphere::load,MapAtmosphere::new,"zombierool_map_atmosphere");}
    public static MapAtmosphere load(CompoundTag n){var d=new MapAtmosphere();d.settings=sanitize(n.getCompound("settings"));if(n.contains("matchBaseline",10))d.matchBaseline=sanitize(n.getCompound("matchBaseline"));d.readPresets(n.getList("presets",10));return d;}
    @Override public CompoundTag save(CompoundTag tag){tag.put("settings",settings.copy());if(matchBaseline!=null)tag.put("matchBaseline",matchBaseline.copy());tag.put("presets",presetLibrary());return tag;}
    private void readPresets(ListTag rows){for(Tag tag:rows){if(customPresets.size()>=32)break;var row=(CompoundTag)tag;String id=row.getString("id"),name=row.getString("name").trim();if(!id.matches("custom_[a-f0-9]{32}")||name.isEmpty())continue;var record=new CompoundTag();record.putString("id",id);record.putString("name",name.substring(0,Math.min(64,name.length())));var definition=sanitize(row.getCompound("settings"));definition.putString("presetId",id);record.put("settings",definition);customPresets.putIfAbsent(id,record);}}
    public ListTag presetLibrary(){var rows=new ListTag();customPresets.values().forEach(row->rows.add(row.copy()));return rows;}
    public CompoundTag editor(){var n=settings();n.put("_presets",presetLibrary());return n;}
    public List<String> availablePresets(){var ids=new ArrayList<>(presetIds());ids.addAll(customPresets.keySet());return ids;}
    public String displayPresetName(String id){return customPresets.containsKey(id)?customPresets.get(id).getString("name"):presetName(id);}
    public CompoundTag resolvePreset(String id){return customPresets.containsKey(id)?customPresets.get(id).getCompound("settings").copy():preset(id);}
    public String createPreset(String name,CompoundTag definition){
        name=name.trim();if(name.isEmpty()||name.length()>64||customPresets.size()>=32)throw new IllegalArgumentException("Invalid preset name or preset library full");String id="custom_"+UUID.randomUUID().toString().replace("-","");var row=new CompoundTag();row.putString("id",id);row.putString("name",name);var n=sanitize(definition);n.putString("presetId",id);row.put("settings",n);customPresets.put(id,row);setDirty();return id;
    }
    /** Map-authored records can be updated; supplied presets never enter this editable library. */
    public boolean saveEditor(ServerLevel level,CompoundTag input){
        var deleted=new HashSet<String>();for(Tag tag:input.getList("_deletedPresets",8)){String id=tag.getAsString();if(presetIds().contains(id)||!id.matches("custom_[a-f0-9]{32}")||!customPresets.containsKey(id))return false;deleted.add(id);}
        var additions=input.getList("_presets",10);var staged=new LinkedHashMap<String,CompoundTag>();
        for(Tag tag:additions){var row=(CompoundTag)tag;String id=row.getString("id"),name=row.getString("name").trim();if(!id.matches("custom_[a-f0-9]{32}")||presetIds().contains(id)||name.isEmpty()||name.length()>64||staged.containsKey(id)||deleted.contains(id))return false;var n=new CompoundTag();n.putString("id",id);n.putString("name",name);var definition=sanitize(row.getCompound("settings"));definition.putString("presetId",id);n.put("settings",definition);staged.put(id,n);}
        long newRecords=staged.keySet().stream().filter(id->!customPresets.containsKey(id)).count();if(customPresets.size()-deleted.size()+newRecords>32)return false;String selected=input.getString("presetId");if(deleted.contains(selected)||selected.startsWith("custom_")&&!customPresets.containsKey(selected)&&!staged.containsKey(selected))return false;
        deleted.forEach(customPresets::remove);customPresets.putAll(staged);configure(level,input);return true;
    }
    public static CompoundTag playerOverride(ServerPlayer p){return p.getPersistentData().getCompound(OVERRIDE).copy();}
    public static boolean hasOverride(ServerPlayer p){var n=p.getPersistentData().getCompound(OVERRIDE);return n.getString("dimension").equals(p.serverLevel().dimension().location().toString())&&n.contains("settings",10);}
    public static CompoundTag effective(ServerPlayer p){return hasOverride(p)?sanitize(playerOverride(p).getCompound("settings")):get(p.serverLevel()).settings();}
    public static void restorePlayer(ServerPlayer p,CompoundTag previous){if(previous.isEmpty())p.getPersistentData().remove(OVERRIDE);else p.getPersistentData().put(OVERRIDE,previous.copy());get(p.serverLevel()).playerNext.remove(p.getUUID());MapAtmospherePackets.sync(p,effective(p));}
    public static void configurePlayer(ServerPlayer p,CompoundTag settings){var n=new CompoundTag();n.putString("dimension",p.serverLevel().dimension().location().toString());n.put("settings",sanitize(settings));restorePlayer(p,n);}
    public static boolean applyPlayerPreset(ServerPlayer p,String id){
        if(id.isBlank())return true;if(id.equals("@map")){restorePlayer(p,new CompoundTag());return true;}var d=get(p.serverLevel());if(!d.availablePresets().contains(id)&&!id.equals("ashfall"))return false;var n=d.resolvePreset(id);
        if(!id.startsWith("custom_"))for(Tag tag:n.getList("sources",10)){var row=(CompoundTag)tag;row.putDouble("x",row.getDouble("x")+p.getX());row.putDouble("y",row.getDouble("y")+p.getY()-80);row.putDouble("z",row.getDouble("z")+p.getZ());}
        var override=new CompoundTag();override.putString("dimension",p.serverLevel().dimension().location().toString());override.put("settings",n);restorePlayer(p,override);return true;
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event){if(event.getEntity() instanceof ServerPlayer p)p.getPersistentData().remove(OVERRIDE);}
    public static void beginMatch(ServerLevel level){var d=get(level);restoreMatch(level);d.matchBaseline=d.settings();d.setDirty();}
    public static void clearPlayerOverrides(ServerLevel level){var d=get(level);d.playerNext.clear();d.next.clear();for(ServerPlayer p:level.players()){p.getPersistentData().remove(OVERRIDE);MapAtmospherePackets.sync(p,d.settings);}}
    public static void restoreMatch(ServerLevel level){var d=get(level);if(d.matchBaseline!=null){d.settings=d.matchBaseline.copy();d.matchBaseline=null;d.setDirty();}clearPlayerOverrides(level);}
    public CompoundTag settings(){return settings.copy();}
    public void configure(ServerLevel level,CompoundTag input){settings=sanitize(input);next.clear();setDirty();for(ServerPlayer p:level.players())MapAtmospherePackets.sync(p,effective(p));me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnMapAtmosphereChanged");}
    public static CompoundTag defaults(){
        var n=new CompoundTag();n.putBoolean("enabled",false);n.putBoolean("skyEnabled",true);n.putString("presetId","none");n.putString("skyTexture","zombierool:textures/environment/ashfall_sky.png");n.putString("skyLayout","cube");n.putString("skyCoordinates","z_up");
        for(String k:List.of("skyBrightness","saturation","contrast","gamma","red","green","blue","ambientVolume"))n.putDouble(k,1);
        for(String k:List.of("skyYaw","skyPitch","exposure","vignette","dirt","grain"))n.putDouble(k,0);
        n.putDouble("vignetteSoftness",.5);n.putInt("skySceneVersion",3);n.put("skyObjects",new ListTag());n.put("sources",new ListTag());return n;
    }
    private static void number(CompoundTag out,CompoundTag in,String key,double fallback,double min,double max){double v=in.contains(key,99)?in.getDouble(key):fallback;out.putDouble(key,Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback);}
    private static String resource(String value,String fallback){var id=ResourceLocation.tryParse(value);return id==null?fallback:id.toString();}
    public static CompoundTag sanitize(CompoundTag input){
        var n=defaults();n.putString("presetId",input.getString("presetId"));n.putBoolean("enabled",input.getBoolean("enabled"));if(input.contains("skyEnabled"))n.putBoolean("skyEnabled",input.getBoolean("skyEnabled"));
        n.putString("skyTexture",resource(input.getString("skyTexture"),n.getString("skyTexture")));n.putString("skyLayout","panorama".equals(input.getString("skyLayout"))?"panorama":"cube");n.putString("skyCoordinates","y_up".equals(input.getString("skyCoordinates"))?"y_up":"z_up");
        number(n,input,"skyYaw",0,-3600,3600);number(n,input,"skyPitch",0,-180,180);number(n,input,"skyBrightness",1,0,8);
        for(String key:List.of("red","green","blue","contrast","gamma","saturation"))number(n,input,key,1,key.equals("gamma")?.1:0,4);
        number(n,input,"exposure",0,-5,5);for(String key:List.of("vignette","dirt","grain"))number(n,input,key,0,0,1);number(n,input,"vignetteSoftness",.5,.05,1);number(n,input,"ambientVolume",1,0,2);
        var rows=new ListTag();var ids=new HashSet<String>();for(Tag entry:input.getList("sources",Tag.TAG_COMPOUND)){if(rows.size()>=MAX_SOURCES)break;var s=sanitizeSource((CompoundTag)entry);if(!s.getString("id").isBlank()&&ids.add(s.getString("id")))rows.add(s);}n.put("sources",rows);n.put("skyObjects",!input.contains("skyObjects",9)&&n.getBoolean("enabled")&&!n.getString("presetId").startsWith("custom_")?SkyObjects.preset(n.getString("presetId")):SkyObjects.migrate(n.getString("presetId"),input.getInt("skySceneVersion"),input.getList("skyObjects",10)));return n;
    }
    public static CompoundTag sanitizeSource(CompoundTag input){
        var n=new CompoundTag();String id=input.getString("id").trim();n.putString("id",id.length()>64?id.substring(0,64):id);n.putString("sound",resource(input.getString("sound"),"zombierool:ambient_ash_wind"));
        for(String k:List.of("enabled","global","loop"))n.putBoolean(k,!input.contains(k)&&k.equals("enabled")||input.getBoolean(k));
        for(String k:List.of("x","y","z"))number(n,input,k,0,-30_000_000,30_000_000);
        number(n,input,"volume",1,0,4);number(n,input,"pitch",1,.25,4);number(n,input,"pitchVariation",.05,0,.5);
        number(n,input,"near",8,0,4096);number(n,input,"radius",128,1,16384);number(n,input,"occlusion",.35,0,1);
        number(n,input,"fadeIn",20,0,1200);number(n,input,"fadeOut",20,0,1200);number(n,input,"intervalMin",400,1,72000);number(n,input,"intervalMax",1200,n.getDouble("intervalMin"),72000);
        number(n,input,"flash",0,0,1);number(n,input,"soundSpeed",0,0,2000);return n;
    }
    public static CompoundTag ashfall(){
        var n=defaults();n.putString("presetId","nuketown_zombies");n.putBoolean("enabled",true);n.putDouble("saturation",.72);n.putDouble("red",1.08);n.putDouble("green",.94);n.putDouble("blue",.85);n.putDouble("vignette",.25);n.putDouble("dirt",.12);n.putDouble("grain",.035);
        n.put("skyObjects",SkyObjects.preset("nuketown_zombies"));n.put("sources",OriginalMapAmbience.sources("nuketown_zombies"));return sanitize(n);
    }
    public static java.util.List<String> soundIds(){return java.util.stream.Stream.concat(OriginalMapAmbience.soundIds().stream(),List.of("ambient_ash_wind","ambient_cloud_rumble","ambient_distant_thunder","ambient_distant_alarm","ambient_wind_gust","ambient_lunar_base","ambient_lunar_signal","ambient_night_wind","ambient_night_whispers","ambient_tower_wind","ambient_tower_creak").stream().map(id->"zombierool:"+id)).distinct().toList();}
    public static java.util.List<String> presetIds(){return List.of("nuketown_zombies","moon","nacht_der_untoten","die_rise","origins","revelations","the_giant","shangri_la","halo_blizzard","halo_zenith","none");}
    public static String presetName(String id){return switch(id){case "nuketown_zombies","ashfall"->"Nuketown Zombies";case "moon"->"Moon";case "nacht_der_untoten"->"Nacht der Untoten";case "die_rise"->"Die Rise";case "origins"->"Origins";case "revelations"->"Revelations";case "the_giant"->"The Giant";case "shangri_la"->"Shangri-La";case "halo_blizzard"->"Blizzard";case "halo_zenith"->"Zenith";default->"None";};}
    public static CompoundTag preset(String id){
        if(id.equals("ashfall")||id.equals("nuketown_zombies"))return ashfall();if(id.equals("none"))return defaults();
        if(!presetIds().contains(id))throw new IllegalArgumentException("Unknown atmosphere preset: "+id);
        var n=defaults();n.putBoolean("enabled",true);n.putString("presetId",id);var rows=new ListTag();String sky;
        switch(id){
            case "halo_blizzard"->{sky="halo_blizzard";n.putString("skyLayout","panorama");n.putString("skyCoordinates","y_up");n.putDouble("blue",1.03);n.putDouble("vignette",.1);rows.add(source("wind","zombierool:ambient_wind_gust",true,true,0,80,0,.1,512,400,1200));rows.add(source("gusts","zombierool:ambient_night_wind",false,true,0,80,0,.15,512,600,1600));}
            case "halo_zenith"->{sky="halo_zenith";n.putString("skyLayout","panorama");n.putString("skyCoordinates","y_up");n.putDouble("vignette",.08);rows.add(source("wind","zombierool:ambient_ash_wind",true,true,0,80,0,.1,512,400,1200));rows.add(source("gusts","zombierool:ambient_wind_gust",false,true,0,80,0,.1,512,700,1800));}
            case "origins"->{sky="trenches";n.putDouble("saturation",.85);n.putDouble("blue",1.04);n.putDouble("vignette",.16);}
            case "moon"->{sky="lunar";n.putDouble("saturation",.6);n.putDouble("blue",1.08);n.putDouble("vignette",.15);rows.add(source("base","zombierool:ambient_lunar_base",true,true,0,80,0,.35,512,400,1200));rows.add(source("signals","zombierool:ambient_lunar_signal",false,true,0,80,0,.65,512,500,1600));}
            case "nacht_der_untoten"->{sky="nightfall";n.putDouble("saturation",.45);n.putDouble("blue",1.08);n.putDouble("vignette",.35);n.putDouble("grain",.025);rows.add(source("wind","zombierool:ambient_night_wind",true,true,0,80,0,.4,512,400,1200));rows.add(source("whispers","zombierool:ambient_night_whispers",false,true,0,80,0,.6,512,300,1000));}
            case "revelations"->{sky="fractured";n.putString("skyLayout","panorama");n.putString("skyCoordinates","y_up");n.putDouble("saturation",1.05);n.putDouble("vignette",.16);rows.add(source("rift","zombierool:ambient_lunar_base",true,true,0,80,0,.15,512,400,1200));rows.add(source("wind","zombierool:ambient_wind_gust",false,true,0,80,0,.2,512,500,1500));}
            case "the_giant"->{sky="winter";n.putString("skyLayout","panorama");n.putString("skyCoordinates","y_up");n.putDouble("saturation",.8);n.putDouble("blue",1.04);n.putDouble("vignette",.2);rows.add(source("wind","zombierool:ambient_night_wind",true,true,0,80,0,.25,512,400,1200));rows.add(source("metal","zombierool:ambient_tower_creak",false,false,0,90,-30,.35,160,600,1800));}
            case "shangri_la"->{sky="jungle";n.putString("skyLayout","panorama");n.putString("skyCoordinates","y_up");n.putDouble("saturation",1.05);n.putDouble("vignette",.12);rows.add(source("wind","zombierool:ambient_ash_wind",true,true,0,80,0,.2,512,400,1200));rows.add(source("gusts","zombierool:ambient_wind_gust",false,true,0,80,0,.3,512,500,1400));}
            default->{sky="highrise";n.putDouble("saturation",.85);n.putDouble("red",1.05);n.putDouble("vignette",.18);rows.add(source("wind","zombierool:ambient_tower_wind",true,true,0,80,0,.45,512,400,1200));rows.add(source("metal","zombierool:ambient_tower_creak",false,false,0,90,-30,.7,160,240,800));}
        }n.put("skyObjects",SkyObjects.preset(id));n.putString("skyTexture","zombierool:textures/environment/"+sky+"_sky.png");n.put("sources",id.equals("die_rise")||id.equals("origins")?OriginalMapAmbience.sources(id):rows);return sanitize(n);
    }
    public static CompoundTag source(String id,String sound,boolean loop,boolean global,double x,double y,double z,double volume,double radius,int min,int max){var n=new CompoundTag();n.putString("id",id);n.putString("sound",sound);n.putBoolean("loop",loop);n.putBoolean("global",global);n.putBoolean("enabled",true);n.putDouble("x",x);n.putDouble("y",y);n.putDouble("z",z);n.putDouble("volume",volume);n.putDouble("radius",radius);n.putInt("intervalMin",min);n.putInt("intervalMax",max);return sanitizeSource(n);}
    public boolean putSource(ServerLevel l,CompoundTag source){var n=settings();var rows=n.getList("sources",10);String id=source.getString("id");rows.removeIf(t->((CompoundTag)t).getString("id").equals(id));if(rows.size()>=MAX_SOURCES||id.isBlank())return false;rows.add(sanitizeSource(source));n.put("sources",rows);configure(l,n);return true;}
    public boolean removeSource(ServerLevel l,String id){var n=settings();var rows=n.getList("sources",10);boolean removed=rows.removeIf(t->((CompoundTag)t).getString("id").equals(id));if(removed){n.put("sources",rows);configure(l,n);}return removed;}
    public boolean trigger(ServerLevel l,String id){if(!settings.getBoolean("enabled"))return false;for(Tag t:settings.getList("sources",10)){var source=(CompoundTag)t;if(source.getString("id").equals(id)&&source.getBoolean("enabled")){var cue=source.copy();cue.putLong("_seed",l.random.nextLong());for(ServerPlayer p:l.players())if(!hasOverride(p))MapAtmospherePackets.cue(p,cue);me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnAmbientSound",id,source.getString("sound"));return true;}}return false;}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel l)||l.players().isEmpty()||WaveManager.isPausedByPlayer())return;var d=get(l);
        if(d.settings.getBoolean("enabled"))for(Tag t:d.settings.getList("sources",10)){var s=(CompoundTag)t;if(!s.getBoolean("enabled")||s.getBoolean("loop"))continue;String id=s.getString("id");long now=l.getGameTime();if(now>=d.next.getOrDefault(id,Long.MAX_VALUE))d.trigger(l,id);
            if(!d.next.containsKey(id)||now>=d.next.get(id)){int min=(int)s.getDouble("intervalMin"),max=(int)s.getDouble("intervalMax");d.next.put(id,now+min+l.random.nextInt(Math.max(1,max-min+1)));}}
        d.tickPlayers(l);
    }
    private void tickPlayers(ServerLevel l){
        playerNext.keySet().removeIf(id->l.getPlayerByUUID(id)==null);for(ServerPlayer p:l.players()){
            if(!hasOverride(p)){playerNext.remove(p.getUUID());continue;}var n=effective(p);if(!n.getBoolean("enabled"))continue;var clocks=playerNext.computeIfAbsent(p.getUUID(),id->new HashMap<>());long now=l.getGameTime();
            for(Tag tag:n.getList("sources",10)){var source=(CompoundTag)tag;if(!source.getBoolean("enabled")||source.getBoolean("loop"))continue;String id=source.getString("id");long due=clocks.getOrDefault(id,Long.MAX_VALUE);if(now>=due){var cue=source.copy();cue.putLong("_seed",l.random.nextLong());MapAtmospherePackets.cue(p,cue);}if(!clocks.containsKey(id)||now>=due){int min=(int)source.getDouble("intervalMin"),max=(int)source.getDouble("intervalMax");clocks.put(id,now+min+l.random.nextInt(Math.max(1,max-min+1)));}}
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)MapAtmospherePackets.sync(p,effective(p));}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)MapAtmospherePackets.sync(p,effective(p));}
}
