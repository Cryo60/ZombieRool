package me.cryo.zombierool.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Set;

public final class BlockPosNbt {
    private BlockPosNbt() {}

    public static void loadSet(CompoundTag nbt, String key, Set<BlockPos> set) {
        if (!nbt.contains(key, 9)) {
            return;
        }
        ListTag list = nbt.getList(key, 10);
        for (int i = 0; i < list.size(); i++) {
            set.add(read(list.getCompound(i)));
        }
    }

    public static void saveSet(CompoundTag nbt, String key, Set<BlockPos> set) {
        ListTag list = new ListTag();
        for (BlockPos pos : set) {
            list.add(write(pos));
        }
        nbt.put(key, list);
    }

    public static BlockPos read(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static CompoundTag write(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }
}
