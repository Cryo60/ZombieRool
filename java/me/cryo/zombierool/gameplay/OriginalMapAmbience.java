package me.cryo.zombierool.gameplay;

import com.google.gson.*;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local original environmental aliases, keeping randomized variants in each sound event. */
public final class OriginalMapAmbience {
    private static final Map<String,ListTag> PRESETS=load();
    private static Map<String,ListTag> load(){
        var result=new LinkedHashMap<String,ListTag>();
        try(var in=OriginalMapAmbience.class.getResourceAsStream("/assets/zombierool/original-map-ambience.json")){
            if(in==null)throw new IOException("Missing original ambience definitions");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:root.entrySet()){
                var rows=new ListTag();for(JsonElement value:entry.getValue().getAsJsonArray()){
                    var n=new CompoundTag();for(var field:value.getAsJsonObject().entrySet()){
                        var p=field.getValue().getAsJsonPrimitive();if(p.isBoolean())n.putBoolean(field.getKey(),p.getAsBoolean());else if(p.isNumber())n.putDouble(field.getKey(),p.getAsDouble());else n.putString(field.getKey(),p.getAsString());
                    }rows.add(MapAtmosphere.sanitizeSource(n));
                }result.put(entry.getKey(),rows);
            }
        }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load original map ambience",e);}
        return Collections.unmodifiableMap(result);
    }
    public static ListTag sources(String preset){var rows=PRESETS.get(preset);return rows==null?new ListTag():rows.copy();}
    public static List<String> soundIds(){var out=new ArrayList<String>();for(var rows:PRESETS.values())for(Tag row:rows)out.add(((CompoundTag)row).getString("sound"));return List.copyOf(out);}
}
