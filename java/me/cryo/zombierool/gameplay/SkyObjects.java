package me.cryo.zombierool.gameplay;

import com.google.gson.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Portable sky scenery: definitions travel with presets, world saves and player overrides. */
public final class SkyObjects {
    public static final int MAX_OBJECTS=12, MAX_BOXES=64;
    public static final List<String> TYPES=List.of("sun","destroyed_earth","moon","nuclear_cloud","earth","dogfight","eclipse","floating_rocks","meteor","necropolis","custom");
    private SkyObjects(){}
    private static void value(CompoundTag out,CompoundTag in,String k,double fallback,double min,double max){double v=in.contains(k,99)?in.getDouble(k):fallback;out.putDouble(k,Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback);}
    public static ListTag sanitize(ListTag input){var result=new ListTag();var ids=new HashSet<String>();for(Tag tag:input){if(result.size()>=MAX_OBJECTS)break;if(!(tag instanceof CompoundTag n))continue;var row=sanitize(n);if(!row.getString("id").isBlank()&&ids.add(row.getString("id")))result.add(row);}return result;}
    public static CompoundTag sanitize(CompoundTag in){
        var n=new CompoundTag();String id=in.getString("id").trim();n.putString("id",id.substring(0,Math.min(64,id.length())));String type=in.getString("type");n.putString("type",TYPES.contains(type)?type:"custom");
        String name=in.getString("name").trim();n.putString("name",name.substring(0,Math.min(64,name.length())));n.putBoolean("enabled",!in.contains("enabled")||in.getBoolean("enabled"));
        value(n,in,"yaw",0,-180,180);value(n,in,"elevation",25,-85,85);value(n,in,"distance",100,10,1000);value(n,in,"size",16,.1,200);
        for(String k:List.of("rotation","tilt","roll"))value(n,in,k,0,-180,180);
        value(n,in,"brightness",1,0,5);value(n,in,"lunarPhase",0,0,7);for(String color:List.of("tintR","tintG","tintB"))value(n,in,color,1,0,1);
        value(n,in,"spin",0,-45,45);value(n,in,"orbit",0,-12,12);value(n,in,"bob",0,0,10);value(n,in,"speed",1,0,4);value(n,in,"phase",0,0,360);value(n,in,"opacity",1,0,1);
        var boxes=new ListTag();for(Tag t:in.getList("boxes",10)){if(boxes.size()>=MAX_BOXES)break;var b=(CompoundTag)t;var box=new CompoundTag();for(String k:List.of("x","y","z"))value(box,b,k,0,-4,4);for(String k:List.of("sx","sy","sz"))value(box,b,k,.2,.001,8);for(String k:List.of("r","g","b"))value(box,b,k,.7,0,1);value(box,b,"alpha",1,0,1);value(box,b,"emissive",0,0,1);boxes.add(box);}n.put("boxes",n.getString("type").equals("necropolis")?new ListTag():boxes);return n;
    }

