package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.config.MapConfigExtensions;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** Swimming is a map choice; existing maps keep their ground-based zombies. */
public final class EnemyWaterMovement {
    public static final String ID="zombierool:water_enemies";
    private EnemyWaterMovement() {}
    public static void register(){
        CompoundTag defaults=new CompoundTag();defaults.putBoolean("swimmingZombies",false);
        MapConfigExtensions.register(new ResourceLocation(ID),"enemies",defaults);
    }
    public static boolean enabled(Level level){
        return level instanceof ServerLevel server && WorldConfig.get(server).getAddonConfig().getCompound(ID).getBoolean("swimmingZombies");
    }
    public static boolean swims(Mob mob){return mob instanceof ZombieEntity && enabled(mob.level());}
    public static void configure(ServerLevel level,boolean swimming){
        var config=WorldConfig.get(level);var addons=config.getAddonConfig();var values=addons.getCompound(ID);
        values.putBoolean("swimmingZombies",swimming);addons.put(ID,values);
        var patch=new CompoundTag();patch.put("addonConfig",addons);config.loadEditable(patch);config.setDirty();
        for(var entity:level.getAllEntities())if(entity instanceof ZombieEntity zombie){zombie.getNavigation().stop();zombie.setYya(0);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(zombie);}
    }
}
