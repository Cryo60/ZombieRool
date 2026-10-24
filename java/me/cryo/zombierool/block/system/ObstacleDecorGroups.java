package me.cryo.zombierool.block.system;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** One saved visual for a connected buyable barrier; each barrier cell retains its purchase logic. */
public final class ObstacleDecorGroups {
    public static boolean apply(ServerPlayer player,BlockPos start,BlockState source){
        boolean furniture=source.getBlock() instanceof FurnitureSystem.FurnitureBlock;
        if(!furniture&&(!(source.getBlock() instanceof MapDeviceSystem.DeviceBlock block)||block.kind!=MapDeviceSystem.Kind.VEHICLE))return false;
        var level=player.serverLevel();var visual=new CompoundTag();AABB local;
        if(furniture){visual.putString("type","furniture");MimicSystem.saveMimic(visual,source);local=((FurnitureSystem.FurnitureBlock)source.getBlock()).wholeShape(source).bounds();if(source.getBlock() instanceof FurnitureSystem.ExtendedFurnitureBlock extended){var direction=extended.extensionDirection(source);local=local.minmax(source.setValue(FurnitureSystem.ExtendedFurnitureBlock.EXTENSION,true).getShape(level,start).bounds().move(direction.getStepX(),direction.getStepY(),direction.getStepZ()));}}
        else{visual.putString("type","vehicle");var config=new MapDeviceSystem.Device(start,source).scenery;visual.putString("mesh",me.cryo.zombierool.gameplay.AmbientScenery.vehicleMesh(config));local=me.cryo.zombierool.gameplay.VehicleDecorPhysics.localBounds(config);float yaw=180-source.getValue(MapDeviceSystem.DeviceBlock.FACING).toYRot();visual.putFloat("yaw",yaw);double cos=Math.cos(Math.toRadians(yaw)),sin=Math.sin(Math.toRadians(yaw));AABB rotated=null;for(double x:new double[]{local.minX,local.maxX})for(double z:new double[]{local.minZ,local.maxZ}){double rx=x*cos+z*sin,rz=-x*sin+z*cos;var point=new AABB(rx,local.minY,rz,rx,local.maxY,rz);rotated=rotated==null?point:rotated.minmax(point);}local=rotated;}
        if(furniture)local=new AABB(Math.floor(local.minX+1e-6),Math.floor(local.minY+1e-6),Math.floor(local.minZ+1e-6),Math.ceil(local.maxX-1e-6),Math.ceil(local.maxY-1e-6),Math.ceil(local.maxZ-1e-6));
        int width=(int)Math.ceil(local.getXsize()-1e-6),height=(int)Math.ceil(local.getYsize()-1e-6),depth=(int)Math.ceil(local.getZsize()-1e-6);
        if((long)width*height*depth>4096){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Decoration footprint is too large."),true);return true;}
        var cells=new LinkedHashSet<BlockPos>();for(var pos:BlockPos.betweenClosed(start,start.offset(width-1,height-1,depth-1))){if(!(level.getBlockEntity(pos) instanceof ObstacleDoorSystem.ObstacleDoorBlockEntity)){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Requires an obstacle area of "+width+" x "+height+" x "+depth+" blocks, starting at the clicked block."),true);return true;}cells.add(pos.immutable());}
        var anchor=start.immutable();AABB group=new AABB(start,start.offset(width,height,depth));visual.putLong("anchor",anchor.asLong());visual.putInt("members",cells.size());visual.putLongArray("cells",cells.stream().mapToLong(BlockPos::asLong).toArray());
        visual.putDouble("scale",1);visual.putDouble("x",start.getX()-local.minX);visual.putDouble("y",start.getY()-local.minY);visual.putDouble("z",start.getZ()-local.minZ);
        visual.putDouble("minX",group.minX);visual.putDouble("minY",group.minY);visual.putDouble("minZ",group.minZ);visual.putDouble("maxX",group.maxX);visual.putDouble("maxY",group.maxY);visual.putDouble("maxZ",group.maxZ);
        var previous=new HashSet<Long>();for(var pos:cells){var old=(ObstacleDoorSystem.ObstacleDoorBlockEntity)level.getBlockEntity(pos);if(!old.decorGroup.isEmpty()&&previous.add(old.decorGroup.getLong("anchor")))clearOldGroup(level,pos,old.decorGroup.copy());}
        for(var pos:cells){var obstacle=(ObstacleDoorSystem.ObstacleDoorBlockEntity)level.getBlockEntity(pos);obstacle.setMimic(cells.size()==1&&furniture?source:null);obstacle.decorGroup=visual.copy();obstacle.setChanged();var state=level.getBlockState(pos);level.setBlock(pos,state.setValue(ObstacleDoorSystem.ObstacleDoorBlock.HAS_COPIED_BLOCK,true),3);level.sendBlockUpdated(pos,state,level.getBlockState(pos),3);}
        return true;
    }
    private static void clearOldGroup(net.minecraft.server.level.ServerLevel level,BlockPos start,CompoundTag tag){
        long anchor=tag.getLong("anchor");var pending=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();boolean legacy=!tag.contains("cells");if(legacy)pending.add(start);else for(long packed:tag.getLongArray("cells"))pending.add(BlockPos.of(packed));
        while(!pending.isEmpty()&&visited.size()<4096){var pos=pending.removeFirst();if(!visited.add(pos)||!(level.getBlockEntity(pos) instanceof ObstacleDoorSystem.ObstacleDoorBlockEntity member)||member.decorGroup.isEmpty()||member.decorGroup.getLong("anchor")!=anchor)continue;member.setMimic(null);if(legacy)for(var direction:Direction.values())pending.add(pos.relative(direction));}
    }
    private ObstacleDecorGroups(){}
}
