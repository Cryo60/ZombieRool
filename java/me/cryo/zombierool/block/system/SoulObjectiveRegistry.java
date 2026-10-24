package me.cryo.zombierool.block.system;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared, dimension-local objectives. Completion survives chunk unloads, never match resets. */
public final class SoulObjectiveRegistry extends SavedData {
    private final Map<BlockPos,CompoundTag> entries=new HashMap<>();
    private final Map<String,Set<BlockPos>> names=new HashMap<>();
    private final Map<String,String> aliases=new HashMap<>();
    private int nextSoul=1,nextCerberus=1;
    public long epoch,revision;
    public static SoulObjectiveRegistry get(ServerLevel level){return level.getDataStorage().computeIfAbsent(SoulObjectiveRegistry::load,SoulObjectiveRegistry::new,"zombierool_soul_objectives");}
    public static String cleanId(String id){String clean=id.trim().replaceAll("[^a-zA-Z0-9_.:-]","_");return clean.substring(0,Math.min(64,clean.length()));}
    private static SoulObjectiveRegistry load(CompoundTag n){var r=new SoulObjectiveRegistry();r.epoch=n.getLong("epoch");r.nextSoul=Math.max(1,n.getInt("nextSoul"));r.nextCerberus=Math.max(1,n.getInt("nextCerberus"));for(Tag t:n.getList("entries",10)){var e=(CompoundTag)t;r.entries.put(BlockPos.of(e.getLong("pos")),e.copy());}var aliases=n.getCompound("aliases");for(String key:aliases.getAllKeys())r.aliases.put(key,aliases.getString(key));r.index();return r;}
    private void index(){names.clear();entries.forEach((p,e)->names.computeIfAbsent(e.getString("id"),k->new HashSet<>()).add(p));}
    @Override public CompoundTag save(CompoundTag n){n.putLong("epoch",epoch);n.putInt("nextSoul",nextSoul);n.putInt("nextCerberus",nextCerberus);var list=new ListTag();entries.values().forEach(e->list.add(e.copy()));n.put("entries",list);var a=new CompoundTag();aliases.forEach(a::putString);n.put("aliases",a);return n;}
    public String automaticId(BlockPos position,boolean cerberus,String oldId){
        var existing=entries.get(position);
        if(existing!=null&&existing.getString("id").matches("[SC][0-9]+"))return existing.getString("id");
        String id;
        do{id=cerberus?"C"+nextCerberus++:"S"+nextSoul++;}while(names.containsKey(id)||aliases.containsKey(id));
        if(!oldId.isBlank())aliases.put(oldId,id);
        // Reserve immediately so another placement cannot take this ID before sync.
        var entry=new CompoundTag();entry.putLong("pos",position.asLong());entry.putString("id",id);entry.putBoolean("completed",existing!=null&&existing.getBoolean("completed"));
        entries.put(position,entry);index();revision++;setDirty();return id;
    }
    private String resolve(String id){return names.containsKey(id)?id:aliases.getOrDefault(id,id);}
    public void update(SoulChestSystem.Chest chest){
        var e=new CompoundTag();e.putLong("pos",chest.getBlockPos().asLong());e.putString("id",chest.objectiveId);e.putBoolean("completed",chest.enabled&&chest.completed);
        if(e.equals(entries.get(chest.getBlockPos())))return;
        entries.put(chest.getBlockPos(),e);index();revision++;setDirty();
    }
    public void remove(BlockPos p){if(entries.remove(p)!=null){index();revision++;setDirty();}}
    public boolean unique(String id){return names.getOrDefault(resolve(id),Set.of()).size()==1;}
    public BlockPos position(String id){var positions=names.getOrDefault(resolve(id),Set.of());return positions.size()==1?positions.iterator().next():null;}
    public List<String> ids(){return names.keySet().stream().sorted().toList();}
    public boolean complete(String id){var positions=names.getOrDefault(resolve(id),Set.of());return positions.size()==1&&entries.get(positions.iterator().next()).getBoolean("completed");}
    public boolean allComplete(String ids){var keys=parse(ids);return !keys.isEmpty()&&keys.size()<=64&&keys.stream().allMatch(this::complete);}
    public static List<String> parse(String ids){return Arrays.stream(ids.split("[,;\\s]+",-1)).map(String::trim).filter(s->!s.isEmpty()).distinct().limit(65).toList();}
    public void reset(){epoch++;entries.values().forEach(e->e.putBoolean("completed",false));revision++;setDirty();}
}