    private static ListTag necropolisGeometry;
    /** Built from the locally installed Naxxramas WMO; loaded once, never sent in map packets. */
    public static ListTag necropolis(){return necropolisGeometry().copy();}
    public static ListTag necropolisGeometry(){
        if(necropolisGeometry==null){
            try(var stream=SkyObjects.class.getResourceAsStream("/assets/zombierool/scenery/necropolis.json")){
                if(stream==null)throw new IllegalStateException("Missing necropolis geometry");
                var json=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
                necropolisGeometry=net.minecraft.nbt.TagParser.parseTag(json.toString()).getList("boxes",10);
            }catch(Exception e){throw new IllegalStateException("Cannot load necropolis geometry",e);}
        }return necropolisGeometry;
    }
    private static void skyBox(ListTag list,double x,double y,double z,double sx,double sy,double sz,double r,double g,double b,double emissive){
        var n=new CompoundTag();String[] keys={"x","y","z","sx","sy","sz","r","g","b","emissive","alpha"};double[] v={x,y,z,sx,sy,sz,r,g,b,emissive,1};for(int i=0;i<keys.length;i++)n.putDouble(keys[i],v[i]);list.add(n);
    }
    public static CompoundTag object(String id,String type,double yaw,double elevation,double size){var n=new CompoundTag();n.putString("id",id);n.putString("type",type);n.putDouble("yaw",yaw);n.putDouble("elevation",elevation);n.putDouble("size",size);return sanitize(n);}
    public static ListTag preset(String id){var rows=new ListTag();switch(id){
        case "nuketown_zombies","ashfall"->{var n=object("cloud","nuclear_cloud",30,16,42);n.putDouble("distance",80);n.putDouble("speed",1);rows.add(n);}
        case "moon"->{var n=object("earth","earth",-35,38,19);n.putDouble("spin",.65);n.putDouble("tilt",18);rows.add(n);}
        case "origins"->{var n=object("air_battle","dogfight",0,85,100);n.putDouble("distance",100);rows.add(n);}
        case "shangri_la"->{var n=object("eclipse","eclipse",25,48,8);rows.add(n);}
        case "die_rise"->{for(int i=0;i<4;i++){var n=object("meteor_"+i,"meteor",-135+i*90,45,5);n.putDouble("phase",i*90);rows.add(n);}}
        default->{}
    }return rows;}
    /** Upgrade only supplied prototype scenes; custom preset geometry and placement stay intact. */
    public static ListTag migrate(String preset,int version,ListTag rows){
        if(version>=3||preset.startsWith("custom_")||rows.isEmpty())return sanitize(rows);
        var ids=new HashSet<String>();for(Tag tag:rows)if(tag instanceof CompoundTag n)ids.add(n.getString("id"));
        if(preset.equals("origins")&&ids.equals(Set.of("squadron_0","squadron_1"))||preset.equals("die_rise")&&ids.equals(Set.of("island_0","island_1","island_2","meteor")))return preset(preset);
        // Upgrade only unchanged supplied scenes. Keep any map-authored adjustments.
        if(version==2){
            if(preset.equals("origins")&&rows.size()==1&&matches(rows.getCompound(0),"air_battle",0,55,45,180,0))return preset(preset);
            if(preset.equals("die_rise")&&rows.size()==2&&matches(rows.getCompound(0),"meteor_0",-65,48,3,100,0)&&matches(rows.getCompound(1),"meteor_1",95,58,3,100,240))return preset(preset);
            if((preset.equals("nuketown_zombies")||preset.equals("ashfall"))&&rows.size()==1&&matches(rows.getCompound(0),"cloud",30,12,30,100,0))return preset(preset);
        }
        var result=sanitize(rows);if(preset.equals("nuketown_zombies"))for(Tag tag:result){var n=(CompoundTag)tag;if(n.getString("id").equals("cloud")&&Math.abs(n.getDouble("speed")-.65)<.001)n.putDouble("speed",1);}
        return result;
    }
    private static boolean matches(CompoundTag n,String id,double yaw,double elevation,double size,double distance,double phase){return n.getBoolean("enabled")&&n.getString("name").isBlank()&&n.getDouble("opacity")==1&&java.util.List.of("rotation","tilt","roll","spin","orbit","bob").stream().allMatch(k->n.getDouble(k)==0)&&n.getString("id").equals(id)&&Math.abs(n.getDouble("yaw")-yaw)<.001&&Math.abs(n.getDouble("elevation")-elevation)<.001&&Math.abs(n.getDouble("size")-size)<.001&&Math.abs(n.getDouble("distance")-distance)<.001&&Math.abs(n.getDouble("phase")-phase)<.001&&Math.abs(n.getDouble("speed")-1)<.001;}
    /** A sky-wide fall, with a fresh deterministic bearing each cycle and time for its trail to fade. */
    public record MeteorFlight(float age,double azimuthOffset,double elevationOffset){}
    public static MeteorFlight meteorFlight(float seconds,String id){
        int cycle=(int)Math.floor(seconds/18);float age=seconds-cycle*18;double jitter=new Random(id.hashCode()*31L+cycle*7919L).nextDouble()*50-25;
        return new MeteorFlight(age,cycle*137.507764+jitter+Math.min(age,12)*.8,35-Math.min(age,12)/12*160);
    }
    /** Compact JSON model, centered on its origin. No external file or executable code is retained. */
    public static CompoundTag importModel(String json,String id){
        if(json.length()>128*1024)throw new IllegalArgumentException("Model exceeds 128 KiB");var root=JsonParser.parseString(json).getAsJsonObject();var array=root.getAsJsonArray("boxes");if(array==null||array.isEmpty()||array.size()>MAX_BOXES)throw new IllegalArgumentException("Use 1Ã¢â‚¬â€œ64 colored boxes");
        var n=object(id,"custom",0,30,16);if(root.has("name"))n.putString("name",root.get("name").getAsString());var boxes=new ListTag();
        for(JsonElement entry:array){var box=entry.getAsJsonObject();var b=new CompoundTag();for(String key:List.of("x","y","z","sx","sy","sz","r","g","b","alpha","emissive"))if(box.has(key)){double v=box.get(key).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite model coordinate");b.putDouble(key,v);}boxes.add(b);}n.put("boxes",boxes);return sanitize(n);
    }
}
