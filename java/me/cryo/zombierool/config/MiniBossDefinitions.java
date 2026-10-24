package me.cryo.zombierool.config;

import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

/** Data-pack boss templates: no new Java entity or renderer is needed. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MiniBossDefinitions {
    private static final Map<String,CompoundTag> DEFINITIONS=new java.util.concurrent.ConcurrentSkipListMap<>();
    public static boolean contains(String id){return id.equals("panzer")||id.equals("brutus")||DEFINITIONS.containsKey(id);}
    public static List<String> ids(){var out=new ArrayList<String>(List.of("panzer","brutus"));out.addAll(DEFINITIONS.keySet());return out;}
    public static CompoundTag get(String id){var d=DEFINITIONS.get(id);return d==null?new CompoundTag():d.copy();}
    public static CompoundTag snapshot(){var out=new CompoundTag();DEFINITIONS.forEach((id,p)->out.put(id,p.copy()));return out;}
    public static void receive(CompoundTag profiles){DEFINITIONS.clear();for(String id:profiles.getAllKeys())DEFINITIONS.put(id,profiles.getCompound(id).copy());}
    public static synchronized void register(ResourceLocation id,CompoundTag profile){
        var safe=new CompoundTag();safe.putString("id",id.toString());safe.putString("template",List.of("panzer","brutus","george_romero","astronaut","abomination").contains(profile.getString("template"))?profile.getString("template"):"brutus");safe.putString("name",profile.getString("name").isBlank()?id.toString():profile.getString("name"));
        String texture=profile.getString("texture");if(!texture.isBlank()&&ResourceLocation.tryParse(texture)!=null)safe.putString("texture",texture);
        for(var key:List.of("health","speed","damage")){double value=profile.contains(key,Tag.TAG_ANY_NUMERIC)?profile.getDouble(key):key.equals("health")?360:key.equals("speed")?.36:4;double min=key.equals("speed")?.05:1,max=key.equals("speed")?1:1000000;safe.putDouble(key,Double.isFinite(value)?Math.max(min,Math.min(max,value)):min);}
        DEFINITIONS.put(id.toString(),safe);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void reload(net.minecraftforge.event.AddReloadListenerEvent event){event.addListener(new SimpleJsonResourceReloadListener(new Gson(),"mini_bosses"){
        @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){DEFINITIONS.clear();files.forEach((id,json)->{try{var p=new CompoundTag();for(var e:json.getAsJsonObject().entrySet()){var v=e.getValue();if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber())p.putDouble(e.getKey(),v.getAsDouble());else if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString())p.putString(e.getKey(),v.getAsString());}register(id,p);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(MiniBossDefinitions.class).error("Invalid mini-boss definition {}",id,ex);}});}
    });}
    private MiniBossDefinitions(){}
}
