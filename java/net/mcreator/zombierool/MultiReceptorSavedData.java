package net.mcreator.zombierool;

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

    public MultiReceptorSavedData() {
        for (String key : new String[]{"alpha","beta","omega","ultima"}) {
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
        if (receptorPositions.get(key).add(pos.asLong())) {
            setDirty();
        }
    }
    public void unregisterReceptor(String key, BlockPos pos) {
        // Only mark dirty if the position is actually removed
        if (receptorPositions.get(key).remove(pos.asLong())) {
            setDirty();
        }
    }
    public Set<BlockPos> getReceptorPositions(String key) {
        return receptorPositions.get(key)
               .stream().map(BlockPos::of).collect(Collectors.toSet());
    }

    public void load(CompoundTag tag) {
        for (String key : activatedStates.keySet()) {
            activatedStates.put(key, tag.getBoolean(key));

            ListTag listTag = tag.getList(key + "_receptors", Tag.TAG_LONG);
            Set<Long> set = new HashSet<>();
            for (Tag t : listTag) {
                set.add(((LongTag) t).getAsLong());
            }
            receptorPositions.put(key, set);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (String key : activatedStates.keySet()) {
            tag.putBoolean(key, activatedStates.get(key));

            ListTag listTag = new ListTag();
            for (long l : receptorPositions.get(key)) {
                listTag.add(LongTag.valueOf(l));
            }
            tag.put(key + "_receptors", listTag);
        }
        return tag;
    }
}