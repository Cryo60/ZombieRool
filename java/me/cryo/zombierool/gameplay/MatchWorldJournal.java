package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.api.AddonPlacements;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncOverlaysPacket;
import me.cryo.zombierool.util.WorldZombieroolPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers the first block, its block-entity data and its price overlays,
 * then puts them back at the end of the match.
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MatchWorldJournal {
    private static final Map<BlockPos, Snapshot> ORIGINAL = new HashMap<>();
    private static boolean restoring = false;
    private static boolean dirty = false;
    private static boolean loaded = false;

    private MatchWorldJournal() {}

    public static boolean isRestoring() {
        return restoring;
    }

    public static void note(ServerLevel level, BlockPos pos) {
        if (restoring || level.dimension() != Level.OVERWORLD || !WaveManager.isGameRunning()) return;
        ensureLoaded(level);
        BlockPos key = pos.immutable();
        if (ORIGINAL.containsKey(key)) return;
        BlockState state = level.getBlockState(key);
        CompoundTag beTag = null;
        BlockEntity be = level.getBlockEntity(key);
        if (be != null) beTag = be.saveWithoutMetadata();
        ORIGINAL.put(key, new Snapshot(state, beTag, captureOverlays(level, key)));
        dirty = true;
    }

    public static void restore(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        ensureLoaded(level);
        if (ORIGINAL.isEmpty()) { me.cryo.zombierool.block.system.GlassDoorEvents.reset(level); return; }
        restoring = true;
        try {
            WorldConfig config = WorldConfig.get(level);
            boolean overlaysChanged = false;
            for (Map.Entry<BlockPos, Snapshot> entry : ORIGINAL.entrySet()) {
                BlockPos pos = entry.getKey();
                Snapshot snap = entry.getValue();
                level.setBlock(pos, snap.state, 3);
                if (snap.blockEntity != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        be.load(snap.blockEntity);
                        be.setChanged();
                    }
                }
                level.sendBlockUpdated(pos, snap.state, level.getBlockState(pos), 3);
                for (String key : snap.overlays.getAllKeys()) {
                    config.addMapOverlay(key, snap.overlays.getString(key));
                    overlaysChanged = true;
                }
            }
            if (overlaysChanged) {
                S2CSyncOverlaysPacket packet = new S2CSyncOverlaysPacket(config.getMapOverlays());
                for (ServerPlayer player : level.players()) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
            me.cryo.zombierool.block.system.GlassDoorEvents.reset(level);
            ORIGINAL.clear();
            dirty = false;
            File file = journalFile(level);
            if (file.exists()) file.delete();
        } finally {
            restoring = false;
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            restore(level);
            AddonPlacements.apply(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            if (dirty) save(level);
            ORIGINAL.clear();
            loaded = false;
            dirty = false;
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !dirty) return;
        ServerLevel level = event.getServer().overworld();
        if (level != null) save(level);
    }

    private static CompoundTag captureOverlays(ServerLevel level, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        String prefix = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_";
        for (Map.Entry<String, String> entry : WorldConfig.get(level).getMapOverlays().entrySet()) {
            if (entry.getKey().startsWith(prefix)) tag.putString(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    private static void ensureLoaded(ServerLevel level) {
        if (loaded) return;
        loaded = true;
        File file = journalFile(level);
        if (!file.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
                BlockState state = readState(entry.getCompound("state"));
                CompoundTag be = entry.contains("be", Tag.TAG_COMPOUND) ? entry.getCompound("be") : null;
                CompoundTag overlays = entry.contains("overlays", Tag.TAG_COMPOUND) ? entry.getCompound("overlays") : new CompoundTag();
                ORIGINAL.put(pos, new Snapshot(state, be, overlays));
            }
        } catch (Exception e) {
            me.cryo.zombierool.ZombieroolMod.LOGGER.error("[ZombieRool] Could not read match journal", e);
        }
    }

    private static BlockState readState(CompoundTag tag) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(tag.getString("Name")));
        if (block == null) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockState state = block.defaultBlockState();
        CompoundTag props = tag.getCompound("Properties");
        for (String key : props.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            if (property != null) state = setProperty(state, property, props.getString(key));
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    private static void save(ServerLevel level) {
        try {
            WorldZombieroolPaths.ensureZombieroolDir(level);
            ListTag list = new ListTag();
            for (Map.Entry<BlockPos, Snapshot> entry : ORIGINAL.entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("x", entry.getKey().getX());
                tag.putInt("y", entry.getKey().getY());
                tag.putInt("z", entry.getKey().getZ());
                tag.put("state", NbtUtils.writeBlockState(entry.getValue().state));
                if (entry.getValue().blockEntity != null) tag.put("be", entry.getValue().blockEntity);
                if (!entry.getValue().overlays.isEmpty()) tag.put("overlays", entry.getValue().overlays);
                list.add(tag);
            }
            CompoundTag root = new CompoundTag();
            root.put("blocks", list);
            NbtIo.writeCompressed(root, journalFile(level));
            dirty = false;
        } catch (Exception e) {
            me.cryo.zombierool.ZombieroolMod.LOGGER.error("[ZombieRool] Could not save match journal", e);
        }
    }

    private static File journalFile(ServerLevel level) {
        return new File(WorldZombieroolPaths.getZombieroolDir(level), "match_journal.nbt");
    }

    private static final class Snapshot {
        final BlockState state;
        final CompoundTag blockEntity;
        final CompoundTag overlays;

        Snapshot(BlockState state, CompoundTag blockEntity, CompoundTag overlays) {
            this.state = state;
            this.blockEntity = blockEntity;
            this.overlays = overlays == null ? new CompoundTag() : overlays;
        }
    }
}
