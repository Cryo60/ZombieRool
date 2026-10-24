package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.block.*;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.config.GlobalSwitchState;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Map-local source selection, separate from the runtime powered state. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class BlockPowerSettings extends SavedData {
    public enum Mode {
        DEFAULT, GLOBAL, LOCAL, EITHER, BOTH;
        public boolean accepts(boolean global, boolean local) {
            return switch(this) { case GLOBAL -> global; case LOCAL -> local; case BOTH -> global && local; default -> global || local; };
        }
    }
    private final Map<BlockPos,Mode> modes=new HashMap<>();
    public static BlockPowerSettings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag->{
            var data=new BlockPowerSettings();
            for(Tag entry:tag.getList("blocks",Tag.TAG_COMPOUND)) {
                var n=(CompoundTag)entry;
                try { Mode mode=Mode.valueOf(n.getString("mode")); if(mode!=Mode.DEFAULT)data.modes.put(BlockPos.of(n.getLong("pos")),mode); } catch(IllegalArgumentException ignored) {}
            }
            return data;
        },BlockPowerSettings::new,"zombierool_block_power");
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list=new ListTag(); modes.forEach((pos,mode)->{var n=new CompoundTag();n.putLong("pos",pos.asLong());n.putString("mode",mode.name());list.add(n);}); tag.put("blocks",list);return tag;
    }
    public static BlockPos root(Level level,BlockPos pos) {
        pos=TraversalSystem.lower(level,pos);
        BlockPos machine=BrutusMachineLocks.machineRoot(level,pos);
        if(!machine.equals(pos))return machine;
        if(level.getBlockState(pos).getBlock() instanceof TeleporterSystem.Platform)return TeleporterSystem.center(level.getBlockState(pos),pos);
        if(level.getBlockEntity(pos) instanceof TraversalSystem.Anchor anchor) {
            var primary=anchor.configurationRoot();if(primary!=null)return primary.getBlockPos();
        }
        return pos;
    }
    public static boolean configurable(Level level,BlockPos pos) {
        var block=level.getBlockState(pos).getBlock();var be=level.getBlockEntity(root(level,pos));
        return block instanceof SignalTechnicalBlock || be instanceof PerksSystem.PerksAColaBlockEntity
            || be instanceof PackAPunchSystem.PackAPunchBlockEntity || be instanceof DerWunderfizzBlockEntity
            || be instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity || be instanceof TeleporterSystem.Pad
            || be instanceof TraversalSystem.Anchor a && a.kind()==TraversalSystem.Kind.ZIPLINE
            || be instanceof MapDeviceSystem.Device d && !MapDeviceSystem.isEmitter(d.kind());
    }
    /** Serialization must not resolve a block or request its unloading chunk. */
    public static Mode storedMode(ServerLevel level,BlockPos root) { return get(level).modes.getOrDefault(root,Mode.DEFAULT); }
    public static Mode mode(Level level,BlockPos pos) { return level instanceof ServerLevel server?storedMode(server,root(level,pos)):Mode.DEFAULT; }
    public static void setMode(ServerLevel level,BlockPos pos,Mode mode) {
        BlockPos p=root(level,pos);
        if(mode==Mode.DEFAULT)get(level).modes.remove(p);else get(level).modes.put(p.immutable(),mode);
        get(level).setDirty();MapPower.refreshSoon(level);
        var state=level.getBlockState(p);level.sendBlockUpdated(p,state,state,3);
    }
    /** An explicit mode also enables power gating on devices previously configured as independent. */
    public static boolean required(Level level,BlockPos pos,boolean required,boolean legacyPower) {
        Mode mode=mode(level,pos);
        if(mode==Mode.DEFAULT)return !required || legacyPower;
        return mode.accepts(GlobalSwitchState.isActivated(level),localSignal(level,root(level,pos)));
    }
    public static boolean localSignal(Level level,BlockPos pos) {
        if(PortableEquipment.localPower(level,pos))return true;
        var block=level.getBlockState(pos).getBlock();
        if(block instanceof TeleporterSystem.Platform) {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(at(level,pos.offset(x,0,z)))return true;
            return false;
        }
        if(block instanceof PerksSystem.PerksAColaBlock)return at(level,pos)||at(level,pos.above())||at(level,pos.below());
        if(level.getBlockEntity(pos) instanceof TraversalSystem.Anchor anchor&&anchor.kind()==TraversalSystem.Kind.ZIPLINE){
            if(at(level,pos)||at(level,pos.above()))return true;
            return anchor.exit!=null&&level.hasChunkAt(anchor.exit)&&(at(level,anchor.exit)||at(level,anchor.exit.above()));
        }
        var shape=level.getBlockState(pos).getShape(level,pos);
        int height=shape.isEmpty()?1:Math.max(1,Math.min(4,(int)Math.ceil(shape.max(Direction.Axis.Y))));
        for(int y=0;y<height;y++)if(at(level,pos.above(y)))return true;
        return false;
    }
    private static boolean at(Level level,BlockPos pos) {
        for(Direction direction:Direction.values()) {
            BlockPos neighbor=pos.relative(direction);BlockState state=level.getBlockState(neighbor);
            // A global activator touching a machine is not a second independent source.
            if(state.getBlock() instanceof ActivatorBlock)continue;
            if(state.getSignal(level,neighbor,direction)>0)return true;
            if(state.isRedstoneConductor(level,neighbor))for(Direction strong:Direction.values()) {
                BlockPos source=neighbor.relative(strong);BlockState sourceState=level.getBlockState(source);
                if(!(sourceState.getBlock() instanceof ActivatorBlock)&&sourceState.getDirectSignal(level,source,strong)>0)return true;
            }
        }
        return false;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void configure(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getEntity().isCreative()||event.getHand()!=InteractionHand.MAIN_HAND||event.getEntity().isShiftKeyDown())return;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        // Blocks with an existing configuration menu expose a button there.
        if(!(block instanceof SignalTechnicalBlock || block instanceof PackAPunchSystem.PackAPunchBlock || block instanceof DerWunderfizzBlock))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(event.getEntity() instanceof ServerPlayer player)BlockPowerPackets.open(player,event.getPos());
    }
    @SubscribeEvent public static void remove(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && configurable(level,event.getPos()))setMode(level,event.getPos(),Mode.DEFAULT);
    }
}
