package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.pathfinder.*;
/** Ground paths also follow the bed underwater; climbing requires physical steps. */
public final class RouteNodeEvaluator extends WalkNodeEvaluator {
    private boolean swimming;
    @Override public void prepare(net.minecraft.world.level.PathNavigationRegion region,net.minecraft.world.entity.Mob mob){
        super.prepare(region,mob);
        swimming=EnemyWaterMovement.swims(mob);
        if(mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity special&&special.isBoss())entityHeight=2;
    }
    @Override public Node getStart(){
        if(!mob.isInWater())return super.getStart();
        BlockPos pos=mob.blockPosition();
        if(swimming)return getStartNode(pos);
        while(pos.getY()>level.getMinBuildHeight()&&level.getFluidState(pos.below()).is(FluidTags.WATER)
                &&level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())pos=pos.below();
        return getStartNode(pos);
    }
    @Override protected Node findAcceptedNode(int x,int y,int z,int steps,double floor,net.minecraft.core.Direction direction,BlockPathTypes previous){
        Node node=super.findAcceptedNode(x,y,z,steps,floor,direction,previous);
        if(swimming||node==null||(node.type!=BlockPathTypes.WATER&&node.type!=BlockPathTypes.WATER_BORDER))return node;
        BlockPos pos=node.asBlockPos();
        while(pos.getY()>level.getMinBuildHeight()&&level.getFluidState(pos.below()).is(FluidTags.WATER)
                &&level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty()){
            pos=pos.below();
            BlockPathTypes type=getBlockPathType(mob,pos);
            if(mob.getPathfindingMalus(type)<0)return null;
            node=getNode(pos.getX(),pos.getY(),pos.getZ());node.type=type;node.costMalus=mob.getPathfindingMalus(type);
        }
        return node;
    }

    @Override public BlockPathTypes getBlockPathType(net.minecraft.world.level.BlockGetter world,int x,int y,int z,net.minecraft.world.entity.Mob entity){
        BlockPathTypes type=super.getBlockPathType(world,x,y,z,entity);
        BlockPos pos=new BlockPos(x,y,z);
        if(type==BlockPathTypes.OPEN&&breakable(world.getBlockState(pos).getBlock()))return BlockPathTypes.WALKABLE_DOOR;
        if(type==BlockPathTypes.DOOR_IRON_CLOSED&&PressurePlateDoors.hasPlate(world,pos)){
            // The normal evaluator has already checked navigation markers and forbidden columns.
            return BlockPathTypes.WALKABLE_DOOR;
        }
        return type;
    }

    private static boolean breakable(net.minecraft.world.level.block.Block block){return block instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor||block instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock||block instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock;}
    private boolean clearSwimBox(net.minecraft.world.phys.AABB box){
        var volume=net.minecraft.world.phys.shapes.Shapes.create(box);
        for(BlockPos pos:BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))){var state=level.getBlockState(pos);if(breakable(state.getBlock()))continue;var shape=state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(mob));if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(volume,shape.move(pos.getX(),pos.getY(),pos.getZ()),net.minecraft.world.phys.shapes.BooleanOp.AND))return false;}
        return true;
    }
    @Override public int getNeighbors(Node[] neighbors,Node current){
        boolean submerged=swimming&&level.getFluidState(current.asBlockPos()).is(FluidTags.WATER);
        // The ground evaluator projects water neighbors onto the bed before returning
        // them. Reusing those edges makes each pursuit refresh pull a swimmer down.
        int count=submerged?0:super.getNeighbors(neighbors,current);
        int kept=0;
        for(int i=0;i<count;i++)if(PressurePlateDoors.canApproach(level,current.asBlockPos(),neighbors[i].asBlockPos()))neighbors[kept++]=neighbors[i];
        count=kept;
        if(!swimming)return count;
        // Water paths use all three axes, but dry nodes still require ordinary ground
        // support. Never allow the swimming graph to fly across a dry gap.
        for(var direction:net.minecraft.core.Direction.values()){
            BlockPos pos=current.asBlockPos().relative(direction);
            boolean wet=level.getFluidState(pos).is(FluidTags.WATER);
            boolean currentWet=level.getFluidState(current.asBlockPos()).is(FluidTags.WATER);
            if(!wet&&!currentWet)continue;
            BlockPathTypes type=getBlockPathType(level,pos.getX(),pos.getY(),pos.getZ(),mob);
            boolean surface=!wet&&currentWet&&direction==net.minecraft.core.Direction.UP&&level.getFluidState(pos.below()).is(FluidTags.WATER);
            if(type==BlockPathTypes.BLOCKED||type==BlockPathTypes.OPEN&&!surface||type==BlockPathTypes.DOOR_IRON_CLOSED||mob.getPathfindingMalus(type)<0)continue;
            if(!wet&&!surface&&type!=BlockPathTypes.WALKABLE&&type!=BlockPathTypes.WALKABLE_DOOR&&type!=BlockPathTypes.DOOR_OPEN&&type!=BlockPathTypes.WATER_BORDER&&type!=BlockPathTypes.WATER)continue;
            if(!wet&&!surface&&level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())continue;
            var box=mob.getBoundingBox().move(pos.getX()+.5-mob.getX(),pos.getY()-mob.getY(),pos.getZ()+.5-mob.getZ()).deflate(.001);
            if(!clearSwimBox(box))continue;
            if(!PressurePlateDoors.canApproach(level,current.asBlockPos(),pos))continue;
            Node next=getNode(pos.getX(),pos.getY(),pos.getZ());
            if(next.closed||!me.cryo.zombierool.block.system.OneWayGate.allowed(mob,current,next))continue;
            boolean duplicate=false;for(int i=0;i<count;i++)if(neighbors[i]==next){duplicate=true;break;}
            if(duplicate||count>=neighbors.length)continue;
            next.type=type;next.costMalus=Math.max(next.costMalus,mob.getPathfindingMalus(type));neighbors[count++]=next;
        }
        if(submerged&&count<neighbors.length){
            var route=me.cryo.zombierool.block.system.TraversalSystem.edge(mob,current);
            if(route!=null){var pos=route.destination();Node next=getNode(pos.getX(),pos.getY(),pos.getZ());if(!next.closed&&me.cryo.zombierool.block.system.OneWayGate.allowed(mob,current,next)){next.type=BlockPathTypes.WALKABLE;next.costMalus=0;neighbors[count++]=next;}}
        }
        return count;
    }
}
