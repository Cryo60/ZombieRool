package me.cryo.zombierool.config;

import com.mojang.serialization.Lifecycle; // si nécessaire
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MultiReceptorSavedData extends SavedData {
    private static final String DATA_NAME = "multi_receptor_data";

    // état activé/désactivé
    private final Map<String, Boolean> activatedStates = new HashMap<>();
    // positions des récepteurs pour chaque canal (“alpha”, “beta”, …)
    private final Map<String, Set<Long>> receptorPositions = new HashMap<>();
    private final Map<String, Map<Long, Boolean>> activators = new HashMap<>();

    public boolean updateActivator(String key, BlockPos pos, boolean powered) {
        var sources=activators.computeIfAbsent(key,k->new HashMap<>());
        Boolean previous=sources.put(pos.asLong(),powered);
        if(previous==null||previous!=powered)setDirty();
        return sources.containsValue(true);
    }
    public boolean removeActivator(String key, BlockPos pos) {
        var sources=activators.get(key);
        if(sources==null)return false;
        if(sources.remove(pos.asLong())!=null)setDirty();
        return sources.containsValue(true);
    }
    public Set<BlockPos> getActivatorPositions(String key){return activators.getOrDefault(key,java.util.Collections.emptyMap()).keySet().stream().map(BlockPos::of).collect(Collectors.toSet());}

    public MultiReceptorSavedData() {
        for (var channel : me.cryo.zombierool.block.SignalChannel.values()) {
            String key=channel.id();
            activatedStates.put(key, false);
            receptorPositions.put(key, new HashSet<>());
        }
    }

    public static MultiReceptorSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            tag -> {
                MultiReceptorSavedData data = new MultiReceptorSavedData();
                data.load(tag);
                return data;
            },
            MultiReceptorSavedData::new,
            DATA_NAME
        );
    }

    public void setActivated(String key, boolean activated) {
        // Only mark dirty if the state actually changes
        if (activatedStates.getOrDefault(key, false) != activated) {
            activatedStates.put(key, activated);
            setDirty();
        }
    }
    public boolean isActivated(String key) {
        return activatedStates.getOrDefault(key, false);
    }

    public void registerReceptor(String key, BlockPos pos) {
        // Only mark dirty if the position is actually added
        if (receptorPositions.computeIfAbsent(key,k->new HashSet<>()).add(pos.asLong())) {
            setDirty();
        }
    }
    public void unregisterReceptor(String key, BlockPos pos) {
        // Only mark dirty if the position is actually removed
        if (receptorPositions.getOrDefault(key,java.util.Collections.emptySet()).remove(pos.asLong())) {
            setDirty();
        }
    }
    public Set<BlockPos> getReceptorPositions(String key) {
        return receptorPositions.getOrDefault(key,java.util.Collections.emptySet())
               .stream().map(BlockPos::of).collect(Collectors.toSet());
    }
    public Set<String> channels(){Set<String> keys=new HashSet<>(activatedStates.keySet());keys.addAll(receptorPositions.keySet());keys.addAll(activators.keySet());return keys;}

    public void load(CompoundTag tag) {
        for(String key:tag.getAllKeys())if(tag.contains(key,Tag.TAG_BYTE))activatedStates.putIfAbsent(key,false);
        for (String key : activatedStates.keySet()) {
            activatedStates.put(key, tag.getBoolean(key));

            ListTag listTag = tag.getList(key + "_receptors", Tag.TAG_LONG);
            Set<Long> set = new HashSet<>();
            for (Tag t : listTag) {
                set.add(((LongTag) t).getAsLong());
            }
            receptorPositions.put(key, set);
            var sources=new HashMap<Long,Boolean>();
            for(Tag entry:tag.getList(key+"_activators",Tag.TAG_COMPOUND)) {
                var n=(CompoundTag)entry;sources.put(n.getLong("pos"),n.getBoolean("powered"));
            }
            activators.put(key,sources);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (String key : channels()) {
            tag.putBoolean(key, activatedStates.getOrDefault(key,false));

            ListTag listTag = new ListTag();
            for (long l : receptorPositions.getOrDefault(key,java.util.Collections.emptySet())) {
                listTag.add(LongTag.valueOf(l));
            }
            tag.put(key + "_receptors", listTag);
            var sources=new ListTag();
            activators.getOrDefault(key,java.util.Collections.emptyMap()).forEach((pos,powered)->{var n=new CompoundTag();n.putLong("pos",pos);n.putBoolean("powered",powered);sources.add(n);});
            tag.put(key+"_activators",sources);
        }
        return tag;
    }
}
