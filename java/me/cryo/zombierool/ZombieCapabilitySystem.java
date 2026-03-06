package me.cryo.zombierool.core.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ZombieCapabilitySystem {

    // 1. Interface
    public interface IData extends INBTSerializable<CompoundTag> {
        int getMoney();
        void setMoney(int amount);
        void addMoney(int amount);
        boolean removeMoney(int amount);
        boolean isDown();
        void setDown(boolean down);
        List<String> getActivePerks();
        void addPerk(String perkId);
        boolean hasPerk(String perkId);
        void removePerk(String perkId);
        void clearPerks();
        int getPerkCount();
        void copyFrom(IData source);
        boolean isDirty();
        void setDirty(boolean dirty);
    }

    // 2. Implementation
    public static class Impl implements IData {
        private int money = 500;
        private boolean isDown = false;
        private final List<String> perks = new ArrayList<>();
        private boolean isDirty = true;

        @Override public int getMoney() { return money; }
        @Override public void setMoney(int amount) { this.money = Math.max(0, amount); this.isDirty = true; }
        @Override public void addMoney(int amount) { this.money += amount; this.isDirty = true; }
        @Override public boolean removeMoney(int amount) {
            if (this.money >= amount) {
                this.money -= amount;
                this.isDirty = true;
                return true;
            }
            return false;
        }
        @Override public boolean isDown() { return isDown; }
        @Override public void setDown(boolean down) { this.isDown = down; this.isDirty = true; }
        @Override public List<String> getActivePerks() { return new ArrayList<>(perks); }
        @Override public void addPerk(String perkId) { if (!perks.contains(perkId)) { perks.add(perkId); this.isDirty = true; } }
        @Override public boolean hasPerk(String perkId) { return perks.contains(perkId); }
        @Override public void removePerk(String perkId) { if (perks.remove(perkId)) { this.isDirty = true; } }
        @Override public void clearPerks() { if (!perks.isEmpty()) { perks.clear(); this.isDirty = true; } }
        @Override public int getPerkCount() { return perks.size(); }
        @Override public void copyFrom(IData source) {
            this.money = source.getMoney();
            this.isDown = source.isDown();
            this.perks.clear();
            this.perks.addAll(source.getActivePerks());
            this.isDirty = true;
        }
        @Override public boolean isDirty() { return isDirty; }
        @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }

        @Override public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Money", money);
            tag.putBoolean("IsDown", isDown);
            ListTag perkList = new ListTag();
            for (String perk : perks) perkList.add(StringTag.valueOf(perk));
            tag.put("Perks", perkList);
            return tag;
        }
        @Override public void deserializeNBT(CompoundTag nbt) {
            if (nbt.contains("Money")) this.money = nbt.getInt("Money");
            if (nbt.contains("IsDown")) this.isDown = nbt.getBoolean("IsDown");
            if (nbt.contains("Perks")) {
                this.perks.clear();
                ListTag perkList = nbt.getList("Perks", Tag.TAG_STRING);
                for (int i = 0; i < perkList.size(); i++) this.perks.add(perkList.getString(i));
            }
        }
    }

    // 3. Provider
    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        public static final Capability<IData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>(){});
        private IData backend = null;
        private final LazyOptional<IData> optional = LazyOptional.of(this::createBackend);

        private IData createBackend() {
            if (this.backend == null) this.backend = new Impl();
            return this.backend;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == PLAYER_DATA) return optional.cast();
            return LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return this.backend.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag nbt) { this.backend.deserializeNBT(nbt); }
    }
}