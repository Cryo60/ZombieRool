package net.mcreator.zombierool;

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
        data.setActivated(activated);
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
