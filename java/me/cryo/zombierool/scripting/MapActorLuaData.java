package me.cryo.zombierool.scripting;

import net.minecraft.nbt.*;
import org.luaj.vm2.*;

/** Bounded conversion for new per-entity fields, including lists of summoned UUIDs. */
public final class MapActorLuaData {
    public static CompoundTag compound(LuaTable table){return object(table,0,new int[]{2048});}
    private static CompoundTag object(LuaTable table,int depth,int[] remaining){
        var out=new CompoundTag();for(var key:table.keys()){
            if(!key.isstring()||key.isnumber())throw new LuaError("Object keys must be strings");
            String name=key.checkjstring();if(name.length()>80)throw new LuaError("Data key too long");
            out.put(name,value(table.get(key),depth+1,remaining));
        }return out;
    }
    private static Tag value(LuaValue value,int depth,int[] remaining){
        if(depth>8||--remaining[0]<0)throw new LuaError("Actor data exceeds nesting or entry limits");
        if(value.isboolean())return ByteTag.valueOf(value.checkboolean());
        if(value.isnumber()){double n=value.checkdouble();if(!Double.isFinite(n))throw new LuaError("Finite actor data required");return DoubleTag.valueOf(n);}
        if(value.istable()){
            var table=value.checktable();if(table.length()==0)return object(table,depth,remaining);
            if(table.length()>256||table.keys().length!=table.length())throw new LuaError("Use an array or an object, not both");
            var list=new ListTag();for(int i=1;i<=table.length();i++){var item=value(table.get(i),depth+1,remaining);if(!list.isEmpty()&&list.getElementType()!=item.getId())throw new LuaError("Array entries must have the same type");list.add(item);}return list;
        }
        if(value.isstring()){String s=value.checkjstring();if(s.length()>4096)throw new LuaError("Actor data string too long");return StringTag.valueOf(s);}
        throw new LuaError("Actor data supports numbers, strings, booleans and tables");
    }
    private MapActorLuaData(){}
}
