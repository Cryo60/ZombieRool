package me.cryo.zombierool.mixins;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(WalkNodeEvaluator.class)
public abstract class ZombieroolWalkNodeEvaluatorMixin {
    @Inject(method="getBlockPathTypeRaw",at=@At("HEAD"),cancellable=true)
    private static void zr$vehicleObstacle(BlockGetter getter,BlockPos pos,CallbackInfoReturnable<BlockPathTypes> ci){var l=me.cryo.zombierool.gameplay.VehicleDecorPhysics.level(getter);if(l!=null&&!me.cryo.zombierool.gameplay.VehicleDecorPhysics.collisions(l,new net.minecraft.world.phys.AABB(pos).deflate(.01)).isEmpty())ci.setReturnValue(BlockPathTypes.BLOCKED);}
    @Unique private static final TagKey<Block> zr$allowed=TagKey.create(Registries.BLOCK,new ResourceLocation("zombierool","allowed_blocks"));
    @Unique private static final TagKey<Block> zr$limits=TagKey.create(Registries.BLOCK,new ResourceLocation("zombierool","limit_blocks"));
    @Unique private final Map<Long,Column> zr$columns=new HashMap<>();
    @Unique private BlockGetter zr$world;
    @Inject(method="prepare",at=@At("HEAD"))
    private void zr$prepare(PathNavigationRegion region,Mob mob,CallbackInfo ci){zr$columns.clear();zr$world=region;}
    @Inject(method="done",at=@At("HEAD"))
    private void zr$done(CallbackInfo ci){zr$columns.clear();zr$world=null;}
    @Inject(method="getBlockPathType",at=@At("RETURN"),cancellable=true)
    private void zr$type(BlockGetter world,int x,int y,int z,Mob mob,CallbackInfoReturnable<BlockPathTypes> ci){
        if(!(mob instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity)&&!(mob instanceof me.cryo.zombierool.entity.MapActorEntity)&&!(mob instanceof me.cryo.zombierool.entity.MrChiefEntity))return;
        if(me.cryo.zombierool.block.system.TraversalSystem.endpoint(world,new BlockPos(x,y,z))){ci.setReturnValue(BlockPathTypes.WALKABLE);return;}
        var type=ci.getReturnValue();if(type==BlockPathTypes.BLOCKED)return;
        if(zr$world!=world){zr$columns.clear();zr$world=world;}
        long key=((long)x<<32)^(z&0xffffffffL);
        Column column=zr$columns.computeIfAbsent(key,k->new Column(world.getMinBuildHeight(),world.getMaxBuildHeight()));
        if(column.allowed(world,x,y,z))return;
        ci.setReturnValue(BlockPathTypes.BLOCKED);
    }
    @Unique private static final class Column {
        final int low;
        final byte[] flags;
        final java.util.BitSet known=new java.util.BitSet(),values=new java.util.BitSet();
        Column(int low,int high){this.low=low;flags=new byte[high-low+1];}
        byte flag(BlockGetter world,int x,int i,int z){
            if(flags[i]==0){var state=world.getBlockState(new BlockPos(x,low+i,z));flags[i]=(byte)(1|(state.is(zr$allowed)?2:0)|(state.is(zr$limits)?4:0));}
            return flags[i];
        }
        boolean allowed(BlockGetter world,int x,int y,int z){
            int i=y-low;if(i<0||i>=flags.length)return false;
            if(known.get(i))return values.get(i);
            for(int j=i;j>=0;j--){
                byte f=flag(world,x,j,z);
                // The downward branch excludes the allowed block from limit checks.
                if((f&2)!=0){known.set(j,i+1);values.set(j,i+1);return true;}
                if((f&4)!=0)break;
            }
            if((flag(world,x,i,z)&4)==0)for(int j=i+1;j<flags.length;j++){
                byte f=flag(world,x,j,z);
                if((f&4)!=0)break;
                if((f&2)!=0){known.set(i,j+1);values.set(i,j+1);return true;}
            }
            known.set(i);return false;
        }
    }
}
