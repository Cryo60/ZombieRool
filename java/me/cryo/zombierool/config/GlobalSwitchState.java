package me.cryo.zombierool.config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

public class GlobalSwitchState {

    // Enregistre un bloc Activator dans le monde
    public static void registerActivator(Level world, BlockPos pos) {
        GlobalSwitchSavedData data = GlobalSwitchSavedData.get(world);
        data.registerActivator(pos);
    }

    // Désenregistre un bloc Activator dans le monde
    public static void unregisterActivator(Level world, BlockPos pos) {
        GlobalSwitchSavedData data = GlobalSwitchSavedData.get(world);
        data.unregisterActivator(pos);
    }

    // Change l'état global
    public static void setActivated(Level world, boolean activated) {
        GlobalSwitchSavedData data = GlobalSwitchSavedData.get(world);
        boolean changed=data.isActivated()!=activated;
        data.setActivated(activated);
        if(changed)notifyActivators(world);
    }
    public static void notifyActivators(Level world){
        for(BlockPos pos:getActivatorPositions(world))if(world.hasChunkAt(pos)&&world.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.ActivatorBlock){
            var state=world.getBlockState(pos);boolean powered=isActivated(world);
            if(state.getValue(me.cryo.zombierool.block.ActivatorBlock.POWERED)!=powered)world.setBlock(pos,state.setValue(me.cryo.zombierool.block.ActivatorBlock.POWERED,powered),3);
            var block=world.getBlockState(pos).getBlock();world.updateNeighborsAt(pos,block);
            for(var direction:net.minecraft.core.Direction.values())world.updateNeighborsAt(pos.relative(direction),block);
        }
    }

    // Récupère l'état global
    public static boolean isActivated(Level world) {
	    if (world instanceof ServerLevel serverWorld) {
	         GlobalSwitchSavedData data = GlobalSwitchSavedData.get(world);
	         return data.isActivated();
	    }
	    // Sur le client, on retourne une valeur par défaut (ici false)
	    return false;
	}


    // Récupère l'ensemble des positions d'activateurs
    public static Set<BlockPos> getActivatorPositions(Level world) {
        GlobalSwitchSavedData data = GlobalSwitchSavedData.get(world);
        return data.getActivatorPositions();
    }
}
