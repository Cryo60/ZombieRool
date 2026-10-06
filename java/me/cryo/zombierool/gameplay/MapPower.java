package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.block.ActivatorBlock;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.PowerSwitchBlock;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.system.PackAPunchSystem;
import me.cryo.zombierool.block.system.PerksSystem;
import me.cryo.zombierool.config.GlobalSwitchState;
import me.cryo.zombierool.config.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Perks, Pack-a-Punch and Wunderfizz only sampled redstone when they were placed
 * or when a neighbour changed. Turning the power on, or placing the activator
 * afterwards, left them off. This refreshes every loaded machine from the real
 * power state, whichever block was placed first.
 */
@Mod.EventBusSubscriber(modid = "zombierool")
public final class MapPower {
    private static int scheduledTick = Integer.MIN_VALUE;

    private MapPower() {}

    public static boolean receives(Level level, BlockPos pos) {
        if (level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above()) || level.hasNeighborSignal(pos.below())) {
            return true;
        }
        if (!(level instanceof ServerLevel server) || !GlobalSwitchState.isActivated(server)) return false;
        return nearActivator(level, pos) || nearActivator(level, pos.above()) || nearActivator(level, pos.below());
    }

    private static boolean nearActivator(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ActivatorBlock) return true;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof ActivatorBlock) return true;
        }
        return false;
    }

    public static void refreshSoon(ServerLevel level) {
        int now = level.getServer().getTickCount();
        if (scheduledTick == now) return;
        scheduledTick = now;
        level.getServer().tell(new TickTask(now + 1, () -> refresh(level)));
        level.getServer().tell(new TickTask(now + 8, () -> refresh(level)));
    }

    public static void refresh(ServerLevel level) {
        applySwitchState(level);
        List<BlockPos> perks = new ArrayList<>();
        List<BlockPos> punches = new ArrayList<>();
        List<BlockPos> fizz = new ArrayList<>();
        forEachLoadedChunk(level, chunk -> {
            for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (be instanceof PerksSystem.PerksAColaBlockEntity) perks.add(be.getBlockPos());
                else if (be instanceof PackAPunchSystem.PackAPunchBlockEntity) punches.add(be.getBlockPos());
                else if (be instanceof DerWunderfizzBlockEntity) fizz.add(be.getBlockPos());
            }
        });
        for (BlockPos pos : perks) syncPerk(level, pos);
        for (BlockPos pos : punches) syncFlag(level, pos, PackAPunchSystem.PackAPunchBlock.POWERED);
        for (BlockPos pos : fizz) syncFlag(level, pos, DerWunderfizzBlock.POWERED);
    }

    /** True when the perk machine is powered. Also writes that into the block and its data. */
    public static boolean syncPerk(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PerksSystem.PerksAColaBlock)) return false;
        boolean powered = receives(level, pos);
        boolean changed = false;
        if (level.getBlockEntity(pos) instanceof PerksSystem.PerksAColaBlockEntity be && be.isPowered() != powered) {
            be.setPowered(powered);
            changed = true;
        }
        if (state.getValue(PerksSystem.PerksAColaBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(PerksSystem.PerksAColaBlock.POWERED, powered), 3);
        } else if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
        return powered;
    }

    public static void syncFlag(ServerLevel level, BlockPos pos, BooleanProperty property) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(property)) return;
        boolean powered = receives(level, pos);
        if (state.getValue(property) != powered) {
            level.setBlock(pos, state.setValue(property, powered), 3);
        }
    }

    public static void resetPerkCoins(ServerLevel level) {
        forEachLoadedChunk(level, chunk -> {
            for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (be instanceof PerksSystem.PerksAColaBlockEntity perk) perk.resetCoins();
            }
        });
    }

    private static void forEachLoadedChunk(ServerLevel level, Consumer<LevelChunk> consumer) {
        Set<ChunkPos> visited = new HashSet<>();
        int radius = Math.min(12, Math.max(4, level.getServer().getPlayerList().getViewDistance()));
        List<BlockPos> anchors = new ArrayList<>();
        for (ServerPlayer player : level.players()) anchors.add(player.blockPosition());
        anchors.add(level.getSharedSpawnPos());
        anchors.addAll(WorldConfig.get(level).getPowerSwitchPositions());
        anchors.addAll(GlobalSwitchState.getActivatorPositions(level));
        anchors.addAll(WorldConfig.get(level).getWunderfizzPositions());
        for (BlockPos anchor : anchors) {
            if (anchor == null) continue;
            int cx = anchor.getX() >> 4;
            int cz = anchor.getZ() >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!visited.add(new ChunkPos(cx + dx, cz + dz))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) consumer.accept(chunk);
                }
            }
        }
    }

    private static void applySwitchState(ServerLevel level) {
        boolean found = false;
        boolean on = false;
        for (BlockPos pos : WorldConfig.get(level).getPowerSwitchPositions()) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PowerSwitchBlock)) continue;
            found = true;
            if (state.getValue(PowerSwitchBlock.POWERED)) on = true;
        }
        if (found) GlobalSwitchState.setActivated(level, on);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            refreshSoon(level);
        }
    }
}
