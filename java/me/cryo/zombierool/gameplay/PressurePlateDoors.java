package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Plan through an iron door only when the approach has a mob-operated plate. */
public final class PressurePlateDoors {
    private PressurePlateDoors() {}
    public static BlockPos lower(BlockGetter level,BlockPos pos){
        var state=level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
    }
    public static boolean closedIron(BlockGetter level,BlockPos pos){
        var state=level.getBlockState(pos);
        // Mod barricades define their own traversability and are attacked on approach.
        if(state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor)return false;
        return state.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand() && !state.getValue(DoorBlock.OPEN);
    }
    public static boolean plate(BlockGetter level,BlockPos pos){
        // Weighted and stone plates also react to mobs. Wooden buttons do not.
        return level.getBlockState(pos).getBlock() instanceof BasePressurePlateBlock;
    }
    public static boolean hasPlate(BlockGetter level,BlockPos pos){
        if(!(level.getBlockState(pos).getBlock() instanceof DoorBlock))return false;
        BlockPos feet=lower(level,pos);Direction normal=level.getBlockState(feet).getValue(DoorBlock.FACING);
        return plate(level,feet.relative(normal))||plate(level,feet.relative(normal.getOpposite()));
    }
    public static boolean canApproach(BlockGetter level,BlockPos from,BlockPos to){
        if(!closedIron(level,to))return true;
        BlockPos feet=lower(level,to);
        if(from.getX()==feet.getX()&&from.getZ()==feet.getZ())return true;
        Direction normal=level.getBlockState(feet).getValue(DoorBlock.FACING);
        for(Direction side:new Direction[]{normal,normal.getOpposite()}){
            BlockPos plate=feet.relative(side);
            if(plate(level,plate)&&from.getX()==plate.getX()&&from.getZ()==plate.getZ()&&Math.abs(from.getY()-feet.getY())<=1)return true;
        }
        return false;
    }
}
