package net.mcreator.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class GlobalSwitchSavedData extends SavedData {
    private static final String DATA_NAME = "global_switch_data";

    private boolean globalActivated = false;
    private final Set<BlockPos> activatorPositions = new HashSet<>();

    public GlobalSwitchSavedData() {}

    // Méthode de lecture depuis un CompoundTag
    public static GlobalSwitchSavedData load(CompoundTag tag) {
        GlobalSwitchSavedData data = new GlobalSwitchSavedData();
        data.globalActivated = tag.getBoolean("globalActivated");

        // Chargement des positions des activateurs
        if (tag.contains("activatorPositions", 9)) { // 9 = type LIST
            ListTag list = tag.getList("activatorPositions", 10); // 10 = type COMPOUND
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                int x = posTag.getInt("x");
                int y = posTag.getInt("y");
                int z = posTag.getInt("z");
                data.activatorPositions.add(new BlockPos(x, y, z));
            }
        }
        return data;
    }

    // Sauvegarde des données dans un CompoundTag
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("globalActivated", globalActivated);

        // Sauvegarde des positions des activateurs
        ListTag list = new ListTag();
        for (BlockPos pos : activatorPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            list.add(posTag);
        }
        tag.put("activatorPositions", list);
        return tag;
    }

    // Méthodes d'accès
    public boolean isActivated() {
        return globalActivated;
    }

    public void setActivated(boolean activated) {
        this.globalActivated = activated;
        setDirty();
    }

    public Set<BlockPos> getActivatorPositions() {
        return new HashSet<>(activatorPositions);
    }

    public void registerActivator(BlockPos pos) {
        activatorPositions.add(pos);
        setDirty();
    }

    public void unregisterActivator(BlockPos pos) {
        activatorPositions.remove(pos);
        setDirty();
    }

    // Méthode utilitaire pour récupérer ou créer l'instance de données attachées au monde
    public static GlobalSwitchSavedData get(Level world) {
        if (world instanceof ServerLevel serverWorld) {
            return serverWorld.getDataStorage().computeIfAbsent(
                    GlobalSwitchSavedData::load,
                    GlobalSwitchSavedData::new,
                    DATA_NAME);
        } else {
            throw new IllegalStateException("GlobalSwitchSavedData ne peut être récupéré que sur un ServerLevel !");
        }
    }
}
