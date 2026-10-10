package me.cryo.zombierool.block.system;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Keeps destination IDs and Pack-a-Punch requirements visible across unloaded chunks. */
public final class TeleporterRegistry extends SavedData {
    public final Map<BlockPos,CompoundTag> pads=new HashMap<>();
    private final Map<String,Set<String>> conditions=new HashMap<>();
    public static TeleporterRegistry get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TeleporterRegistry::load,TeleporterRegistry::new,"zombierool_teleporters");}
    private static TeleporterRegistry load(CompoundTag n){
        TeleporterRegistry data=new TeleporterRegistry();for(Tag tag:n.getList("pads",10)){CompoundTag p=(CompoundTag)tag;data.pads.put(BlockPos.of(p.getLong("pos")),p.getCompound("config").copy());}return data;
    }
    @Override public CompoundTag save(CompoundTag n){ListTag list=new ListTag();pads.forEach((pos,config)->{CompoundTag p=new CompoundTag();p.putLong("pos",pos.asLong());p.put("config",config.copy());list.add(p);});n.put("pads",list);return n;}
    public void update(TeleporterSystem.Pad pad){CompoundTag n=pad.config.copy();n.putBoolean("linked",pad.linked);n.putLong("pendingUntil",pad.pendingUntil);n.putLong("cooldownUntil",pad.cooldownUntil);pads.put(pad.getBlockPos(),n);setDirty();}
    public void remove(BlockPos p){pads.remove(p);setDirty();}
    public boolean condition(String channel,String key){return conditions.getOrDefault(channel,Set.of()).contains(key);}
    public void condition(String channel,String key,boolean active){Set<String> set=conditions.computeIfAbsent(channel,k->new HashSet<>());if(active)set.add(key);else set.remove(key);}
    public int conditionCount(String channel){return (int)conditions.getOrDefault(channel,Set.of()).stream().filter(key->key.startsWith("defcon_")).count();}
    public void clearConditions(String channel){conditions.remove(channel);}
    public void reset(){conditions.clear();pads.values().forEach(c->{c.putBoolean("linked",false);c.putLong("pendingUntil",0);c.putLong("cooldownUntil",0);});setDirty();}
    public List<BlockPos> named(String id){return pads.entrySet().stream().filter(e->e.getValue().getString("id").equals(id)).map(Map.Entry::getKey).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();}
    public static boolean isSource(CompoundTag c){return c.getString("role").equals("SOURCE");}
    public boolean packUnlocked(){
        Map<String,Integer> required=new HashMap<>(),linked=new HashMap<>();
        pads.values().forEach(c->{if(c.getBoolean("enabled")&&isSource(c)){
            String channel=c.getString("channel");required.merge(channel,c.getInt("papLinks"),Math::max);
            if(c.getBoolean("linked")&&c.getInt("papLinks")>0)linked.merge(channel,1,Integer::sum);
        }});
        return required.entrySet().stream().allMatch(e->linked.getOrDefault(e.getKey(),0)>=e.getValue());
    }
}
