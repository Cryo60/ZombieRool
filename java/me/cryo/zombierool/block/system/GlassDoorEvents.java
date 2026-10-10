package me.cryo.zombierool.block.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid="zombierool")
public final class GlassDoorEvents {
    private static final Map<ServerLevel,Set<BlockPos>> loaded=new WeakHashMap<>();
    private static final Map<java.util.UUID,Long> lastStrike=new java.util.HashMap<>();
    public static void track(ServerLevel level,BlockPos pos){loaded.computeIfAbsent(level,k->new HashSet<>()).add(pos.immutable());}
    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk){
            for(var entity:chunk.getBlockEntities().values())if(entity.getBlockState().getBlock() instanceof GlassDefenseDoorBlock)track(level,entity.getBlockPos());
        }
    }
    @SubscribeEvent public static void strike(PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||event.getEntity().isCreative())return;
        if(!(level.getBlockState(event.getPos()).getBlock() instanceof GlassDefenseDoorBlock))return;
        long now=level.getGameTime();var id=event.getEntity().getUUID();
        if(now-lastStrike.getOrDefault(id,-100L)>=6){lastStrike.put(id,now);GlassDefenseDoorBlock.damage(level,event.getPos());}
        event.setCanceled(true);
    }
    public static void reset(ServerLevel level){
        lastStrike.clear();
        var positions=loaded.get(level);if(positions==null)return;
        for(BlockPos pos:positions){
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.getBlock() instanceof GlassDefenseDoorBlock)
                level.setBlock(pos,state.setValue(GlassDefenseDoorBlock.STAGE,7).setValue(GlassDefenseDoorBlock.BREACHED,false).setValue(GlassDefenseDoorBlock.PERMANENTLY_OPEN,false),3);
        }
    }
}
