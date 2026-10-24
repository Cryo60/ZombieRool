package me.cryo.zombierool.config;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Addons declare their map settings once; persistence and the menu use the same schema. */
public final class MapConfigExtensions {
    private record Schema(String category,CompoundTag defaults) {}
    private static final Map<ResourceLocation,Schema> SCHEMAS=new LinkedHashMap<>();
    public static synchronized void register(ResourceLocation id,String category,CompoundTag defaults){
        if(!Set.of("general","enemies","weapons","extras").contains(category))throw new IllegalArgumentException("Unknown map config category");
        if(SCHEMAS.containsKey(id))throw new IllegalArgumentException("Duplicate map settings: "+id);
        SCHEMAS.put(id,new Schema(category,defaults.copy()));
    }
    public static CompoundTag schemas(){var out=new CompoundTag();SCHEMAS.forEach((id,s)->{var row=new CompoundTag();row.putString("category",s.category);row.put("defaults",s.defaults.copy());out.put(id.toString(),row);});return out;}
    public static CompoundTag sanitize(CompoundTag input){
        // Keep settings for temporarily absent addons, so uninstalling does not destroy map data.
        var out=input.copy();SCHEMAS.forEach((id,s)->out.put(id.toString(),merge(s.defaults,input.getCompound(id.toString()))));return out;
    }
    private static CompoundTag merge(CompoundTag defaults,CompoundTag input){var out=defaults.copy();for(String key:defaults.getAllKeys()){var d=defaults.get(key);var v=input.get(key);if(v==null)continue;if(d instanceof CompoundTag c&&v instanceof CompoundTag n)out.put(key,merge(c,n));else if(d instanceof ByteTag&&v instanceof NumericTag)out.putBoolean(key,input.getBoolean(key));else if(d instanceof NumericTag&&v instanceof NumericTag n&&Double.isFinite(n.getAsDouble())){if(d instanceof IntTag)out.putInt(key,n.getAsInt());else out.putDouble(key,n.getAsDouble());}else if(d instanceof StringTag&&v instanceof StringTag)out.putString(key,input.getString(key));}return out;}
    private MapConfigExtensions() {}
}
