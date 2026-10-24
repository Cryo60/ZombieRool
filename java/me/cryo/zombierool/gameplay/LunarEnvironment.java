package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dimension-local, persistent atmosphere volumes. The smallest matching volume wins, independently of chunk loading order. */
public final class LunarEnvironment extends SavedData {
    private final Map<String,net.minecraft.core.BlockPos> blockIds=new LinkedHashMap<>();
    public net.minecraft.core.BlockPos blockPosition(String id){return blockIds.get(id);}
    public boolean canBind(String id,net.minecraft.core.BlockPos pos){return id.isBlank()||!blockIds.containsKey(id)||blockIds.get(id).equals(pos);}
    public void bind(String id,net.minecraft.core.BlockPos pos){if(!id.isBlank()&&pos.equals(blockIds.get(id)))return;blockIds.entrySet().removeIf(e->e.getValue().equals(pos));if(!id.isBlank())blockIds.put(id,pos.immutable());setDirty();}
    public void unbind(net.minecraft.core.BlockPos pos){blockIds.entrySet().removeIf(e->e.getValue().equals(pos));setDirty();}
    public record Conditions(double gravity, boolean vacuum) {}
    private record Zone(AABB box, Conditions conditions) {}
    private final Map<String,Zone> zones = new LinkedHashMap<>();
    public static LunarEnvironment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LunarEnvironment::load,LunarEnvironment::new,"zombierool_lunar_zones");
    }
    public static double gravity(double value) { return Double.isFinite(value)?Math.max(.02,Math.min(4,value)):1; }
    public void put(String id,AABB box,double gravity,boolean vacuum) {
        if(id==null||id.isBlank()||id.length()>128)throw new IllegalArgumentException("Zone ID required (1-128 characters)");
        if(!Double.isFinite(box.minX+box.minY+box.minZ+box.maxX+box.maxY+box.maxZ))throw new IllegalArgumentException("Finite zone bounds required");
        zones.remove(id);zones.put(id,new Zone(box,new Conditions(gravity(gravity),vacuum)));setDirty();
    }
    public boolean remove(String id) { boolean removed=zones.remove(id)!=null;if(removed)setDirty();return removed; }
    public boolean matches(String id,AABB box,double gravity,boolean vacuum){var z=zones.get(id);return z!=null&&z.box.equals(box)&&z.conditions.gravity==gravity&&z.conditions.vacuum==vacuum;}
    public Conditions at(double x,double y,double z,Conditions fallback) {
        Conditions result=fallback;double smallest=Double.POSITIVE_INFINITY;String selected=null;
        for(var entry:zones.entrySet()){
            Zone zone=entry.getValue();if(!zone.box.contains(x,y,z))continue;
            double volume=zone.box.getXsize()*zone.box.getYsize()*zone.box.getZsize();
            if(volume<smallest||(volume==smallest&&(selected==null||entry.getKey().compareTo(selected)<0))){smallest=volume;selected=entry.getKey();result=zone.conditions;}
        }
        return result;
    }
    public CompoundTag describe() { return save(new CompoundTag()); }
    @Override public CompoundTag save(CompoundTag out) {
        ListTag list=new ListTag();zones.forEach((id,zone)->{var n=new CompoundTag();n.putString("id",id);var b=zone.box;
            n.putDouble("x1",b.minX);n.putDouble("y1",b.minY);n.putDouble("z1",b.minZ);n.putDouble("x2",b.maxX);n.putDouble("y2",b.maxY);n.putDouble("z2",b.maxZ);
            n.putDouble("gravity",zone.conditions.gravity);n.putBoolean("vacuum",zone.conditions.vacuum);list.add(n);});out.put("zones",list);var ids=new CompoundTag();blockIds.forEach((id,pos)->ids.putLong(id,pos.asLong()));out.put("blockIds",ids);return out;
    }
    public static LunarEnvironment load(CompoundTag in) {
        var result=new LunarEnvironment();for(var value:in.getList("zones",10)){var n=(CompoundTag)value;
            result.put(n.getString("id"),new AABB(n.getDouble("x1"),n.getDouble("y1"),n.getDouble("z1"),n.getDouble("x2"),n.getDouble("y2"),n.getDouble("z2")),n.getDouble("gravity"),n.getBoolean("vacuum"));}var ids=in.getCompound("blockIds");for(String id:ids.getAllKeys())result.blockIds.put(id,net.minecraft.core.BlockPos.of(ids.getLong(id)));return result;
    }
}
