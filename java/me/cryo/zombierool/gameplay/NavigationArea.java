package me.cryo.zombierool.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;

/** Cheap admission check before allocating a full pathfinding region for a player. */
public final class NavigationArea {
    private static final TagKey<Block> ALLOWED=TagKey.create(Registries.BLOCK,new ResourceLocation("zombierool","allowed_blocks"));
    private static final TagKey<Block> LIMITS=TagKey.create(Registries.BLOCK,new ResourceLocation("zombierool","limit_blocks"));
    private static final Map<Level,Cache> CACHES=new WeakHashMap<>();
    private static final class Cache {long tick=Long.MIN_VALUE;final Map<Long,Boolean> positions=new HashMap<>();}
    private NavigationArea(){}
    public static void invalidate(Level level){CACHES.remove(level);}
    public static boolean allows(Level level,BlockPos pos){
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))return false;
        Cache cache=CACHES.computeIfAbsent(level,key->new Cache());
        if(cache.tick!=level.getGameTime()||cache.positions.size()>4096){cache.positions.clear();cache.tick=level.getGameTime();}
        return cache.positions.computeIfAbsent(pos.asLong(),key->scan(level,pos));
    }
    private static boolean scan(Level level,BlockPos pos){
        if(me.cryo.zombierool.block.system.TraversalSystem.endpoint(level,pos))return true;
        var cursor=pos.mutable();
        // Match the walk evaluator: an allowed marker below wins before a limit;
        // searching upward never crosses a limit block, including the feet cell.
        for(int y=pos.getY();y>=level.getMinBuildHeight();y--){
            cursor.setY(y);var state=level.getBlockState(cursor);
            if(state.is(ALLOWED))return true;
            if(state.is(LIMITS))break;
        }
        if(level.getBlockState(pos).is(LIMITS))return false;
        for(int y=pos.getY()+1;y<level.getMaxBuildHeight();y++){
            cursor.setY(y);var state=level.getBlockState(cursor);
            if(state.is(LIMITS))break;
            if(state.is(ALLOWED))return true;
        }
        return false;
    }
    public static boolean allowsPursuit(Player player){
        var wanted=RouteNavigation.pursuitPosition(player);var feet=BlockPos.containing(wanted);var level=player.level();
        if(allows(level,feet))return true;
        // Preserve the supported edge fallback for players sneaking off a platform.
        for(int x=net.minecraft.util.Mth.floor(wanted.x-1);x<=net.minecraft.util.Mth.floor(wanted.x+1);x++)
            for(int z=net.minecraft.util.Mth.floor(wanted.z-1);z<=net.minecraft.util.Mth.floor(wanted.z+1);z++){
                double dx=x+.5-wanted.x,dz=z+.5-wanted.z;if(dx*dx+dz*dz>2.25)continue;
                var support=new BlockPos(x,net.minecraft.util.Mth.floor(wanted.y-.01),z);
                if(!level.hasChunkAt(support))continue;
                var shape=level.getBlockState(support).getCollisionShape(level,support);
                if(!shape.isEmpty()&&Math.abs(support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y)-wanted.y)<=.6
                        &&allows(level,BlockPos.containing(x+.5,support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y),z+.5)))return true;
            }
        return false;
    }
}
