package me.cryo.zombierool.config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public class MultiReceptorState {
    private record DebugOverride(boolean value,boolean before) {}
    private static final java.util.Map<ServerLevel,java.util.Map<String,DebugOverride>> DEBUG=new java.util.WeakHashMap<>();
    public static void debugOverride(ServerLevel level,String key,Boolean value){
        var overrides=DEBUG.computeIfAbsent(level,unused->new java.util.HashMap<>());
        if(value==null){var previous=overrides.remove(key);if(previous!=null)setActivated(level,key,previous.before);refreshInputs(level);}
        else {boolean before=overrides.containsKey(key)?overrides.get(key).before:isActivated(level,key);overrides.put(key,new DebugOverride(value,before));setActivated(level,key,value);}
    }
    public static void clearDebugOverrides(ServerLevel level){
        var previous=DEBUG.remove(level);if(previous!=null)previous.forEach((key,value)->setActivated(level,key,value.before));refreshInputs(level);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stopDebug(net.minecraftforge.event.server.ServerStoppingEvent event){for(var level:event.getServer().getAllLevels())clearDebugOverrides(level);}

    public static void updateActivator(Level level,String key,BlockPos pos,boolean powered){
        if(level instanceof ServerLevel server)setActivated(level,key,MultiReceptorSavedData.get(server).updateActivator(key,pos,powered));
    }
    public static void removeActivator(Level level,String key,BlockPos pos){
        if(level instanceof ServerLevel server)setActivated(level,key,MultiReceptorSavedData.get(server).removeActivator(key,pos));
    }
    public static void refreshInputs(ServerLevel level){
        var data=MultiReceptorSavedData.get(level);
        for(String key:data.channels())for(BlockPos pos:data.getActivatorPositions(key))if(level.hasChunkAt(pos)){
            if(level.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.AbstractActivatorBlock activator)updateActivator(level,activator.signalChannel(),pos,level.hasNeighborSignal(pos));
            else removeActivator(level,key,pos);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void loadSignals(net.minecraftforge.event.level.ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk))return;
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount()+1,()->{
            if(!level.hasChunkAt(chunk.getPos().getWorldPosition()))return;
            for(int section=0;section<chunk.getSections().length;section++){
                var states=chunk.getSections()[section];
                if(!states.getStates().maybeHas(s->s.getBlock() instanceof me.cryo.zombierool.block.AbstractActivatorBlock||s.getBlock() instanceof me.cryo.zombierool.block.AbstractReceptorBlock))continue;
                int y0=level.getMinBuildHeight()+section*16;
                for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){
                    var state=states.getBlockState(x,y,z);var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,y0+y,chunk.getPos().getMinBlockZ()+z);
                    if(state.getBlock() instanceof me.cryo.zombierool.block.AbstractActivatorBlock activator)updateActivator(level,activator.signalChannel(),pos,level.hasNeighborSignal(pos));
                    else if(state.getBlock() instanceof me.cryo.zombierool.block.AbstractReceptorBlock receptor){registerReceptor(level,receptor.signalChannel(),pos);notifyReceptors(level,receptor.signalChannel());}
                }
            }
        }));
    }

    public static void setActivated(Level level, String key, boolean activated) {
        if (level instanceof ServerLevel sl) {
            var override=DEBUG.getOrDefault(sl,java.util.Map.of()).get(key);if(override!=null)activated=override.value;
            boolean changed=isActivated(level,key)!=activated;
            MultiReceptorSavedData.get(sl).setActivated(key, activated);
            if(changed)notifyReceptors(level,key);
        }
    }
    public static void notifyReceptors(Level level,String key){
        for(BlockPos pos:getReceptorPositions(level,key))if(level.hasChunkAt(pos)){
            var state=level.getBlockState(pos);var block=state.getBlock();
            if(!(block instanceof me.cryo.zombierool.block.AbstractReceptorBlock))continue;
            boolean powered=isActivated(level,key);
            if(state.getValue(me.cryo.zombierool.block.AbstractReceptorBlock.POWERED)!=powered)level.setBlock(pos,state.setValue(me.cryo.zombierool.block.AbstractReceptorBlock.POWERED,powered),3);
            level.updateNeighborsAt(pos,block);
            for(var direction:net.minecraft.core.Direction.values())level.updateNeighborsAt(pos.relative(direction),block);
        }
    }
    public static void reset(Level level){
        if(level instanceof ServerLevel sl)DEBUG.remove(sl);
        if(level instanceof ServerLevel server)for(String key:MultiReceptorSavedData.get(server).channels()){
            setActivated(level,key,false);notifyReceptors(level,key);
        }
    }

    public static boolean isActivated(Level level, String key) {
        if (level instanceof ServerLevel sl) {
            return MultiReceptorSavedData.get(sl).isActivated(key);
        }
        return false;
    }

    public static void registerReceptor(Level level, String key, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            MultiReceptorSavedData.get(sl).registerReceptor(key, pos);
        }
    }

    public static void unregisterReceptor(Level level, String key, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            MultiReceptorSavedData.get(sl).unregisterReceptor(key, pos);
        }
    }

    public static Set<BlockPos> getReceptorPositions(Level level, String key) {
        if (level instanceof ServerLevel sl) {
            return MultiReceptorSavedData.get(sl).getReceptorPositions(key);
        }
        return Collections.emptySet();
    }
}
